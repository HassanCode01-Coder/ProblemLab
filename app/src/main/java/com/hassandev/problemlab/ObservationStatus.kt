package com.hassandev.problemlab

import androidx.compose.ui.graphics.Color

val ALL_STATUSES = listOf("observe", "a_analyser", "valide", "idee", "opportunite", "resolu")

fun statusLabel(status: String): String = when (status) {
    "observe" -> "Observée"
    "a_analyser" -> "À analyser"
    "valide" -> "Validée"
    "idee" -> "Idée"
    "opportunite" -> "Opportunité"
    "resolu" -> "Résolue"
    else -> status.replaceFirstChar { it.uppercase() }
}

// Couleurs sobres : l'orange de la marque reste réservé aux actions
fun statusColor(status: String): Color = when (status) {
    "observe" -> Color(0xFF7C8B76)
    "a_analyser" -> Color(0xFFB7862E)
    "valide" -> Color(0xFF3F7D4E)
    "idee" -> Color(0xFF3E6A8A)
    "opportunite" -> Color(0xFFA8461F)
    "resolu" -> Color(0xFF3E4C39)
    else -> Color(0xFF7C8B76)
}