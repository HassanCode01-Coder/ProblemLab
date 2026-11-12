package com.hassandev.problemlab

import android.content.Context
import android.net.Uri
import kotlinx.serialization.json.Json

data class ObservationDraft(
    val description: String,
    val sector: Sector?,
    val photoUris: List<Uri>,
    val pendingCameraUri: Uri?
)

// Brouillon de l'observation en cours, conservé sur le téléphone même si Android ferme l'app
object DraftStore {
    private const val PREFS = "observation_draft"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isOpen(context: Context): Boolean = prefs(context).getBoolean("open", false)

    fun setOpen(context: Context, open: Boolean) {
        prefs(context).edit().putBoolean("open", open).apply()
    }

    fun save(
        context: Context,
        description: String,
        sector: Sector?,
        photoUris: List<Uri>,
        pendingCameraUri: Uri?
    ) {
        prefs(context).edit()
            .putString("description", description)
            .putString("sector", sector?.let { Json.encodeToString(Sector.serializer(), it) })
            .putString("photos", photoUris.joinToString("\n") { it.toString() })
            .putString("pending_camera", pendingCameraUri?.toString())
            .apply()
    }

    fun load(context: Context): ObservationDraft {
        val p = prefs(context)
        val sector = p.getString("sector", null)?.let {
            try {
                Json.decodeFromString(Sector.serializer(), it)
            } catch (e: Exception) {
                null
            }
        }
        val photos = (p.getString("photos", "") ?: "")
            .split("\n")
            .filter { it.isNotBlank() }
            .map { Uri.parse(it) }
        val pending = p.getString("pending_camera", null)?.let { Uri.parse(it) }
        return ObservationDraft(
            description = p.getString("description", "") ?: "",
            sector = sector,
            photoUris = photos,
            pendingCameraUri = pending
        )
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}