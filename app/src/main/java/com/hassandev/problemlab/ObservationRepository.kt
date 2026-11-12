package com.hassandev.problemlab

import android.content.Context
import android.net.Uri
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import java.io.File

class SaveObservationException(message: String) : Exception(message)

suspend fun fetchObservations(): List<Observation> {
    val userId = supabase.auth.currentUserOrNull()?.id ?: return emptyList()

    return try {
        supabase.postgrest["observations"]
            .select {
                filter { eq("user_id", userId) }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<Observation>()
    } catch (e: Exception) {
        emptyList()
    }
}

suspend fun saveObservation(
    context: Context,
    description: String,
    sector: Sector?,
    gpsPosition: GpsPosition?,
    photoUris: List<Uri>,
    audioFile: File?
): Observation {
    val userId = supabase.auth.currentUserOrNull()?.id
        ?: throw SaveObservationException("Utilisateur non connecté")

    val bucket = supabase.storage.from("observation-media")

    // 1. Upload de la note vocale, si présente
    var audioUrl: String? = null
    if (audioFile != null && audioFile.exists()) {
        val path = "$userId/audio/${audioFile.name}"
        bucket.upload(path, audioFile.readBytes()) {
            upsert = true
        }
        audioUrl = bucket.publicUrl(path)
    }

    // 2. Création de l'observation en base (pour récupérer son id)
    val newObservation = Observation(
        title = description.take(60).ifBlank { "Observation" },
        description = description,
        sectorId = sector?.id,
        userId = userId,
        latitude = gpsPosition?.latitude,
        longitude = gpsPosition?.longitude,
        audioUrl = audioUrl
    )

    val insertedObservation = supabase.postgrest["observations"]
        .insert(newObservation) {
            select()
        }
        .decodeSingle<Observation>()

    val observationId = insertedObservation.id
        ?: throw SaveObservationException("Échec de la création de l'observation")

    // 3. Upload des photos et insertion dans observation_photos
    photoUris.forEachIndexed { index, uri ->
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        if (bytes != null) {
            val path = "$userId/photos/${observationId}_$index.jpg"
            bucket.upload(path, bytes) {
                upsert = true
            }
            val photoUrl = bucket.publicUrl(path)

            supabase.postgrest["observation_photos"].insert(
                ObservationPhoto(
                    observationId = observationId,
                    photoUrl = photoUrl
                )
            )
        }
    }

    // 4. Nettoyage du cache local maintenant que tout est uploadé
    audioFile?.delete()
    photoUris.forEach { uri ->
        if (uri.scheme == "content" && uri.authority == "${context.packageName}.fileprovider") {
            context.contentResolver.delete(uri, null, null)
        }
    }

    return insertedObservation
}
// Toutes les photos d'une observation
suspend fun fetchObservationPhotos(observationId: String): List<ObservationPhoto> {
    return try {
        supabase.postgrest["observation_photos"]
            .select { filter { eq("observation_id", observationId) } }
            .decodeList<ObservationPhoto>()
    } catch (e: Exception) {
        android.util.Log.e("PROBLEMLAB_DEBUG", "Erreur chargement photos", e)
        emptyList()
    }
}

// Première photo de chaque observation (pour les miniatures de la liste)
suspend fun fetchFirstPhotoUrls(observationIds: List<String>): Map<String, String> {
    if (observationIds.isEmpty()) return emptyMap()
    return try {
        supabase.postgrest["observation_photos"]
            .select { filter { isIn("observation_id", observationIds) } }
            .decodeList<ObservationPhoto>()
            .groupBy { it.observationId }
            .mapValues { it.value.first().photoUrl }
    } catch (e: Exception) {
        android.util.Log.e("PROBLEMLAB_DEBUG", "Erreur chargement miniatures", e)
        emptyMap()
    }
}
// Supprime une observation, ses photos (lignes et fichiers) et sa note vocale
suspend fun deleteObservation(observation: Observation) {
    val id = observation.id ?: throw SaveObservationException("Observation introuvable")
    val photos = fetchObservationPhotos(id)
    //  Liens avec les propositions de solutions
    supabase.postgrest["proposal_observations"].delete {
        filter { eq("observation_id", id) }
    }
    //  Lignes des photos
    if (photos.isNotEmpty()) {
        supabase.postgrest["observation_photos"].delete {
            filter { eq("observation_id", id) }
        }
    }

    // L'observation elle-même (select() permet de savoir si une ligne a vraiment été supprimée)
    val deleted = supabase.postgrest["observations"]
        .delete {
            select()
            filter { eq("id", id) }
        }
        .decodeList<Observation>()
    if (deleted.isEmpty()) {
        throw SaveObservationException("règle de sécurité manquante sur la table observations")
    }

    // Fichiers du stockage (une erreur ici n'empêche pas la suppression)
    val paths = (photos.map { it.photoUrl } + listOfNotNull(observation.audioUrl))
        .mapNotNull { url -> url.substringAfter("/observation-media/", "").takeIf { it.isNotBlank() } }
    if (paths.isNotEmpty()) {
        try {
            supabase.storage.from("observation-media").delete(paths)
        } catch (e: Exception) {
            android.util.Log.e("PROBLEMLAB_DEBUG", "Erreur suppression fichiers", e)
        }
    }
}