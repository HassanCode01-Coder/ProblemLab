package com.hassandev.problemlab

import io.github.jan.supabase.postgrest.postgrest

private val totalRegex = Regex("""TOTAL\s*:?\s*(\d{1,2})\s*/\s*25""", RegexOption.IGNORE_CASE)

// Total sur 25 écrit par l'agent dans la note de pertinence
fun proposalAiTotal(proposal: Proposal): Int? {
    val text = splitWebSection(proposal.description ?: "").first
    return totalRegex.find(text)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(0, 25)
}

// Étoiles affichées : ta note si tu en as donné une, sinon la note de l'IA ramenée sur 5
fun proposalStars(proposal: Proposal): Float? =
    proposal.rating?.toFloat() ?: proposalAiTotal(proposal)?.let { it / 5f }

// Classement : étoiles décroissantes, puis total de l'IA, puis les plus récentes
fun rankProposals(proposals: List<Proposal>): List<Proposal> =
    proposals.sortedWith(
        compareByDescending<Proposal> { proposalStars(it) ?: -1f }
            .thenByDescending { proposalAiTotal(it) ?: -1 }
            .thenByDescending { it.createdAt ?: "" }
    )

// Enregistre ta note (1 à 5), ou la retire si rating vaut null
suspend fun saveProposalRating(proposalId: String, rating: Int?) {
    val updated = supabase.postgrest["proposals"]
        .update({ set("rating", rating) }) {
            select()
            filter { eq("id", proposalId) }
        }
        .decodeList<Proposal>()
    if (updated.isEmpty()) {
        throw GenerateProposalException("règle de sécurité manquante sur la table proposals")
    }
}