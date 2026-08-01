package ui.theme

import androidx.compose.ui.graphics.Color

/** One theme's full set of base colors. Soft/alpha variants are derived in Theme.kt. */
data class AppPalette(
    val bg: Color,
    val bgSurface: Color,
    val bgElevated: Color,
    val bgHover: Color,
    val bgInput: Color,
    val borderColor: Color,
    val borderSubtle: Color,
    val dividerColor: Color,
    val primaryBlue: Color,
    val primaryBlueDim: Color,
    val primaryBlueLight: Color,
    val accentGreen: Color,
    val accentRed: Color,
    val accentOrange: Color,
    val accentPurple: Color,
    val accentCyan: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textOnPrimary: Color,
)
