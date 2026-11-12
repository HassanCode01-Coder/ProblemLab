package com.hassandev.problemlab

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.util.Locale

private fun openInMaps(context: Context, latitude: Double, longitude: Double) {
    val coords = String.format(Locale.US, "%.6f,%.6f", latitude, longitude)
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:$coords?q=$coords"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        try {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/maps/search/?api=1&query=$coords")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e2: Exception) {
            android.util.Log.e("PROBLEMLAB_DEBUG", "Impossible d'ouvrir la carte", e2)
        }
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
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

// Une ligne de l'historique de suivi
@Composable
private fun TimelineEntry(color: Color, title: String, date: String, text: String?) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(8.dp)
                .background(color, CircleShape)
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                fontWeight = FontWeight.SemiBold,
                color = Ink
            )
            if (date.isNotBlank()) {
                Text(
                    text = date,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                    color = Moss
                )
            }
            if (!text.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = text, style = MaterialTheme.typography.bodyMedium, color = Ink)
            }
        }
    }
}

@Composable
fun ObservationDetailScreen(
    observation: Observation,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var photos by remember { mutableStateOf<List<ObservationPhoto>>(emptyList()) }
    var photosLoading by remember { mutableStateOf(true) }
    var sectorName by remember { mutableStateOf<String?>(null) }
    var fullscreenPhoto by remember { mutableStateOf<String?>(null) }

    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var audioPlaying by remember { mutableStateOf(false) }
    var audioLoading by remember { mutableStateOf(false) }
    var audioError by remember { mutableStateOf(false) }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    // Statut, notes et historique de suivi
    var status by remember { mutableStateOf(observation.status) }
    var notes by remember { mutableStateOf<List<ObservationNote>>(emptyList()) }
    var notesLoading by remember { mutableStateOf(true) }
    var showStatusDialog by remember { mutableStateOf(false) }
    var pendingStatus by remember { mutableStateOf(observation.status) }
    var statusNote by remember { mutableStateOf("") }
    var showNoteDialog by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }
    var followUpBusy by remember { mutableStateOf(false) }
    var followUpError by remember { mutableStateOf<String?>(null) }

    BackHandler { onBack() }

    LaunchedEffect(observation.id) {
        val id = observation.id
        photos = if (id != null) fetchObservationPhotos(id) else emptyList()
        photosLoading = false
    }

    LaunchedEffect(observation.id) {
        val id = observation.id
        notes = if (id != null) fetchNotes(id) else emptyList()
        notesLoading = false
    }

    LaunchedEffect(observation.sectorId) {
        val sectorId = observation.sectorId
        sectorName = if (sectorId == null) null else fetchSectors().firstOrNull { it.id == sectorId }?.name
    }

    DisposableEffect(Unit) {
        onDispose { player?.release() }
    }

    fun stopAudio() {
        player?.release()
        player = null
        audioPlaying = false
        audioLoading = false
    }

    fun toggleAudio(url: String) {
        if (audioPlaying || audioLoading) {
            stopAudio()
            return
        }
        audioError = false
        audioLoading = true
        try {
            val mp = MediaPlayer()
            mp.setDataSource(url)
            mp.setOnPreparedListener {
                it.start()
                audioLoading = false
                audioPlaying = true
            }
            mp.setOnCompletionListener { stopAudio() }
            mp.setOnErrorListener { _, _, _ ->
                stopAudio()
                audioError = true
                true
            }
            player = mp
            mp.prepareAsync()
        } catch (e: Exception) {
            android.util.Log.e("PROBLEMLAB_DEBUG", "Erreur lecture audio", e)
            stopAudio()
            audioError = true
        }
    }

    Column(
        modifier = modifier
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
                "Observation",
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

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusLabel(status, modifier = Modifier.weight(1f))
                TextButton(
                    onClick = {
                        pendingStatus = status
                        statusNote = ""
                        showStatusDialog = true
                    }
                ) {
                    Text(
                        "Changer le statut",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = Terracotta
                    )
                }
            }
            val meta = listOfNotNull(sectorName, formatObservationDateTime(observation.createdAt).ifBlank { null })
                .joinToString("  ·  ")
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color = MossDark
                )
            }
        }

        DetailSection(title = "Description") {
            Text(
                text = observation.description?.takeIf { it.isNotBlank() } ?: observation.title,
                style = MaterialTheme.typography.bodyLarge,
                color = Ink
            )
        }

        DetailSection(title = "Suivi") {
            OutlinedButton(
                onClick = {
                    noteText = ""
                    showNoteDialog = true
                }
            ) {
                Text("Ajouter une note")
            }

            followUpError?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            if (notesLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Terracotta
                )
            } else {
                notes.forEach { note ->
                    TimelineEntry(
                        color = note.status?.let { statusColor(it) } ?: Moss,
                        title = note.status?.let { "Statut : ${statusLabel(it)}" } ?: "Note",
                        date = formatObservationDateTime(note.createdAt),
                        text = note.content
                    )
                    HorizontalDivider(color = Moss.copy(alpha = 0.2f))
                }
                TimelineEntry(
                    color = Moss,
                    title = "Observation enregistrée",
                    date = formatObservationDateTime(observation.createdAt),
                    text = null
                )
            }
        }

        if (photosLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = Terracotta
            )
        } else if (photos.isNotEmpty()) {
            DetailSection(title = "Photos (${photos.size})") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(photos) { photo ->
                        AsyncImage(
                            model = photo.photoUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(width = 220.dp, height = 170.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { fullscreenPhoto = photo.photoUrl }
                        )
                    }
                }
            }
        }

        observation.audioUrl?.let { url ->
            DetailSection(title = "Note vocale") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(Terracotta, CircleShape)
                            .clickable { toggleAudio(url) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (audioLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                                color = Paper
                            )
                        } else {
                            Icon(
                                if (audioPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                contentDescription = if (audioPlaying) "Arrêter" else "Écouter",
                                tint = Paper
                            )
                        }
                    }
                    Text(
                        text = when {
                            audioError -> "Impossible de lire la note vocale."
                            audioLoading -> "Chargement..."
                            audioPlaying -> "Lecture en cours"
                            else -> "Appuie pour écouter"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (audioError) MaterialTheme.colorScheme.error else MossDark
                    )
                }
            }
        }

        val latitude = observation.latitude
        val longitude = observation.longitude
        if (latitude != null && longitude != null) {
            DetailSection(title = "Position") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Terracotta)
                    Text(
                        text = String.format(Locale.US, "%.5f, %.5f", latitude, longitude),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ink
                    )
                }
                OutlinedButton(onClick = { openInMaps(context, latitude, longitude) }) {
                    Text("Ouvrir dans Maps")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }

    // Changement de statut, avec note facultative
    if (showStatusDialog) {
        AlertDialog(
            onDismissRequest = { if (!followUpBusy) showStatusDialog = false },
            containerColor = Paper,
            title = { Text("Changer le statut") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    ALL_STATUSES.forEach { s ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { pendingStatus = s }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = pendingStatus == s,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = Terracotta)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    statusLabel(s),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Ink
                                )
                                Text(
                                    statusDescription(s),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                    color = MossDark
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = statusNote,
                        onValueChange = { statusNote = it },
                        label = { Text("Note (facultatif)") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !followUpBusy && pendingStatus != status,
                    onClick = {
                        val id = observation.id
                        if (id != null) {
                            followUpBusy = true
                            followUpError = null
                            scope.launch {
                                try {
                                    changeObservationStatus(id, pendingStatus, statusNote)
                                    status = pendingStatus
                                    notes = fetchNotes(id)
                                    showStatusDialog = false
                                } catch (e: Exception) {
                                    android.util.Log.e("PROBLEMLAB_DEBUG", "Erreur changement de statut", e)
                                    showStatusDialog = false
                                    followUpError = "Changement impossible : ${e.message}"
                                } finally {
                                    followUpBusy = false
                                }
                            }
                        }
                    }
                ) {
                    Text(if (followUpBusy) "Enregistrement..." else "Enregistrer", color = Terracotta)
                }
            },
            dismissButton = {
                TextButton(enabled = !followUpBusy, onClick = { showStatusDialog = false }) {
                    Text("Annuler", color = MossDark)
                }
            }
        )
    }

    // Ajout d'une note
    if (showNoteDialog) {
        AlertDialog(
            onDismissRequest = { if (!followUpBusy) showNoteDialog = false },
            containerColor = Paper,
            title = { Text("Ajouter une note") },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Note") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !followUpBusy && noteText.isNotBlank(),
                    onClick = {
                        val id = observation.id
                        if (id != null) {
                            followUpBusy = true
                            followUpError = null
                            scope.launch {
                                try {
                                    addNote(id, noteText)
                                    notes = fetchNotes(id)
                                    showNoteDialog = false
                                } catch (e: Exception) {
                                    android.util.Log.e("PROBLEMLAB_DEBUG", "Erreur ajout de note", e)
                                    showNoteDialog = false
                                    followUpError = "Ajout impossible : ${e.message}"
                                } finally {
                                    followUpBusy = false
                                }
                            }
                        }
                    }
                ) {
                    Text(if (followUpBusy) "Enregistrement..." else "Enregistrer", color = Terracotta)
                }
            },
            dismissButton = {
                TextButton(enabled = !followUpBusy, onClick = { showNoteDialog = false }) {
                    Text("Annuler", color = MossDark)
                }
            }
        )
    }

    // Confirmation de suppression
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!deleting) showDeleteDialog = false },
            containerColor = Paper,
            title = { Text("Supprimer cette observation ?") },
            text = {
                Text("Les photos, la note vocale et le suivi associés seront aussi supprimés. Cette action est irréversible.")
            },
            confirmButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = {
                        deleting = true
                        deleteError = null
                        scope.launch {
                            try {
                                deleteObservation(observation)
                                showDeleteDialog = false
                                onDeleted()
                            } catch (e: Exception) {
                                android.util.Log.e("PROBLEMLAB_DEBUG", "Erreur suppression observation", e)
                                showDeleteDialog = false
                                deleteError = "Suppression impossible : ${e.message}"
                            } finally {
                                deleting = false
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

    // Photo en plein écran
    fullscreenPhoto?.let { url ->
        Dialog(
            onDismissRequest = { fullscreenPhoto = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { fullscreenPhoto = null },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                IconButton(
                    onClick = { fullscreenPhoto = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Fermer", tint = Color.White)
                }
            }
        }
    }
}