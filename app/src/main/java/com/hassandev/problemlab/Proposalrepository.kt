package com.hassandev.problemlab

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.call.body
import kotlinx.serialization.Serializable

class GenerateProposalException(message: String) : Exception(message)

@Serializable
data class GenerateProposalRequest(val observations: List<String>)

@Serializable
data class GenerateProposalResponse(
    val title: String? = null,
    val description: String? = null,
    val error: String? = null
)

suspend fun generateProposal(observationTexts: List<String>): GenerateProposalResponse {
    val response = supabase.functions.invoke(
        function = "generate-proposal",
        body = GenerateProposalRequest(observations = observationTexts)
    )
    val result = response.body<GenerateProposalResponse>()

    if (result.error != null) {
        throw GenerateProposalException(result.error)
    }
    if (result.title == null || result.description == null) {
        throw GenerateProposalException("Réponse incomplète de l'IA.")
    }
    return result
}

suspend fun saveProposal(
    title: String,
    description: String,
    observationIds: List<String>
): Proposal {
    val userId = supabase.auth.currentUserOrNull()?.id
        ?: throw GenerateProposalException("Utilisateur non connecté")

    val newProposal = Proposal(
        title = title,
        description = description,
        userId = userId,
        modelUsed = "gemini-2.5-flash"
    )

    val insertedProposal = supabase.postgrest["proposals"]
        .insert(newProposal) { select() }
        .decodeSingle<Proposal>()

    val proposalId = insertedProposal.id
        ?: throw GenerateProposalException("Échec de la création de la proposition")

    observationIds.forEach { observationId ->
        supabase.postgrest["proposal_observations"].insert(
            ProposalObservationLink(
                proposalId = proposalId,
                observationId = observationId
            )
        )
    }

    return insertedProposal
}

// Propositions de l'utilisateur, les plus récentes d'abord
suspend fun fetchProposals(): List<Proposal> {
    val userId = supabase.auth.currentUserOrNull()?.id ?: return emptyList()
    return try {
        supabase.postgrest["proposals"]
            .select {
                filter { eq("user_id", userId) }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<Proposal>()
    } catch (e: Exception) {
        android.util.Log.e("PROBLEMLAB_DEBUG", "Erreur chargement propositions", e)
        emptyList()
    }
}

// Liens entre des propositions et leurs observations
suspend fun fetchProposalLinks(proposalIds: List<String>): List<ProposalObservationLink> {
    if (proposalIds.isEmpty()) return emptyList()
    return try {
        supabase.postgrest["proposal_observations"]
            .select { filter { isIn("proposal_id", proposalIds) } }
            .decodeList<ProposalObservationLink>()
    } catch (e: Exception) {
        android.util.Log.e("PROBLEMLAB_DEBUG", "Erreur chargement des liens", e)
        emptyList()
    }
}

// Propositions liées à une observation (utilisé dans l'écran de détail d'une observation)
suspend fun fetchProposalsForObservation(observationId: String): List<Proposal> {
    return try {
        val proposalIds = supabase.postgrest["proposal_observations"]
            .select { filter { eq("observation_id", observationId) } }
            .decodeList<ProposalObservationLink>()
            .map { it.proposalId }
            .distinct()
        if (proposalIds.isEmpty()) {
            emptyList()
        } else {
            supabase.postgrest["proposals"]
                .select {
                    filter { isIn("id", proposalIds) }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<Proposal>()
        }
    } catch (e: Exception) {
        android.util.Log.e("PROBLEMLAB_DEBUG", "Erreur chargement des propositions de l'observation", e)
        emptyList()
    }
}

// Supprime une proposition et ses liens avec les observations
suspend fun deleteProposal(proposalId: String) {
    supabase.postgrest["proposal_observations"].delete {
        filter { eq("proposal_id", proposalId) }
    }
    val deleted = supabase.postgrest["proposals"]
        .delete {
            select()
            filter { eq("id", proposalId) }
        }
        .decodeList<Proposal>()
    if (deleted.isEmpty()) {
        throw GenerateProposalException("règle de sécurité manquante sur la table proposals")
    }
}
@Serializable
data class ResearchRequest(val mode: String, val text: String)

@Serializable
data class ResearchResponse(val description: String? = null, val error: String? = null)

// Marqueur qui sépare la proposition de sa vérification web
const val WEB_MARKER = "VÉRIFICATION SUR LE WEB"

// Sépare la proposition (première partie) de sa vérification web (seconde partie, si elle existe)
fun splitWebSection(text: String): Pair<String, String?> {
    val index = text.indexOf(WEB_MARKER)
    return if (index >= 0) {
        text.substring(0, index).trimEnd() to text.substring(index).trim()
    } else {
        text.trim() to null
    }
}

// Lance la vérification d'une proposition avec la recherche web
suspend fun researchProposal(title: String, description: String): String {
    val response = supabase.functions.invoke(
        function = "generate-proposal",
        body = ResearchRequest(mode = "research", text = "$title\n\n$description")
    )
    val result = response.body<ResearchResponse>()
    if (result.error != null) {
        throw GenerateProposalException(result.error)
    }
    return result.description ?: throw GenerateProposalException("Réponse incomplète de l'IA.")
}

// Enregistre la nouvelle description d'une proposition
suspend fun saveProposalDescription(proposalId: String, description: String) {
    val updated = supabase.postgrest["proposals"]
        .update({ set("description", description) }) {
            select()
            filter { eq("id", proposalId) }
        }
        .decodeList<Proposal>()
    if (updated.isEmpty()) {
        throw GenerateProposalException("règle de sécurité manquante sur la table proposals")
    }
}