package com.hassandev.problemlab

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val ObservationCardBackground = Color(0xFFF8F8F3)

private fun parseObservationDateTime(value: String?): LocalDateTime? {
    if (value == null) return null
    return try {
        OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
    } catch (e: Exception) {
        null
    }
}

// "Aujourd'hui", "Hier" ou "4 nov. 2026"
fun formatObservationDate(value: String?): String {
    val dateTime = parseObservationDateTime(value) ?: return (value?.take(10) ?: "")
    val date = dateTime.toLocalDate()
    val today = LocalDate.now()
    return when (date) {
        today -> "Aujourd'hui"
        today.minusDays(1) -> "Hier"
        else -> date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRENCH))
    }
}

// "4 novembre 2026 à 23:41"
fun formatObservationDateTime(value: String?): String {
    val dateTime = parseObservationDateTime(value) ?: return (value?.take(10) ?: "")
    return dateTime.format(DateTimeFormatter.ofPattern("d MMMM yyyy 'à' HH:mm", Locale.FRENCH))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObservationsListScreen(
    onAddClick: () -> Unit,
    refreshTrigger: Int = 0,
    onOpenObservation: (Observation) -> Unit = {}
) {
    var observations by remember { mutableStateOf<List<Observation>>(emptyList()) }
    var sectors by remember { mutableStateOf<List<Sector>>(emptyList()) }
    var photoUrls by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }

    var selectedStatuses by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedSector by remember { mutableStateOf<Sector?>(null) }
    var sectorMenuExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(refreshTrigger) {
        isLoading = true
        observations = fetchObservations()
        sectors = fetchSectors()
        isLoading = false
        photoUrls = fetchFirstPhotoUrls(observations.mapNotNull { it.id })
    }

    val filteredObservations = observations.filter { obs ->
        val statusMatch = selectedStatuses.isEmpty() || obs.status in selectedStatuses
        val sectorMatch = selectedSector == null || obs.sectorId == selectedSector?.id
        val searchMatch = searchQuery.isBlank() ||
                obs.title.contains(searchQuery, ignoreCase = true) ||
                obs.description?.contains(searchQuery, ignoreCase = true) == true
        statusMatch && sectorMatch && searchMatch
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick, containerColor = Terracotta) {
                Icon(Icons.Filled.Add, contentDescription = "Ajouter une observation", tint = Paper)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Paper)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Rechercher...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Effacer")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Filtre par statut : chips à cocher, sélection multiple
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ALL_STATUSES) { status ->
                    val isSelected = status in selectedStatuses
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedStatuses = if (isSelected) {
                                selectedStatuses - status
                            } else {
                                selectedStatuses + status
                            }
                        },
                        label = { Text(statusLabel(status)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = statusColor(status).copy(alpha = 0.18f),
                            selectedLabelColor = Ink
                        )
                    )
                }
            }

            // Filtre par secteur : menu déroulant
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                ExposedDropdownMenuBox(
                    expanded = sectorMenuExpanded,
                    onExpandedChange = { sectorMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedSector?.name ?: "Tous les secteurs",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Secteur") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sectorMenuExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = sectorMenuExpanded,
                        onDismissRequest = { sectorMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Tous les secteurs") },
                            onClick = {
                                selectedSector = null
                                sectorMenuExpanded = false
                            }
                        )
                        sectors.forEach { sector ->
                            DropdownMenuItem(
                                text = { Text(sector.name) },
                                onClick = {
                                    selectedSector = sector
                                    sectorMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    isLoading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    filteredObservations.isEmpty() -> {
                        Text(
                            text = if (observations.isEmpty()) {
                                "Aucune observation pour l'instant."
                            } else {
                                "Aucune observation ne correspond à ta recherche ou à ces filtres."
                            },
                            color = MossDark,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp)
                        )
                    }
                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filteredObservations) { observation ->
                                ObservationCard(
                                    observation = observation,
                                    sectorName = observation.sectorId?.let { id ->
                                        sectors.firstOrNull { it.id == id }?.name
                                    },
                                    photoUrl = observation.id?.let { photoUrls[it] },
                                    onClick = { onOpenObservation(observation) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Statut discret : petit point de couleur et libellé neutre
@Composable
fun StatusLabel(status: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(statusColor(status), CircleShape)
        )
        Text(
            text = statusLabel(status),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
            fontWeight = FontWeight.Medium,
            color = Ink.copy(alpha = 0.8f)
        )
    }
}

@Composable
fun ObservationCard(
    observation: Observation,
    sectorName: String? = null,
    photoUrl: String? = null,
    onClick: () -> Unit = {}
) {
    val shape = RoundedCornerShape(12.dp)
    val text = observation.description?.takeIf { it.isNotBlank() } ?: observation.title
    val date = formatObservationDate(observation.createdAt)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(104.dp)
            .clip(shape)
            .background(ObservationCardBackground)
            .border(1.dp, Moss.copy(alpha = 0.25f), shape)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 20.sp),
                fontWeight = FontWeight.SemiBold,
                color = Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (sectorName != null) {
                Text(
                    text = sectorName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
                    color = MossDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatusLabel(observation.status)
                if (date.isNotBlank()) {
                    Text(
                        text = "·  $date",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
                        color = Moss,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (photoUrl != null) {
            AsyncImage(
                model = photoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(92.dp)
                    .fillMaxHeight()
            )
        }
    }
}