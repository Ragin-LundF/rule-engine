package ui.diagrams

import androidx.compose.ui.graphics.Color
import ui.theme.DarkPalette

/**
 * The original GitHub-dark-inspired diagram palette, used when the app theme is dark.
 *
 * Unlike [LightDiagramPalette] this is *not* derived from the app's own dark palette: its blues,
 * greens and reds are GitHub's, chosen so a dense diagram stays legible, and they differ from
 * [DarkPalette] on purpose. Only the two purples coincide, and those are read from the app palette
 * so the one genuine overlap cannot drift. Do not "align" the rest — that is a design change.
 */
internal val DarkDiagramPalette = DiagramPalette(
    diagramBg = Color(0xFF0D1117),
    nodeBgRule = Color(0xFF1C2333),
    nodeBgAnd = Color(0xFF1A2035),
    nodeBgOr = Color(0xFF1A2D1A),
    nodeBgNot = Color(0xFF2D1A1A),
    nodeBgCondition = Color(0xFF161B22),
    nodeBgActions = Color(0xFF1A2233),
    borderRule = Color(0xFF3B4A6B),
    borderAnd = Color(0xFF2B5086),
    borderOr = Color(0xFF2B6B2B),
    borderNot = Color(0xFF7B2B2B),
    borderCondition = Color(0xFF30363D),
    borderActions = Color(0xFF3B5A8B),
    lineColor = Color(0xFF3D4450),
    labelRule = Color(0xFF79C0FF),
    labelAnd = Color(0xFF58A6FF),
    labelOr = Color(0xFF3FB950),
    labelNot = Color(0xFFF85149),
    labelActions = DarkPalette.accentPurple,
    labelField = Color(0xFF79C0FF),
    labelOp = Color(0xFFD29922),
    labelValue = Color(0xFF3FB950),
    labelActionName = DarkPalette.accentPurple,
    labelArg = Color(0xFFE6EDF3),
    textDesc = Color(0xFF8B949E),
)
