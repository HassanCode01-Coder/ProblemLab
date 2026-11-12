package com.hassandev.problemlab

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Palette officielle Problem Lab
val Paper = Color(0xFFEDEEE6)
val Ink = Color(0xFF1C2B27)
val Moss = Color(0xFF7C8B76)
val MossDark = Color(0xFF3E4C39)
val Terracotta = Color(0xFFA8461F)

val Montserrat = FontFamily(
    Font(R.font.montserrat, FontWeight.Normal),
    Font(R.font.montserrat_medium, FontWeight.Medium),
    Font(R.font.montserrat_semibold, FontWeight.SemiBold),
    Font(R.font.montserrat_extrabold, FontWeight.ExtraBold),
)

// Tous les styles utilisent Montserrat, pour une police identique partout dans l'app
val ProblemLabTypography = Typography(
    displayLarge = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.ExtraBold, fontSize = 52.sp),
    displayMedium = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.ExtraBold, fontSize = 42.sp),
    displaySmall = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp),
    headlineLarge = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp),
    headlineMedium = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp),
    headlineSmall = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleLarge = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    titleSmall = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    bodyLarge = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    labelLarge = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 11.sp),
)

private val ProblemLabColorScheme = lightColorScheme(
    primary = Terracotta,
    onPrimary = Paper,
    background = Paper,
    surface = Paper,
    onBackground = Ink,
    onSurface = Ink,
    secondary = MossDark,
)

@Composable
fun ProblemLabTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ProblemLabColorScheme,
        typography = ProblemLabTypography,
        content = content
    )
}