package com.hassandev.problemlab

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.StarHalf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job

private val StarGold = Color(0xFFB7862E)
private val StarEmpty = Color(0xFFBFBAA8)

// Étoiles sur 5, avec demi-étoiles ; cliquables si onRate est fourni
@Composable
fun StarRating(stars: Float, size: Dp = 16.dp, onRate: ((Int) -> Unit)? = null) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 1..5) {
            val icon = when {
                stars >= i -> Icons.Filled.Star
                stars >= i - 0.5f -> Icons.Filled.StarHalf
                else -> Icons.Filled.StarBorder
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (stars >= i - 0.5f) StarGold else StarEmpty,
                modifier = Modifier
                    .size(size)
                    .then(if (onRate != null) Modifier.clickable { onRate(i) } else Modifier)
            )
        }
    }
}

@Composable
private fun ProposalSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ObservationCardBackground)
            .border(1.dp, Moss.copy(alpha = 0.25f), shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp, letterSpacing = 1.sp),
            fontWeight = FontWeight.SemiBold,
            color = MossDark
        )
        content()
    }
}

// Une ligne est un titre de section si elle est en majuscules, sans chiffres (« PLAN DE DÉPLOIEMENT »)
private fun isHeading(line: String): Boolean {
    val t = line.trim().trimEnd(':').trim()
    return t.length >= 4 &&
            t.any { it.isLetter() } &&
            t.none { it.isLowerCase() } &&
            t.none { it.isDigit() } &&
            !t.startsWith("-")
}

// Première phrase lisible d'une proposition (on saute les titres de section)
private fun previewText(description: String?): String? {
    if (description.isNullOrBlank()) return null
    return description.lines()
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() && it.any { c -> c.isLowerCase() } }
}

// Affiche un texte structuré : les titres de section ressortent, le reste est du texte normal
@Composable
private fun FormattedText(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        text.lines().forEach { raw ->
            val line = raw.trimEnd()
            when {
                line.isBlank() -> Spacer(modifier = Modifier.height(2.dp))
                isHeading(line) -> Text(
                    text = line.trim().trimEnd(':').trim(),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, letterSpacing = 1.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = MossDark,
                    modifier = Modifier.padding(top = 12.dp)
                )
                else -> Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                    color = Ink
                )
            }
        }
    }
}

// Rapport de la vérification web : texte, verdict et sources
private data class WebReport(
    val body: String,
    val verdict: String?,
    val sources: List<Pair<String, String>>
)

private fun parseWebReport(webText: String): WebReport {
    val content = webText.removePrefix(WEB_MARKER).trim()
    val sourcesIndex = content.indexOf("\nSOURCES")
    val body = if (sourcesIndex >= 0) content.substring(0, sourcesIndex).trim() else content

    val sources = if (sourcesIndex >= 0) {
        content.substring(sourcesIndex).lines().drop(2).mapNotNull { line ->
            val t = line.trim().removePrefix("-").trim()
            val sep = t.lastIndexOf(" : http")
            when {
                sep > 0 -> t.substring(0, sep) to t.substring(sep + 3)
                t.startsWith("http") -> "Source" to t
                else -> null
            }
        }
    } else {
        emptyList()
    }

    val lines = body.lines().map { it.trim() }
    val conclusionIndex = lines.indexOfFirst { it.trimEnd(':').equals("CONCLUSION", ignoreCase = true) }
    val firstLine = if (conclusionIndex >= 0) {
        lines.drop(conclusionIndex + 1).firstOrNull { it.isNotEmpty() }
    } else {
        null
    }
    val normalized = firstLine?.trimStart('«', '"', '“', ' ', '-', '*')
    val verdict = when {
        normalized == null -> null
        normalized.startsWith("Construire", ignoreCase = true) -> "Construire"
        normalized.startsWith("Valider", ignoreCase = true) -> "Valider d'abord"
        normalized.startsWith("Existe", ignoreCase = true) -> "Existe déjà"
        else -> null
    }
    return WebReport(body, verdict, sources)
}

@Composable
private fun VerdictBadge(verdict: String) {
    val color = when (verdict) {
        "Construire" -> Color(0xFF3F7D4E)
        "Valider d'abord" -> Color(0xFFB7862E)
        else -> Color(0xFFA8461F)
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Text(
            text = "Verdict : $verdict",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            fontWeight = FontWeight.SemiBold,
            color = Ink
        )
    }
}

@Composable
private fun SourceRow(title: String, url: String) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                try {
                    uriHandler.openUri(url)
                } catch (e: Exception) {
                    android.util.Log.e("PROBLEMLAB_DEBUG", "Impossible d'ouvrir la source", e)
                }
            }
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            fontWeight = FontWeight.SemiBold,
            color = Terracotta,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "Appuie pour ouvrir la source",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
            color = Moss
        )
    }
}

@Composable
private fun RankBadge(rank: Int) {
    val first = rank == 1
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (first) Terracotta else Moss.copy(alpha = 0.18f))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(
            text = if (first) "N°1 · Priorité" else "N°$rank",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
            fontWeight = FontWeight.SemiBold,
            color = if (first) Paper else MossDark
        )
    }
}

@Composable
fun ProposalCard(
    proposal: Proposal,
    linkedCount: Int,
    rank: Int? = null,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    val stars = proposalStars(proposal)
    val total = proposalAiTotal(proposal)
    val meta = listOfNotNull(
        if (linkedCount > 0) "$linkedCount observation${if (linkedCount > 1) "s" else ""}" else null,
        formatObservationDate(proposal.createdAt).ifBlank { null }
    ).joinToString("  ·  ")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ObservationCardBackground)
            .border(
                width = if (rank == 1) 1.5.dp else 1.dp,
                color = if (rank == 1) Terracotta else Moss.copy(alpha = 0.25f),
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (rank != null || stars != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (rank != null) RankBadge(rank) else Spacer(modifier = Modifier)
                if (stars != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StarRating(stars = stars, size = 15.dp)
                        Text(
                            text = proposal.rating?.let { "$it/5" } ?: total?.let { "$it/25" } ?: "",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                            color = MossDark
                        )
                    }
                }
            }
        }
        Text(
            text = proposal.title,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 20.sp),
            fontWeight = FontWeight.SemiBold,
            color = Ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        previewText(proposal.description)?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = MossDark,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (meta.isNotEmpty()) {
            Text(
                text = meta,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                color = Moss
            )
        }
    }
}

private val researchSteps = listOf(
    "Recherche de données réelles...",
    "Recherche des solutions existantes...",
    "Analyse de leurs failles...",
    "Rédaction de la conclusion..."
)

@Composable
fun ProposalDetailScreen(
    proposal: Proposal,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    onOpenObservation: (Observation) -> Unit
) {
    val scope = rememberCoroutineScope()

    var fullDescription by remember(proposal.id) { mutableStateOf(proposal.description ?: "") }
    var rating by remember(proposal.id) { mutableStateOf(proposal.rating) }
    var ratingError by remember { mutableStateOf<String?>(null) }

    var linked by remember { mutableStateOf<List<Observation>>(emptyList()) }
    var sectors by remember { mutableStateOf<List<Sector>>(emptyList()) }
    var photoUrls by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }

    var researching by remember { mutableStateOf(false) }
    var researchJob by remember { mutableStateOf<Job?>(null) }
    var researchStep by remember { mutableIntStateOf(0) }
    var researchError by remember { mutableStateOf<String?>(null) }
    var showErrorDetails by remember { mutableStateOf(false) }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    val (mainText, webText) = splitWebSection(fullDescription)
    val report = webText?.let { parseWebReport(it) }
    val aiTotal = proposalAiTotal(proposal.copy(description = fullDescription))
    val stars = rating?.toFloat() ?: aiTotal?.let { it / 5f }

    BackHandler { onBack() }

    LaunchedEffect(proposal.id) {
        val id = proposal.id
        if (id != null) {
            val observationIds = fetchProposalLinks(listOf(id)).map { it.observationId }.toSet()
            linked = fetchObservations().filter { it.id in observationIds }
            sectors = fetchSectors()
            photoUrls = fetchFirstPhotoUrls(linked.mapNotNull { it.id })
        }
        loading = false
    }

    // Fait avancer les messages pendant la recherche web
    LaunchedEffect(researching) {
        researchStep = 0
        while (researching) {
            delay(9000)
            researchStep = (researchStep + 1).coerceAtMost(researchSteps.lastIndex)
        }
    }

    val startResearch: () -> Unit = {
        val id = proposal.id
        if (id != null && !researching) {
            researching = true
            researchError = null
            showErrorDetails = false
            researchJob = scope.launch {
                try {
                    val section = researchProposal(proposal.title, mainText)
                    val updated = mainText.trimEnd() + "\n\n" + section.trim()
                    saveProposalDescription(id, updated)
                    fullDescription = updated
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.e("PROBLEMLAB_DEBUG", "Erreur vérification web", e)
                    researchError = e.message ?: "Erreur inconnue"
                } finally {
                    researching = false
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = Ink)
            }
            Text(
                "Proposition",
                style = MaterialTheme.typography.titleLarge,
                color = Ink,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showDeleteDialog = true }, enabled = !deleting) {
                Icon(Icons.Filled.Delete, contentDescription = "Supprimer", tint = Ink)
            }
        }

        deleteError?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = proposal.title,
                style = MaterialTheme.typography.headlineSmall,
                color = Ink
            )
            val date = formatObservationDateTime(proposal.createdAt)
            if (date.isNotBlank()) {
                Text(
                    text = date,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color = MossDark
                )
            }
        }

        ProposalSection(title = "Priorité") {
            StarRating(
                stars = stars ?: 0f,
                size = 32.dp,
                onRate = { value ->
                    val id = proposal.id
                    if (id != null) {
                        val newRating = if (rating == value) null else value
                        ratingError = null
                        scope.launch {
                            try {
                                saveProposalRating(id, newRating)
                                rating = newRating
                            } catch (e: Exception) {
                                android.util.Log.e("PROBLEMLAB_DEBUG", "Erreur enregistrement de la note", e)
                                ratingError = "Note non enregistrée : ${e.message}"
                            }
                        }
                    }
                }
            )
            Text(
                text = when {
                    rating != null -> "Ta note : $rating/5"
                    aiTotal != null -> "Note de l'IA : $aiTotal/25"
                    else -> "Pas encore notée"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Ink
            )
            Text(
                text = "Appuie sur une étoile pour donner ta note. Elle remplace celle de l'IA dans le classement. Appuie de nouveau sur la même étoile pour la retirer.",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                color = MossDark
            )
            ratingError?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }

        ProposalSection(title = "Proposition") {
            if (mainText.isBlank()) {
                Text(
                    "Aucune description.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MossDark
                )
            } else {
                FormattedText(mainText)
            }
        }

        ProposalSection(title = "Vérification web") {
            if (researching) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = Terracotta,
                        trackColor = Moss.copy(alpha = 0.2f)
                    )
                    Text(
                        text = researchSteps[researchStep],
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Ink
                    )
                    Text(
                        text = "Cela peut prendre jusqu'à une minute.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                        color = MossDark
                    )
                    OutlinedButton(
                        onClick = {
                            researchJob?.cancel()
                            researchJob = null
                            researching = false
                        }
                    ) {
                        Text("Arrêter la recherche")
                    }
                }
            }

            if (report != null) {
                report.verdict?.let { VerdictBadge(it) }
                FormattedText(report.body)
                if (report.sources.isNotEmpty()) {
                    Text(
                        text = "SOURCES",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, letterSpacing = 1.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = MossDark,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    report.sources.forEach { (title, url) -> SourceRow(title, url) }
                }
            } else if (!researching) {
                Text(
                    text = "L'agent cherche sur le web des données réelles (chiffres, solutions existantes, failles) et te donne un verdict : construire, valider d'abord, ou ne pas construire.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MossDark
                )
            }

            researchError?.let { detail ->
                Text(
                    text = if (detail.contains("429")) {
                        "La recherche web n'est pas disponible avec ta clé Gemini actuelle : elle demande un compte avec facturation activée."
                    } else {
                        "La vérification n'a pas abouti. Réessaie dans un instant."
                    },
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                TextButton(onClick = { showErrorDetails = !showErrorDetails }) {
                    Text(if (showErrorDetails) "Masquer le détail" else "Voir le détail", color = MossDark)
                }
                if (showErrorDetails) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                        color = MossDark
                    )
                }
            }

            if (report == null) {
                Button(
                    onClick = startResearch,
                    enabled = !researching && mainText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Terracotta, contentColor = Paper),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Vérifier sur le web", fontWeight = FontWeight.SemiBold)
                }
            } else {
                OutlinedButton(
                    onClick = startResearch,
                    enabled = !researching,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Relancer la vérification")
                }
            }
        }

        ProposalSection(title = "Observations liées (${linked.size})") {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Terracotta
                )
            } else if (linked.isEmpty()) {
                Text(
                    "Aucune observation liée.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MossDark
                )
            } else {
                linked.forEach { observation ->
                    ObservationCard(
                        observation = observation,
                        sectorName = observation.sectorId?.let { id -> sectors.firstOrNull { it.id == id }?.name },
                        photoUrl = observation.id?.let { photoUrls[it] },
                        onClick = { onOpenObservation(observation) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!deleting) showDeleteDialog = false },
            containerColor = Paper,
            title = { Text("Supprimer cette proposition ?") },
            text = {
                Text("Les observations liées sont conservées. Cette action est irréversible.")
            },
            confirmButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = {
                        val id = proposal.id
                        if (id != null) {
                            deleting = true
                            deleteError = null
                            scope.launch {
                                try {
                                    deleteProposal(id)
                                    showDeleteDialog = false
                                    onDeleted()
                                } catch (e: Exception) {
                                    android.util.Log.e("PROBLEMLAB_DEBUG", "Erreur suppression proposition", e)
                                    showDeleteDialog = false
                                    deleteError = "Suppression impossible : ${e.message}"
                                } finally {
                                    deleting = false
                                }
                            }
                        }
                    }
                ) {
                    Text(
                        if (deleting) "Suppression..." else "Supprimer",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(enabled = !deleting, onClick = { showDeleteDialog = false }) {
                    Text("Annuler", color = MossDark)
                }
            }
        )
    }
}