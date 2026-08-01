package ui.diagrams

import androidx.compose.ui.graphics.Color

/** Light counterpart of [DarkDiagramPalette]: same hue roles on tinted light surfaces. */
internal val LightDiagramPalette = DiagramPalette(
    diagramBg = Color(0xFFF6F8FA),
    nodeBgRule = Color(0xFFEFF4FF),
    nodeBgAnd = Color(0xFFEDF3FF),
    nodeBgOr = Color(0xFFEAF7EF),
    nodeBgNot = Color(0xFFFDEEEE),
    nodeBgCondition = Color(0xFFFFFFFF),
    nodeBgActions = Color(0xFFF3EFFE),
    borderRule = Color(0xFFC7D9FB),
    borderAnd = Color(0xFF9EC1F7),
    borderOr = Color(0xFF9BD8B0),
    borderNot = Color(0xFFF0B4B4),
    borderCondition = Color(0xFFD5DAE1),
    borderActions = Color(0xFFC9B8F5),
    lineColor = Color(0xFFC4CBD6),
    labelRule = Color(0xFF2563EB),
    labelAnd = Color(0xFF1D4ED8),
    labelOr = Color(0xFF16A34A),
    labelNot = Color(0xFFDC2626),
    labelActions = Color(0xFF7C3AED),
    labelField = Color(0xFF0891B2),
    labelOp = Color(0xFFB45309),
    labelValue = Color(0xFF16A34A),
    labelActionName = Color(0xFF7C3AED),
    labelArg = Color(0xFF1A1F2B),
    textDesc = Color(0xFF5C6470),
)
