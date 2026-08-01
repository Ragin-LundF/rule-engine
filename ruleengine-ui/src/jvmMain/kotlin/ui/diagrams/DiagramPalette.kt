package ui.diagrams

import androidx.compose.ui.graphics.Color

/** One theme's full set of diagram colors; the active one is picked in Colors.kt. */
internal data class DiagramPalette(
    val diagramBg: Color,
    val nodeBgRule: Color,
    val nodeBgAnd: Color,
    val nodeBgOr: Color,
    val nodeBgNot: Color,
    val nodeBgCondition: Color,
    val nodeBgActions: Color,
    val borderRule: Color,
    val borderAnd: Color,
    val borderOr: Color,
    val borderNot: Color,
    val borderCondition: Color,
    val borderActions: Color,
    val lineColor: Color,
    val labelRule: Color,
    val labelAnd: Color,
    val labelOr: Color,
    val labelNot: Color,
    val labelActions: Color,
    val labelField: Color,
    val labelOp: Color,
    val labelValue: Color,
    val labelActionName: Color,
    val labelArg: Color,
    val textDesc: Color,
)
