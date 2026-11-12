package com.hassandev.problemlab

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Camera
import compose.icons.fontawesomeicons.solid.Robot
import compose.icons.fontawesomeicons.solid.Search
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private data class OnboardingPage(val icon: ImageVector, val title: String, val text: String)

private val onboardingPages = listOf(
    OnboardingPage(
        FontAwesomeIcons.Solid.Search,
        "Observe autour de toi",
        "Un souci dans ton quartier, un besoin qui revient, une situation qui t'interpelle ? Chaque problème repéré est le début d'une opportunité."
    ),
    OnboardingPage(
        FontAwesomeIcons.Solid.Camera,
        "Capture en quelques secondes",
        "Ajoute une photo, une note vocale et ta position GPS, puis choisis le secteur et le statut de ton observation."
    ),
    OnboardingPage(
        FontAwesomeIcons.Solid.Robot,
        "Laisse l'Agent IA t'aider",
        "Depuis l'onglet Agent IA, transforme tes observations en propositions de solutions et en idées de projets."
    ),
)

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    var showWelcome by remember { mutableStateOf(true) }
    var page by remember { mutableStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val alphaAnim = remember { Animatable(0f) }
    val scaleAnim = remember { Animatable(0.7f) }

    // Animation du message de bienvenue, puis passage au didacticiel
    LaunchedEffect(Unit) {
        launch { alphaAnim.animateTo(1f, tween(900)) }
        launch {
            scaleAnim.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
            )
        }
        delay(2800)
        alphaAnim.animateTo(0f, tween(500))
        showWelcome = false
    }

    // Note dans le compte que le didacticiel a été vu, puis ferme l'écran
    fun terminer() {
        if (saving) return
        saving = true
        scope.launch {
            try {
                supabase.auth.updateUser {
                    data = buildJsonObject { put("onboarding_done", true) }
                }
            } catch (e: Exception) {
                android.util.Log.e("PROBLEMLAB_DEBUG", "Erreur sauvegarde didacticiel", e)
            } finally {
                onFinished()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
    ) {
        if (showWelcome) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp)
                    .alpha(alphaAnim.value)
                    .scale(scaleAnim.value),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Bienvenue sur", style = MaterialTheme.typography.titleLarge, color = MossDark)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "PROBLEM LAB",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Observer. Comprendre. Transformer.",
                    color = Terracotta,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { terminer() }, enabled = !saving) {
                        Text("Passer", color = MossDark)
                    }
                }

                AnimatedContent(
                    targetState = page,
                    modifier = Modifier.weight(1f),
                    label = "onboarding",
                    transitionSpec = {
                        (slideInHorizontally { it / 3 } + fadeIn(tween(400)))
                            .togetherWith(slideOutHorizontally { -it / 3 } + fadeOut(tween(250)))
                    }
                ) { index ->
                    val p = onboardingPages[index]
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .background(Terracotta.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                p.icon,
                                contentDescription = null,
                                tint = Terracotta,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(28.dp))
                        Text(
                            p.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Ink,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(p.text, color = MossDark, textAlign = TextAlign.Center)
                    }
                }

                Row(
                    modifier = Modifier.padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    onboardingPages.indices.forEach { i ->
                        Box(
                            modifier = Modifier
                                .size(if (i == page) 10.dp else 8.dp)
                                .background(
                                    if (i == page) Terracotta else Color(0xFFBFBAA8),
                                    CircleShape
                                )
                        )
                    }
                }

                Button(
                    onClick = {
                        if (page < onboardingPages.lastIndex) page += 1 else terminer()
                    },
                    enabled = !saving,
                    colors = ButtonDefaults.buttonColors(containerColor = Terracotta, contentColor = Paper),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        if (page < onboardingPages.lastIndex) "Suivant" else "Commencer",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}