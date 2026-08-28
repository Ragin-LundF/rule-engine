package ui.builder.inspector

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ui.AccentCyan
import ui.BgInput
import ui.TextSecondary
import ui.builder.OperandText
import ui.builder.model.BuilderOperand
import ui.builder.model.mutable.BuilderEditorState
import ui.builder.model.selection.SelectionStep
import ui.builder.selection.SelectionResolver
import ui.builder.selection.SelectionTarget
import ui.components.TinyButton

/**
 * The trail from the row down to whatever is being edited, and a way back up each level.
 *
 * This is what makes depth navigable. The old builder expressed it by stacking a panel under the row
 * for every level, which pushed the row off screen and left no way to see where you were; a trail is
 * one line that says it and one click that leaves.
 *
 * Derived from the selection rather than remembered, so it cannot disagree with what the panel shows.
 */
@OptIn(ExperimentalLayoutApi::class)
@Suppress("FunctionNaming")
@Composable
internal fun SelectionTrail(
    anchorLabel: String,
    steps: List<SelectionStep>,
    labelAt: (Int) -> String,
    onNavigate: (List<SelectionStep>) -> Unit,
) {
    // A tinted band rather than loose controls on the panel background. The trail and the editor below
    // it are different kinds of thing — one moves you, the other changes the rule — and a reader has to
    // be able to tell at a glance which is which. Grouping the navigation onto its own surface says that
    // before anything is read; the divider the caller adds underneath says where it ends.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape = RoundedCornerShape(size = 8.dp))
            .background(color = BgInput)
            .padding(all = 8.dp),
    ) {
        Text(
            text = "WHERE YOU ARE",
            style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        // Its own row, above the trail, so it is in the same place at every depth. When it was just the
        // leftmost crumb in a row of identical buttons it moved as the trail grew, and the *loudest*
        // element was the current position — which is where you already are, not somewhere to go. The
        // way out has to be the thing that stands out.
        if (steps.isNotEmpty()) {
            BackButton(
                label = if (steps.size == 1) anchorLabel else labelAt(steps.lastIndex - 1),
                onClick = { onNavigate(steps.dropLast(n = 1)) },
            )
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(space = 4.dp),
            verticalArrangement = Arrangement.spacedBy(space = 2.dp),
        ) {
            TinyButton(text = anchorLabel, onClick = { onNavigate(emptyList()) })
            steps.forEachIndexed { index, _ ->
                Text(
                    text = "›",
                    style = MaterialTheme.typography.caption,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
                TinyButton(
                    text = labelAt(index),
                    primary = index == steps.lastIndex,
                    onClick = { onNavigate(steps.take(n = index + 1)) },
                )
            }
        }
    }
}

/**
 * The way back up one level.
 *
 * Named after where it goes rather than just carrying an arrow, because "back" from four levels down in
 * `abs(sum(a) - sum(b))` is ambiguous otherwise — and the destination is the thing being looked for.
 *
 * Cyan rather than the trail's blue on purpose: blue marks the *current* position throughout the
 * Inspector, so reusing it here would put the action and the state in the same visual language. This is
 * the one control in the panel that leaves where you are, and it is filled rather than outlined so it
 * reads as a button and not as another crumb.
 */
@Suppress("FunctionNaming")
@Composable
private fun BackButton(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(bottom = 8.dp)
            .clip(shape = RoundedCornerShape(size = 6.dp))
            .background(color = AccentCyan.copy(alpha = 0.16f))
            .border(
                width = 1.dp,
                color = AccentCyan.copy(alpha = 0.55f),
                shape = RoundedCornerShape(size = 6.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 6.dp),
    ) {
        Text(
            text = "←",
            style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.Bold),
            color = AccentCyan,
        )
        Text(
            text = "back to $label",
            style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
            color = AccentCyan,
        )
    }
}

/**
 * The whole expression tree of the selected row, with the current position marked.
 *
 * Orientation while drilled in. A trail says how you got here; this says what else is here — which is
 * the thing the old one-panel-at-a-time expansion could never show, because only one side of a
 * comparison could be open at once.
 */
@Suppress("FunctionNaming")
@Composable
internal fun ExpressionOutline(
    state: BuilderEditorState,
    anchorId: String,
    steps: List<SelectionStep>,
    onNavigate: (List<SelectionStep>) -> Unit,
) {
    val lines = outlineOf(state = state, anchorId = anchorId)
    if (lines.size < 2) {
        return
    }
    InspectorSection(title = "Structure")
    Column(modifier = Modifier.fillMaxWidth()) {
        lines.forEach { line ->
            InspectorLine(
                text = line.label,
                selected = line.steps == steps,
                onClick = { onNavigate(line.steps) },
                indent = line.depth,
            )
        }
    }
}

/** One line of the outline: what it is, how deep, and the selection that reaches it. */
private data class OutlineLine(
    val label: String,
    val depth: Int,
    val steps: List<SelectionStep>,
)

/**
 * Flattens a row's operand tree into lines.
 *
 * Walks the model directly rather than through the resolver: the resolver answers "what is at this
 * path", and this needs the mirror question, "what paths are there".
 */
private fun outlineOf(state: BuilderEditorState, anchorId: String): List<OutlineLine> {
    val target = SelectionResolver.resolveCondition(
        state = state,
        conditionId = anchorId,
        steps = emptyList(),
    ) ?: return emptyList()

    val lines = mutableListOf<OutlineLine>()
    when (target) {
        is SelectionTarget.Comparison -> {
            lines += OutlineLine(
                label = "${target.comparison.operator}  (comparison)",
                depth = 0,
                steps = emptyList(),
            )
            appendOperand(
                operand = target.comparison.left,
                steps = listOf(SelectionStep.Left),
                depth = 1,
                lines = lines,
            )
            appendOperand(
                operand = target.comparison.right,
                steps = listOf(SelectionStep.Right),
                depth = 1,
                lines = lines,
            )
        }

        is SelectionTarget.Condition -> lines += OutlineLine(
            label = "${target.condition.field} ${target.condition.operator}",
            depth = 0,
            steps = emptyList(),
        )

        else -> Unit
    }
    return lines
}

private fun appendOperand(
    operand: BuilderOperand,
    steps: List<SelectionStep>,
    depth: Int,
    lines: MutableList<OutlineLine>,
) {
    when (operand) {
        is BuilderOperand.Aggregate -> {
            lines += OutlineLine(label = "Σ ${operand.function}", depth = depth, steps = steps)
            operand.path.forEachIndexed { index, step ->
                lines += OutlineLine(
                    label = "◇ ${step.name}",
                    depth = depth + 1,
                    steps = steps + SelectionStep.Segment(index = index),
                )
            }
        }

        is BuilderOperand.Call -> {
            lines += OutlineLine(label = "ƒ ${operand.function}", depth = depth, steps = steps)
            operand.args.forEachIndexed { index, arg ->
                appendOperand(
                    operand = arg,
                    steps = steps + SelectionStep.Argument(index = index),
                    depth = depth + 1,
                    lines = lines,
                )
            }
        }

        is BuilderOperand.Calc -> {
            lines += OutlineLine(label = "± calculation", depth = depth, steps = steps)
            operand.terms.forEachIndexed { index, term ->
                appendOperand(
                    operand = term.operand,
                    steps = steps + SelectionStep.Term(index = index),
                    depth = depth + 1,
                    lines = lines,
                )
            }
        }

        is BuilderOperand.FieldRef -> lines += OutlineLine(
            label = "◇ ${OperandText.pathToDsl(path = operand.path)}",
            depth = depth,
            steps = steps,
        )

        is BuilderOperand.Literal -> lines += OutlineLine(
            label = "# ${OperandText.toDsl(operand = operand)}",
            depth = depth,
            steps = steps,
        )

        is BuilderOperand.ListLiteral -> lines += OutlineLine(
            label = "# list of ${operand.items.size}",
            depth = depth,
            steps = steps,
        )
    }
}

/** A short label for one step of a trail, given what it points at. */
internal fun stepLabel(step: SelectionStep, target: SelectionTarget?): String {
    val fallback = when (step) {
        SelectionStep.Left -> "left"
        SelectionStep.Right -> "right"
        SelectionStep.Value -> "value"
        SelectionStep.Extraction -> "extract"
        is SelectionStep.Argument -> "arg ${step.index + 1}"
        is SelectionStep.Term -> "term ${step.index + 1}"
        is SelectionStep.Segment -> "step ${step.index + 1}"
        is SelectionStep.Filter -> "where ${step.index + 1}"
    }
    return when (target) {
        is SelectionTarget.Operand -> OperandText.toLabel(operand = target.operand).ifBlank { fallback }
        is SelectionTarget.Segment -> target.segment.name.ifBlank { fallback }
        is SelectionTarget.Filter -> "[ ${OperandText.filterToDsl(filter = target.filter)} ]"
        else -> fallback
    }
}
