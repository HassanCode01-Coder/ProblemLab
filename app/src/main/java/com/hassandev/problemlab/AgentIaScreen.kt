package com.hassandev.problemlab

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.itemsIndexed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job

// Statuts à partir desquels une proposition fait logiquement passer l'observation en « Idée »
private val STATUTS_AVANT_IDEE = listOf("observe", "a_analyser", "valide")

@Composable
private fun SelectableObservationRow(
    observation: Observation,
    sectorName: String?,
    selected: Boolean,
    onToggle: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    val text = observation.description?.takeIf { it.isNotBlank() } ?: observation.title
    val meta = listOfNotNull(
        sectorName,
        formatObservationDate(observation.createdAt).ifBlank { null }
    ).joinToString("  ·  ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ObservationCardBackground)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) Terracotta else Moss.copy(alpha = 0.25f),
                shape = shape
            )
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(checkedColor = Terracotta)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 19.sp),
                fontWeight = FontWeight.SemiBold,
                color = Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color = MossDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun AgentIaScreen(
    preselectedObservationId: String? = null,
    onPreselectionConsumed: () -> Unit = {},
    onOpenObservation: (Observation) -> Unit = {}
) {
    val scope = rememberCoroutineScope()

    var observations by remember { mutableStateOf<List<Observation>>(emptyList()) }
    var sectors by remember { mutableStateOf<List<Sector>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    // 0 = Générer, 1 = Propositions
    var section by remember { mutableIntStateOf(0) }

    var isGenerating by remember { mutableStateOf(false) }
    var generationError by remember { mutableStateOf<String?>(null) }
    var generationJob by remember { mutableStateOf<Job?>(null) }
    var generatedTitle by remember { mutableStateOf<String?>(null) }
    var generatedDescription by remember { mutableStateOf<String?>(null) }

    var isSaving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var showSuccess by remember { mutableStateOf(false) }
    var savedTitle by remember { mutableStateOf("") }
    var eligibleForIdea by remember { mutableStateOf<List<Observation>>(emptyList()) }
    var showStatusPrompt by remember { mutableStateOf(false) }
    var promptBusy by remember { mutableStateOf(false) }

    var proposals by remember { mutableStateOf<List<Proposal>>(emptyList()) }
    var linkCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var proposalsLoading by remember { mutableStateOf(true) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var selectedProposal by remember { mutableStateOf<Proposal?>(null) }

    LaunchedEffect(Unit) {
        observations = fetchObservations()
        sectors = fetchSectors()
        isLoading = false
        if (preselectedObservationId != null) {
            selectedIds = setOf(preselectedObservationId)
            section = 0
            onPreselectionConsumed()
        }
    }

    LaunchedEffect(refreshTrigger) {
        proposalsLoading = true
        proposals = fetchProposals()
        linkCounts = fetchProposalLinks(proposals.mapNotNull { it.id })
            .groupingBy { it.proposalId }
            .eachCount()
        proposalsLoading = false
    }

    // Remet l'écran de génération à zéro et affiche la liste des propositions
    fun finishGeneration() {
        generatedTitle = null
        generatedDescription = null
        selectedIds = emptySet()
        eligibleForIdea = emptyList()
        section = 1
        refreshTrigger++
        scope.launch { observations = fetchObservations() }
    }

    val openedProposal = selectedProposal

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
    ) {
        if (openedProposal != null) {
            ProposalDetailScreen(
                proposal = openedProposal,
                onBack = { selectedProposal = null },
                onDeleted = {
                    selectedProposal = null
                    refreshTrigger++
                },
                onOpenObservation = onOpenObservation
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Agent IA",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Ink
                )

                TabRow(
                    selectedTabIndex = section,
                    containerColor = Paper,
                    contentColor = Terracotta
                ) {
                    Tab(
                        selected = section == 0,
                        onClick = { section = 0 },
                        text = { Text("Générer") },
                        selectedContentColor = Terracotta,
                        unselectedContentColor = MossDark
                    )
                    Tab(
                        selected = section == 1,
                        onClick = { section = 1 },
                        text = { Text("Propositions") },
                        selectedContentColor = Terracotta,
                        unselectedContentColor = MossDark
                    )
                }

                if (section == 0) {
                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Terracotta)
                        }
                    } else if (observations.isEmpty()) {
                        Text(
                            text = "Crée d'abord au moins une observation avant de générer une proposition.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MossDark
                        )
                    } else if (generatedTitle == null) {
                        // Étape 1 : sélection des observations
                        Text(
                            text = "Sélectionne une ou plusieurs observations. L'agent en tirera une proposition de projet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MossDark
                        )

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 8.dp)
                        ) {
                            items(observations) { observation ->
                                val id = observation.id
                                if (id != null) {
                                    SelectableObservationRow(
                                        observation = observation,
                                        sectorName = observation.sectorId?.let { sid ->
                                            sectors.firstOrNull { it.id == sid }?.name
                                        },
                                        selected = id in selectedIds,
                                        onToggle = {
                                            selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                                        }
                                    )
                                }
                            }
                        }

                        generationError?.let {
                            Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        }

                        if (isGenerating) {
                            Text(
                                text = "Génération en cours, cela peut prendre jusqu'à 40 secondes.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MossDark
                            )
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = Terracotta,
                                trackColor = Moss.copy(alpha = 0.2f)
                            )
                            OutlinedButton(
                                onClick = {
                                    generationJob?.cancel()
                                    generationJob = null
                                    isGenerating = false
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Text("Arrêter la génération")
                            }
                        } else {
                            Button(
                                onClick = {
                                    generationError = null
                                    isGenerating = true
                                    generationJob = scope.launch {
                                        try {
                                            val texts = observations
                                                .filter { it.id in selectedIds }
                                                .map { obs ->
                                                    listOfNotNull(obs.title, obs.description).joinToString(" - ")
                                                }
                                            val result = generateProposal(texts)
                                            generatedTitle = result.title
                                            generatedDescription = result.description
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            android.util.Log.e("PROBLEMLAB_DEBUG", "Erreur génération proposition", e)
                                            generationError = "Génération impossible : ${e.message}"
                                        } finally {
                                            isGenerating = false
                                        }
                                    }
                                },
                                enabled = selectedIds.isNotEmpty(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Text("Générer une proposition (${selectedIds.size})")
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    } else {
                        // Étape 2 : résultat généré, modifiable avant enregistrement
                        Text(
                            text = "Relis et ajuste la proposition avant de l'enregistrer.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MossDark
                        )
                        OutlinedTextField(
                            value = generatedTitle ?: "",
                            onValueChange = { generatedTitle = it },
                            label = { Text("Titre du projet") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = generatedDescription ?: "",
                            onValueChange = { generatedDescription = it },
                            label = { Text("Description") },
                            minLines = 6,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )

                        saveError?.let {
                            Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    generatedTitle = null
                                    generatedDescription = null
                                    saveError = null
                                },
                                enabled = !isSaving,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Recommencer")
                            }
                            Button(
                                onClick = {
                                    isSaving = true
                                    saveError = null
                                    scope.launch {
                                        try {
                                            val title = generatedTitle ?: ""
                                            saveProposal(
                                                title = title,
                                                description = generatedDescription ?: "",
                                                observationIds = selectedIds.toList()
                                            )
                                            savedTitle = title
                                            eligibleForIdea = observations.filter {
                                                it.id in selectedIds && it.status in STATUTS_AVANT_IDEE
                                            }
                                            showSuccess = true
                                            delay(1800)
                                            showSuccess = false
                                            if (eligibleForIdea.isNotEmpty()) {
                                                showStatusPrompt = true
                                            } else {
                                                finishGeneration()
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.e("PROBLEMLAB_DEBUG", "Erreur enregistrement proposition", e)
                                            saveError = "Enregistrement impossible : ${e.message}"
                                        } finally {
                                            isSaving = false
                                        }
                                    }
                                },
                                enabled = !isSaving && !generatedTitle.isNullOrBlank(),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isSaving) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("Enregistrer")
                                }
                            }
                        }
                    }
                } else {
                    // Liste des propositions enregistrées
                    if (proposalsLoading) {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Terracotta)
                        }
                    } else if (proposals.isEmpty()) {
                        Text(
                            text = "Aucune proposition pour l'instant. Génère-en une depuis l'onglet Générer.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MossDark
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            itemsIndexed(rankProposals(proposals)) { index, proposal ->
                                ProposalCard(
                                    proposal = proposal,
                                    linkedCount = proposal.id?.let { linkCounts[it] } ?: 0,
                                    rank = index + 1,
                                    onClick = { selectedProposal = proposal }
                                )
                            }
                        }
                    }
                }
            }
        }

        SuccessOverlay(
            visible = showSuccess,
            title = "Proposition enregistrée",
            subtitle = "Retrouve-la dans l'onglet Propositions."
        )
    }

    // Suggestion de passer les observations liées en « Idée »
    if (showStatusPrompt) {
        val count = eligibleForIdea.size
        AlertDialog(
            onDismissRequest = {},
            containerColor = Paper,
            title = { Text("Mettre à jour le suivi ?") },
            text = {
                Text(
                    if (count == 1) {
                        "L'observation liée peut passer au statut « Idée ». Une note de suivi sera ajoutée avec le titre de la proposition."
                    } else {
                        "Les $count observations liées peuvent passer au statut « Idée ». Une note de suivi sera ajoutée avec le titre de la proposition."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !promptBusy,
                    onClick = {
                        promptBusy = true
                        scope.launch {
                            eligibleForIdea.forEach { obs ->
                                val id = obs.id
                                if (id != null) {
                                    try {
                                        changeObservationStatus(id, "idee", "Proposition de solution : $savedTitle")
                                    } catch (e: Exception) {
                                        android.util.Log.e("PROBLEMLAB_DEBUG", "Erreur passage en Idée", e)
                                    }
                                }
                            }
                            promptBusy = false
                            showStatusPrompt = false
                            finishGeneration()
                        }
                    }
                ) {
                    Text(if (promptBusy) "Mise à jour..." else "Passer en Idée", color = Terracotta)
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !promptBusy,
                    onClick = {
                        showStatusPrompt = false
                        finishGeneration()
                    }
                ) {
                    Text("Plus tard", color = MossDark)
                }
            }
        )
    }
}