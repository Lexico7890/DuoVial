package com.duovial.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val DuoVialBackground = Color(0xFF0D1B2A)
val DuoVialSurface = Color(0xFF415A77)
val DuoVialNeonGreen = Color(0xFF00E676)
val DuoVialNeonRed = Color(0xFFFF1744)
val DuoVialAmber = Color(0xFFFFB300)
val DuoVialTextPrimary = Color(0xFFFFFFFF)
val DuoVialTextSecondary = Color(0xFF64748B)
val DuoVialBorder = Color(0xFF1E293B)
val DuoVialCardBackground = Color(0xFF0D1B2A)
val DuoVialGreenDim = Color(0x0D00E676)
val DuoVialAmberLight = Color(0x1AFFB300)
val DuoVialOrange = Color(0xFFFF9F0A)

private val DuoVialDarkColorScheme = darkColorScheme(
    primary = DuoVialNeonGreen,
    onPrimary = DuoVialBackground,
    secondary = DuoVialSurface,
    onSecondary = DuoVialTextPrimary,
    tertiary = DuoVialAmber,
    background = DuoVialBackground,
    onBackground = DuoVialTextPrimary,
    surface = DuoVialCardBackground,
    onSurface = DuoVialTextPrimary,
    surfaceVariant = DuoVialSurface,
    onSurfaceVariant = DuoVialTextSecondary,
    outline = DuoVialBorder,
    error = DuoVialNeonRed,
    onError = Color.White,
)

val DuoVialTypography = Typography(
    titleLarge = TextStyle(
        fontWeight = FontWeight.W900,
        fontSize = 22.sp,
        letterSpacing = 0.5.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.W800,
        fontSize = 16.sp,
        letterSpacing = 1.sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.W800,
        fontSize = 14.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.W600,
        fontSize = 14.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.W500,
        fontSize = 12.sp,
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.W400,
        fontSize = 11.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.W800,
        fontSize = 13.sp,
        letterSpacing = 1.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.W700,
        fontSize = 10.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.W700,
        fontSize = 9.sp,
    ),
)

@Composable
fun DuoVialTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DuoVialDarkColorScheme,
        typography = DuoVialTypography,
        content = content,
    )
}
