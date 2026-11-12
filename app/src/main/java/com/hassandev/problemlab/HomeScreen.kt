package com.hassandev.problemlab

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.delay
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.format.DateTimeParseException

// Identifiant du compte pour lequel la salutation a déjà été affichée (remis à zéro à la déconnexion)
var salutationUserId by mutableStateOf<String?>(null)

private fun parseCreatedAt(value: String?): OffsetDateTime? {
    if (value == null) return null
    return try {
        OffsetDateTime.parse(value)
    } catch (e: DateTimeParseException) {
        null
    }
}

@Composable
fun HomeScreen(
    prenom: String,
    onOpenObservation: (Observation) -> Unit = {},
    onSeeAll: () -> Unit = {}
) {
    var observations by remember { mutableStateOf<List<Observation>>(emptyList()) }
    var sectors by remember { mutableStateOf<List<Sector>>(emptyList()) }
    var photoUrls by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    val userId = supabase.auth.currentUserOrNull()?.id
    var showGreeting by remember { mutableStateOf(salutationUserId != userId) }

    LaunchedEffect(Unit) {
        observations = fetchObservations()
        sectors = fetchSectors()
        isLoading = false
        photoUrls = fetchFirstPhotoUrls(observations.take(4).mapNotNull { it.id })
    }

    // Fait disparaître la salutation après quelques secondes
    LaunchedEffect(Unit) {
        if (showGreeting) {
            delay(3500)
            showGreeting = false
            salutationUserId = userId
        }
    }

    val currentMonth = remember { YearMonth.now() }

    val totalCount = observations.size
    val thisMonthCount = observations.count { obs ->
        val date = parseCreatedAt(obs.createdAt)
        date != null && YearMonth.from(date) == currentMonth
    }
    val sectorNameById = sectors.associateBy({ it.id }, { it.name })
    val topSectors = observations
        .mapNotNull { it.sectorId }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .take(3)
        .mapNotNull { (id, count) -> sectorNameById[id]?.let { it to count } }
    val recentObservations = observations.take(4)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        AnimatedVisibility(
            visible = showGreeting,
            exit = fadeOut(tween(500)) + shrinkVertically(tween(500))
        ) {
            Text(
                text = "Bonjour $prenom",
                style = MaterialTheme.typography.headlineMedium,
                color = Ink,
                modifier = Modifier.padding(bottom = 20.dp)
            )
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Terracotta)
            }
        } else if (totalCount == 0) {
            Text(
                text = "Aucune observation pour l'instant. Appuie sur + pour commencer.",
                style = MaterialTheme.typography.bodyLarge,
                color = MossDark
            )
        } else {
            // Chiffres clés, sans cartes colorées
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Stat(modifier = Modifier.weight(1f), value = totalCount.toString(), label = "Observations")
                VerticalDivider(modifier = Modifier.height(44.dp), color = Moss.copy(alpha = 0.35f))
                Stat(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 20.dp),
                    value = thisMonthCount.toString(),
                    label = "Ce mois-ci"
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = Moss.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(24.dp))

            SectionHeader(title = "Dernières observations", actionLabel = "Tout voir", onAction = onSeeAll)
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                recentObservations.forEach { observation ->
                    ObservationCard(
                        observation = observation,
                        sectorName = observation.sectorId?.let { sectorNameById[it] },
                        photoUrl = observation.id?.let { photoUrls[it] },
                        onClick = { onOpenObservation(observation) }
                    )
                }
            }

            if (topSectors.isNotEmpty()) {
                Spacer(modifier = Modifier.height(32.dp))
                SectionHeader(title = "Secteurs les plus observés")
                Spacer(modifier = Modifier.height(4.dp))
                topSectors.forEach { (name, count) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = name, style = MaterialTheme.typography.bodyMedium, color = Ink)
                        Text(
                            text = count.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MossDark
                        )
                    }
                    HorizontalDivider(color = Moss.copy(alpha = 0.25f))
                }
            }

            // Espace pour que le bouton + ne cache pas le dernier élément
            Spacer(modifier = Modifier.height(96.dp))
        }
    }
}

@Composable
private fun Stat(modifier: Modifier = Modifier, value: String, label: String) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp),
            fontWeight = FontWeight.SemiBold,
            color = Ink
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
            color = MossDark
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, letterSpacing = 1.sp),
            fontWeight = FontWeight.SemiBold,
            color = MossDark
        )
        if (actionLabel != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                fontWeight = FontWeight.SemiBold,
                color = Terracotta,
                modifier = Modifier
                    .clickable(onClick = onAction)
                    .padding(vertical = 4.dp)
            )
        }
    }
}