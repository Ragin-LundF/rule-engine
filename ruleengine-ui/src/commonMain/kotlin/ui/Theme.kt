package ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Shapes
import androidx.compose.material.Typography
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ui.theme.ThemeController

// ── Modern dark-blue palette ─────────────────────────────────────────────────
// Every token is a getter over the current theme's snapshot-state palette
// (ui.theme.ThemeController), rather than a plain val. Compose snapshot reads
// register during composition no matter where the read happens syntactically,
// so every existing usage site (including non-composable code such as
// SyntaxHighlighter.annotateRule) keeps working unchanged and recomposes when
// the theme switches.

// Neutral foundation
val Bg: Color get() = ThemeController.palette.bg
val BgSurface: Color get() = ThemeController.palette.bgSurface
val BgElevated: Color get() = ThemeController.palette.bgElevated
val BgHover: Color get() = ThemeController.palette.bgHover
val BgInput: Color get() = ThemeController.palette.bgInput
val BorderColor: Color get() = ThemeController.palette.borderColor

// Primary / info
val PrimaryBlue: Color get() = ThemeController.palette.primaryBlue
val PrimaryBlueDim: Color get() = ThemeController.palette.primaryBlueDim
val PrimaryBlueLight: Color get() = ThemeController.palette.primaryBlueLight
val PrimaryGlow: Color get() = PrimaryBlue.copy(alpha = 0.15f)

// Semantic accents
val AccentGreen: Color get() = ThemeController.palette.accentGreen
val AccentGreenSoft: Color get() = AccentGreen.copy(alpha = 0.12f)
val AccentRed: Color get() = ThemeController.palette.accentRed
val AccentOrange: Color get() = ThemeController.palette.accentOrange
val AccentOrangeSoft: Color get() = AccentOrange.copy(alpha = 0.12f)
val AccentPurple: Color get() = ThemeController.palette.accentPurple
val AccentPurpleSoft: Color get() = AccentPurple.copy(alpha = 0.12f)
val AccentCyan: Color get() = ThemeController.palette.accentCyan
val AccentCyanSoft: Color get() = AccentCyan.copy(alpha = 0.12f)

// Text
val TextPrimary: Color get() = ThemeController.palette.textPrimary
val TextSecondary: Color get() = ThemeController.palette.textSecondary
val TextMuted: Color get() = ThemeController.palette.textMuted
val TextOnPrimary: Color get() = ThemeController.palette.textOnPrimary

// DSL syntax highlighting colours (kept stable so highlighting behaviour is unchanged).
// Mapping matches the prototype's code pane.
val ColorKeyword: Color get() = PrimaryBlue
val ColorLogic: Color get() = AccentPurple
val ColorField: Color get() = AccentCyan
val ColorAction: Color get() = AccentPurple
val ColorString: Color get() = AccentGreen
val ColorNumber: Color get() = AccentOrange
val ColorOp: Color get() = TextSecondary

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val colors = if (ThemeController.isDark) {
        darkColors(
            background      = Bg,
            surface         = BgSurface,
            primary         = PrimaryBlue,
            primaryVariant  = PrimaryBlueDim,
            secondary       = AccentGreen,
            secondaryVariant= AccentPurple,
            error           = AccentRed,
            onBackground    = TextPrimary,
            onSurface       = TextPrimary,
            onPrimary       = TextOnPrimary,
            onSecondary     = Bg,
            onError         = Color.White,
        )
    } else {
        lightColors(
            background      = Bg,
            surface         = BgSurface,
            primary         = PrimaryBlue,
            primaryVariant  = PrimaryBlueDim,
            secondary       = AccentGreen,
            secondaryVariant= AccentPurple,
            error           = AccentRed,
            onBackground    = TextPrimary,
            onSurface       = TextPrimary,
            onPrimary       = TextOnPrimary,
            onSecondary     = Bg,
            onError         = Color.White,
        )
    }
    MaterialTheme(
        colors = colors,
        typography = Typography(
            h4 = TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize   = 24.sp,
                letterSpacing = (-0.5).sp,
                color = TextPrimary,
            ),
            h5 = TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize   = 20.sp,
                letterSpacing = 0.sp,
                color = TextPrimary,
            ),
            h6 = TextStyle(
                fontWeight = FontWeight.SemiBold,
                fontSize   = 16.sp,
                letterSpacing = 0.sp,
                color = TextPrimary,
            ),
            subtitle1 = TextStyle(
                fontWeight = FontWeight.SemiBold,
                fontSize   = 13.sp,
                letterSpacing = 0.25.sp,
                color = TextPrimary,
            ),
            subtitle2 = TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize   = 12.sp,
                letterSpacing = 0.25.sp,
                color = TextSecondary,
            ),
            body1 = TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize   = 14.sp,
                color = TextPrimary,
            ),
            body2 = TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize   = 13.sp,
                color = TextSecondary,
            ),
            caption = TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize   = 12.sp,
                color = TextSecondary,
                letterSpacing = 0.25.sp,
            ),
            button = TextStyle(
                fontWeight = FontWeight.SemiBold,
                fontSize   = 12.sp,
                letterSpacing = 0.25.sp,
            ),
        ),
        shapes = Shapes(
            small  = RoundedCornerShape(6.dp),
            medium = RoundedCornerShape(10.dp),
            large  = RoundedCornerShape(14.dp),
        ),
        content = content,
    )
}
