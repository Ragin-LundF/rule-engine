package ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Palette ──────────────────────────────────────────────────────────────────
val Bg          = Color(0xFF0D1117)
val BgSurface   = Color(0xFF161B22)
val BgElevated  = Color(0xFF21262D)
val BgHover     = Color(0xFF2D333B)
val BorderColor = Color(0xFF30363D)

val PrimaryBlue   = Color(0xFF58A6FF)
val PrimaryBlueDim = Color(0xFF1F6FEB)
val AccentGreen   = Color(0xFF3FB950)
val AccentRed     = Color(0xFFF85149)
val AccentOrange  = Color(0xFFD29922)
val AccentPurple  = Color(0xFFA78BFA)

val TextPrimary   = Color(0xFFE6EDF3)
val TextSecondary = Color(0xFF8B949E)
val TextMuted     = Color(0xFF484F58)

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
            onPrimary       = Color(0xFF0D1117),
            onSecondary     = Color(0xFF0D1117),
            onError         = Color.White,
        ),
        typography = Typography(
            h5 = TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize   = 20.sp,
                letterSpacing = 0.sp,
            ),
            h6 = TextStyle(
                fontWeight = FontWeight.SemiBold,
                fontSize   = 15.sp,
                letterSpacing = 0.sp,
            ),
            subtitle1 = TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize   = 12.sp,
                letterSpacing = 0.5.sp,
            ),
            subtitle2 = TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize   = 11.sp,
                letterSpacing = 0.25.sp,
                color = TextSecondary,
            ),
            body1 = TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize   = 13.sp,
            ),
            body2 = TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize   = 12.sp,
                color = TextSecondary,
            ),
            caption = TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize   = 11.sp,
                color = TextSecondary,
                letterSpacing = 0.25.sp,
            ),
            button = TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize   = 11.sp,
                letterSpacing = 0.5.sp,
            ),
        ),
        shapes = Shapes(
            small  = RoundedCornerShape(4.dp),
            medium = RoundedCornerShape(6.dp),
            large  = RoundedCornerShape(8.dp),
        ),
        content = content,
    )
}



