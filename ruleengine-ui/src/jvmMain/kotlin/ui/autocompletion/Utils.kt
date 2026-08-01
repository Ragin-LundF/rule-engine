package ui.autocompletion

import androidx.compose.ui.graphics.Color
import ui.ColorAction
import ui.ColorField
import ui.ColorKeyword
import ui.ColorLogic
import ui.ColorNumber
import ui.ColorOp

// Styling helpers used by the dropdown UI
internal fun kindColor(kind: CompletionKind): Color = when (kind) {
    CompletionKind.KEYWORD  -> ColorKeyword
    CompletionKind.LOGIC    -> ColorLogic
    CompletionKind.FIELD    -> ColorField
    CompletionKind.ACTION   -> ColorAction
    CompletionKind.LITERAL  -> ColorNumber
    CompletionKind.OPERATOR -> ColorOp
}

internal fun kindLabel(kind: CompletionKind): String = when (kind) {
    CompletionKind.KEYWORD  -> "kw"
    CompletionKind.LOGIC    -> "op"
    CompletionKind.FIELD    -> "field"
    CompletionKind.ACTION   -> "action"
    CompletionKind.LITERAL  -> "lit"
    CompletionKind.OPERATOR -> "op"
}

