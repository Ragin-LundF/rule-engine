package ui.diagrams

import androidx.compose.ui.graphics.Color
import ui.diagrams.model.DiagramPalette
import ui.theme.LightPalette

/**
 * Light counterpart of [DarkDiagramPalette]: same hue roles on tinted light surfaces.
 *
 * The label and surface colours are read from [LightPalette] rather than restated, because they were
 * copies of it to the digit — a diagram drawn in colours the rest of the light UI has since moved
 * away from reads as a bug. What stays literal are the per-node-kind fills and borders, which the
 * app palette has no equivalent for, and `labelOp`, a deliberately darker amber than
 * [LightPalette.accentOrange] so an operator holds up against a pale node fill.
 *
 * [DarkDiagramPalette] deliberately does *not* do this: it is GitHub-dark-inspired and diverges from
 * the app's dark theme, with only its two purples coinciding.
 */
internal val LightDiagramPalette = DiagramPalette(
    diagramBg = Color(0xFFF6F8FA),
    nodeBgRule = Color(0xFFEFF4FF),
    nodeBgAnd = Color(0xFFEDF3FF),
    nodeBgOr = Color(0xFFEAF7EF),
    nodeBgNot = Color(0xFFFDEEEE),
    nodeBgCondition = LightPalette.bgSurface,
    nodeBgActions = Color(0xFFF3EFFE),
    borderRule = Color(0xFFC7D9FB),
    borderAnd = Color(0xFF9EC1F7),
    borderOr = Color(0xFF9BD8B0),
    borderNot = Color(0xFFF0B4B4),
    borderCondition = LightPalette.borderColor,
    borderActions = Color(0xFFC9B8F5),
    lineColor = Color(0xFFC4CBD6),
    labelRule = LightPalette.primaryBlue,
    labelAnd = LightPalette.primaryBlueDim,
    labelOr = LightPalette.accentGreen,
    labelNot = LightPalette.accentRed,
    labelActions = LightPalette.accentPurple,
    labelField = LightPalette.accentCyan,
    labelOp = Color(0xFFB45309),
    labelValue = LightPalette.accentGreen,
    labelActionName = LightPalette.accentPurple,
    labelArg = LightPalette.textPrimary,
    textDesc = LightPalette.textSecondary,
)
