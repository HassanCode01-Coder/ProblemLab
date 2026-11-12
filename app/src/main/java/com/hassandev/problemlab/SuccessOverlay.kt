package com.hassandev.problemlab

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private val SuccessGreen = Color(0xFF1DB954)

// Message de succès centré sur un voile sombre, à placer dans un Box qui occupe tout l'écran
@Composable
fun SuccessOverlay(visible: Boolean, title: String, subtitle: String? = null) {
    val overlayScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.6f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "successScale"
    )

    AnimatedVisibility(
        visible = visible,
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
                            .background(SuccessGreen, CircleShape),
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
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Ink,
                        textAlign = TextAlign.Center
                    )
                    if (subtitle != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MossDark,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}