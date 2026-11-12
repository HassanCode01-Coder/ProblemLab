package com.hassandev.problemlab

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File

private val SpotifyGreen = Color(0xFF1DB954)

// Conserve la liste des photos quand Android recrée l'écran
private val photoUrisSaver = listSaver<List<Uri>, String>(
    save = { list -> list.map { it.toString() } },
    restore = { saved -> saved.map { Uri.parse(it) } }
)

// Conserve le secteur choisi
private val sectorSaver = Saver<Sector?, String>(
    save = { sector ->
        if (sector == null) "" else Json.encodeToString(Sector.serializer(), sector)
    },
    restore = { saved ->
        if (saved.isEmpty()) null else Json.decodeFromString(Sector.serializer(), saved)
    }
)

// Vérifie qu'un fichier photo existe vraiment et n'est pas vide
private fun photoExists(context: Context, uri: Uri): Boolean = try {
    context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length > 0 } ?: false
} catch (e: Exception) {
    false
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewObservationScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Brouillon éventuel laissé par une session précédente (ex : app fermée par Android pendant la photo)
    val draft = remember {
        DraftStore.load(context).let { d ->
            d.copy(photoUris = d.photoUris.filter { photoExists(context, it) })
        }
    }

    var description by rememberSaveable { mutableStateOf(draft.description) }
    var sectors by remember { mutableStateOf<List<Sector>>(emptyList()) }
    var selectedSector by rememberSaveable(stateSaver = sectorSaver) { mutableStateOf<Sector?>(draft.sector) }
    var sectorMenuExpanded by remember { mutableStateOf(false) }

    var gpsPosition by remember { mutableStateOf<GpsPosition?>(null) }
    var gpsStatus by remember { mutableStateOf("Recherche de la position...") }

    var photoUris by rememberSaveable(stateSaver = photoUrisSaver) { mutableStateOf<List<Uri>>(draft.photoUris) }
    var pendingCameraUri by rememberSaveable { mutableStateOf<Uri?>(draft.pendingCameraUri) }

    var audioFile by remember { mutableStateOf<File?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    var isSaving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var saveSuccess by remember { mutableStateOf(false) }

    val overlayScale by animateFloatAsState(
        targetValue = if (saveSuccess) 1f else 0.6f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "successScale"
    )

    // Quitte l'écran en abandonnant le brouillon
    fun quitter() {
        DraftStore.clear(context)
        onBack()
    }

    BackHandler(enabled = !isSaving && !saveSuccess) { quitter() }

    // Indique qu'un brouillon est ouvert (pour rouvrir ce formulaire si l'app est relancée)
    LaunchedEffect(Unit) {
        DraftStore.setOpen(context, true)
    }

    // Récupère une photo prise juste avant que l'app soit fermée par Android
    LaunchedEffect(Unit) {
        val pending = pendingCameraUri
        if (pending != null && photoExists(context, pending)) {
            if (pending !in photoUris && photoUris.size < 5) {
                photoUris = photoUris + pending
            }
            pendingCameraUri = null
        }
    }

    // Sauvegarde automatique du brouillon à chaque modification
    LaunchedEffect(description, selectedSector, photoUris, pendingCameraUri) {
        if (!saveSuccess) {
            DraftStore.save(context, description, selectedSector, photoUris, pendingCameraUri)
        }
    }

    // Après le message de succès, retour à la liste
    LaunchedEffect(saveSuccess) {
        if (saveSuccess) {
            delay(2000)
            onBack()
        }
    }

    // Charger les secteurs une seule fois à l'ouverture de l'écran
    LaunchedEffect(Unit) {
        sectors = fetchSectors()
    }

    // Gestion de la permission de localisation
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scope.launch {
                val position = getCurrentLocation(context)
                if (position != null) {
                    gpsPosition = position
                    gpsStatus = "Position détectée"
                } else {
                    gpsStatus = "Position introuvable"
                }
            }
        } else {
            gpsStatus = "Permission de localisation refusée"
        }
    }

    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            val position = getCurrentLocation(context)
            if (position != null) {
                gpsPosition = position
                gpsStatus = "Position détectée"
            } else {
                gpsStatus = "Position introuvable"
            }
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // Galerie : sélection multiple d'images existantes
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5)
    ) { uris ->
        val availableSlots = 5 - photoUris.size
        photoUris = (photoUris + uris.take(availableSlots)).distinct()
    }

    // Appareil photo : prise d'une nouvelle photo
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null && uri !in photoUris && photoUris.size < 5) {
            photoUris = photoUris + uri
        }
        pendingCameraUri = null
    }

    // Permission caméra, demandée seulement au moment d'appuyer sur "Prendre une photo"
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = createImageUri(context)
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        }
    }

    // Permission micro, demandée seulement au moment d'appuyer sur "Enregistrer"
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val file = createAudioFile(context)
            audioFile = file
            mediaRecorder = createMediaRecorder(context).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            recordingSeconds = 0
        }
    }

    // Chronomètre pendant l'enregistrement
    LaunchedEffect(isRecording) {
        while (isRecording) {
            delay(1000)
            recordingSeconds++
        }
    }

    // Libère proprement le lecteur/enregistreur audio quand l'écran est quitté
    DisposableEffect(Unit) {
        onDispose {
            mediaRecorder?.release()
            mediaPlayer?.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = { quitter() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                }
                Text(
                    text = "Nouvelle observation",
                    style = MaterialTheme.typography.headlineSmall
                )
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            ExposedDropdownMenuBox(
                expanded = sectorMenuExpanded,
                onExpandedChange = { sectorMenuExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedSector?.name ?: "",
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

            Text(text = gpsStatus, style = MaterialTheme.typography.bodyMedium)

            Text(
                text = "Photos (${photoUris.size}/5)",
                style = MaterialTheme.typography.titleSmall
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        if (photoUris.size < 5) {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        }
                    },
                    enabled = photoUris.size < 5,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Galerie")
                }

                OutlinedButton(
                    onClick = {
                        if (photoUris.size < 5) {
                            val hasPermission = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED

                            if (hasPermission) {
                                val uri = createImageUri(context)
                                pendingCameraUri = uri
                                cameraLauncher.launch(uri)
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }
                    },
                    enabled = photoUris.size < 5,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Prendre une photo")
                }
            }

            if (photoUris.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(photoUris) { uri ->
                        Box {
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(90.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            IconButton(
                                onClick = { photoUris = photoUris.filter { it != uri } },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Retirer")
                            }
                        }
                    }
                }
            }

            Text(
                text = "Note vocale",
                style = MaterialTheme.typography.titleSmall
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!isRecording) {
                    OutlinedButton(
                        onClick = {
                            val hasPermission = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED

                            if (hasPermission) {
                                val file = createAudioFile(context)
                                audioFile = file
                                mediaRecorder = createMediaRecorder(context).apply {
                                    setAudioSource(MediaRecorder.AudioSource.MIC)
                                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                                    setOutputFile(file.absolutePath)
                                    prepare()
                                    start()
                                }
                                isRecording = true
                                recordingSeconds = 0
                            } else {
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    ) {
                        Icon(Icons.Filled.Mic, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Enregistrer")
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            mediaRecorder?.apply {
                                stop()
                                release()
                            }
                            mediaRecorder = null
                            isRecording = false
                        }
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Arrêter (${recordingSeconds}s)")
                    }
                }

                if (audioFile != null && !isRecording) {
                    IconButton(
                        onClick = {
                            if (!isPlaying) {
                                mediaPlayer = MediaPlayer().apply {
                                    setDataSource(audioFile!!.absolutePath)
                                    prepare()
                                    start()
                                    setOnCompletionListener { isPlaying = false }
                                }
                                isPlaying = true
                            } else {
                                mediaPlayer?.stop()
                                mediaPlayer?.release()
                                mediaPlayer = null
                                isPlaying = false
                            }
                        }
                    ) {
                        Icon(
                            if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            contentDescription = "Écouter"
                        )
                    }
                }
            }

            Button(
                onClick = {
                    isSaving = true
                    saveError = null
                    scope.launch {
                        try {
                            saveObservation(
                                context = context,
                                description = description,
                                sector = selectedSector,
                                gpsPosition = gpsPosition,
                                photoUris = photoUris,
                                audioFile = audioFile
                            )
                            DraftStore.clear(context)
                            saveSuccess = true
                        } catch (e: Exception) {
                            android.util.Log.e("PROBLEMLAB_DEBUG", "Erreur enregistrement observation", e)
                            saveError = "DEBUG: ${e.message}"
                        } finally {
                            isSaving = false
                        }
                    }
                },
                enabled = !isSaving && description.isNotBlank() && selectedSector != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Enregistrer l'observation")
                }
            }

            saveError?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }
        }

        // Message de succès centré, avec animation
        AnimatedVisibility(
            visible = saveSuccess,
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(250))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {},
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Paper,
                    shadowElevation = 12.dp,
                    modifier = Modifier
                        .scale(overlayScale)
                        .padding(horizontal = 40.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(SpotifyGreen, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            "Observation enregistrée",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Ink,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Elle a été ajoutée à ta liste.",
                            color = MossDark,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

fun createImageUri(context: Context): Uri {
    val imagesDir = File(context.cacheDir, "images").apply { mkdirs() }
    val file = File(imagesDir, "photo_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
}

fun createAudioFile(context: Context): File {
    val audioDir = File(context.cacheDir, "audio").apply { mkdirs() }
    return File(audioDir, "audio_${System.currentTimeMillis()}.m4a")
}

@Suppress("DEPRECATION")
fun createMediaRecorder(context: Context): MediaRecorder {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        MediaRecorder(context)
    } else {
        MediaRecorder()
    }
}