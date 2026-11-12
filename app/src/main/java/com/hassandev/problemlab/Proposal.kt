package com.hassandev.problemlab

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Proposal(
    val id: String? = null,
    val title: String,
    val description: String? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("model_used") val modelUsed: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val rating: Int? = null
)

@Serializable
data class ProposalObservationLink(
    @SerialName("proposal_id") val proposalId: String,
    @SerialName("observation_id") val observationId: String
)