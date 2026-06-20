package ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Modern dark-blue palette ─────────────────────────────────────────────────
// Neutral foundation
val Bg              = Color(0xFF0B1120)
val BgSurface       = Color(0xFF111C2E)
val BgElevated      = Color(0xFF1A2744)
val BgHover         = Color(0xFF243554)
val BgInput         = Color(0xFF162238)
val BorderColor     = Color(0xFF2A3A55)
val BorderSubtle    = Color(0xFF1E2D48)
val DividerColor    = Color(0xFF1F2E4A)

// Primary / info
val PrimaryBlue     = Color(0xFF3B82F6)
val PrimaryBlueDim  = Color(0xFF2563EB)
val PrimaryBlueLight= Color(0xFF60A5FA)
val PrimaryGlow     = Color(0xFF3B82F6).copy(alpha = 0.15f)

// Semantic accents
val AccentGreen     = Color(0xFF22C55E)
val AccentGreenSoft = Color(0xFF22C55E).copy(alpha = 0.12f)
val AccentRed       = Color(0xFFEF4444)
val AccentRedSoft   = Color(0xFFEF4444).copy(alpha = 0.12f)
val AccentOrange    = Color(0xFFF59E0B)
val AccentOrangeSoft= Color(0xFFF59E0B).copy(alpha = 0.12f)
val AccentPurple    = Color(0xFFA78BFA)
val AccentPurpleSoft= Color(0xFFA78BFA).copy(alpha = 0.12f)
val AccentCyan      = Color(0xFF22D3EE)

// Text
val TextPrimary     = Color(0xFFF0F4FA)
val TextSecondary   = Color(0xFF94A3B8)
val TextMuted       = Color(0xFF64748B)
val TextOnPrimary   = Color(0xFFFFFFFF)

// DSL syntax highlighting colours (kept stable so highlighting behaviour is unchanged)
val ColorKeyword = PrimaryBlueLight
val ColorLogic   = AccentOrange
val ColorField   = AccentCyan
val ColorAction  = AccentPurple
val ColorString  = AccentGreen
val ColorNumber  = PrimaryBlueLight
val ColorOp      = AccentRed

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = darkColors(
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
        ),
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
