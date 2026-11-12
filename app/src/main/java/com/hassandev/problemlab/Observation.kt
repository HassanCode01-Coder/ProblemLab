package com.hassandev.problemlab

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Observation(
    val id: String? = null,
    val title: String,
    val description: String? = null,
    val status: String = "observe",
    @SerialName("sector_id") val sectorId: String? = null,
    val address: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("user_id") val userId: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("audio_url") val audioUrl: String? = null
)

@Serializable
data class ObservationPhoto(
    val id: String? = null,
    @SerialName("observation_id") val observationId: String,
    @SerialName("photo_url") val photoUrl: String
)