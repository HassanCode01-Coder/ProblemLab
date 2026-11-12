package com.hassandev.problemlab

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Une entrée du suivi : une note, un changement de statut, ou les deux
@Serializable
data class ObservationNote(
    val id: String? = null,
    @SerialName("observation_id") val observationId: String,
    @SerialName("user_id") val userId: String? = null,
    val content: String? = null,
    val status: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

fun statusDescription(status: String): String = when (status) {
    "observe" -> "J'ai vu un problème"
    "a_analyser" -> "Je dois creuser"
    "valide" -> "Le problème est réel et important"
    "idee" -> "J'ai une piste de solution"
    "opportunite" -> "Ça vaut la peine d'en faire un projet"
    "resolu" -> "C'est réglé ou porté par un projet"
    else -> ""
}

suspend fun fetchNotes(observationId: String): List<ObservationNote> {
    return try {
        supabase.postgrest["observation_notes"]
            .select {
                filter { eq("observation_id", observationId) }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<ObservationNote>()
    } catch (e: Exception) {
        android.util.Log.e("PROBLEMLAB_DEBUG", "Erreur chargement du suivi", e)
        emptyList()
    }
}

suspend fun addNote(observationId: String, content: String) {
    val userId = supabase.auth.currentUserOrNull()?.id
        ?: throw SaveObservationException("Utilisateur non connecté")
    supabase.postgrest["observation_notes"].insert(
        ObservationNote(
            observationId = observationId,
            userId = userId,
            content = content.trim()
        )
    )
}

// Change le statut de l'observation, avec une note facultative dans le suivi
suspend fun changeObservationStatus(observationId: String, newStatus: String, note: String?) {
    val userId = supabase.auth.currentUserOrNull()?.id
        ?: throw SaveObservationException("Utilisateur non connecté")

    val updated = supabase.postgrest["observations"]
        .update({ set("status", newStatus) }) {
            select()
            filter { eq("id", observationId) }
        }
        .decodeList<Observation>()
    if (updated.isEmpty()) {
        throw SaveObservationException("règle de sécurité manquante sur la table observations")
    }

    supabase.postgrest["observation_notes"].insert(
        ObservationNote(
            observationId = observationId,
            userId = userId,
            content = note?.trim()?.takeIf { it.isNotBlank() },
            status = newStatus
        )
    )
}