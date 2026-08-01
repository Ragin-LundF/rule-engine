package ui.diagrams

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ui.diagrams.model.DiagramPalette
import ui.theme.ThemeController

// ── Colours used only in the diagram ─────────────────────────────────────────
// Getters over the active palette so the diagrams follow the app's light/dark theme.

private val palette: DiagramPalette
    get() = if (ThemeController.isDark) DarkDiagramPalette else LightDiagramPalette

internal val DiagramBg: Color get() = palette.diagramBg
internal val NodeBgRule: Color get() = palette.nodeBgRule
internal val NodeBgAnd: Color get() = palette.nodeBgAnd
internal val NodeBgOr: Color get() = palette.nodeBgOr
internal val NodeBgNot: Color get() = palette.nodeBgNot
internal val NodeBgCondition: Color get() = palette.nodeBgCondition
internal val NodeBgActions: Color get() = palette.nodeBgActions
internal val BorderRule: Color get() = palette.borderRule
internal val BorderAnd: Color get() = palette.borderAnd
internal val BorderOr: Color get() = palette.borderOr
internal val BorderNot: Color get() = palette.borderNot
internal val BorderCondition: Color get() = palette.borderCondition
internal val BorderActions: Color get() = palette.borderActions
internal val LineColor: Color get() = palette.lineColor
internal val LabelRule: Color get() = palette.labelRule
internal val LabelAnd: Color get() = palette.labelAnd
internal val LabelOr: Color get() = palette.labelOr
internal val LabelNot: Color get() = palette.labelNot
internal val LabelActions: Color get() = palette.labelActions
internal val LabelField: Color get() = palette.labelField
internal val LabelOp: Color get() = palette.labelOp
internal val LabelValue: Color get() = palette.labelValue
internal val LabelActionName: Color get() = palette.labelActionName
internal val LabelArg: Color get() = palette.labelArg
internal val TextDesc: Color get() = palette.textDesc

internal val ConnectorW = 1.5.dp
