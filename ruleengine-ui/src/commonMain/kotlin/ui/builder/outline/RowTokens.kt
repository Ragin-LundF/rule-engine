package ui.builder.outline

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ui.AccentCyan
import ui.AccentOrange
import ui.AccentPurple
import ui.AccentRed
import ui.PrimaryBlue
import ui.PrimaryBlueLight
import ui.PrimaryGlow
import ui.TextPrimary
import ui.TextSecondary
import ui.builder.OperandText
import ui.builder.model.BuilderOperand
import ui.builder.model.BuilderPathStep
import ui.builder.model.filters
import ui.builder.model.mutable.MutableBuilderComparison
import ui.builder.model.mutable.MutableBuilderCondition
import ui.builder.model.selection.SelectionStep
import ui.builder.model.slice
import ui.builder.model.sort

/**
 * A condition row rendered as one line of clickable tokens.
 *
 * The row carries no controls. Every part of it is a target that selects — and the Inspector is what
 * edits — so a row is exactly as tall as its text however deep the expression inside it goes. The old
 * rows held eight controls of equal visual weight (`⠿ NOT field ▾ operator ▾ value ☐ ignoreCase ƒ ×`),
 * which is what made the three parts carrying the meaning indistinguishable from the five that did not.
 *
 * Clicking a sub-expression selects *that* sub-expression, not just the row: the aggregate inside
 * `abs(sum(a) - sum(b))` is one click away, and the Inspector opens on it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Suppress("FunctionNaming")
@Composable
internal fun ConditionTokens(
    condition: MutableBuilderCondition,
    selectedSteps: List<SelectionStep>?,
    onSelect: (List<SelectionStep>) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(space = 5.dp),
        verticalArrangement = Arrangement.spacedBy(space = 2.dp),
    ) {
        if (condition.negated) {
            FlagToken(text = "not", color = AccentRed)
        }
        Token(
            text = condition.field.ifBlank { "…" },
            color = AccentCyan,
            selected = selectedSteps?.isEmpty() == true,
            onClick = { onSelect(emptyList()) },
        )
        Token(
            text = condition.operator,
            color = TextSecondary,
            selected = false,
            onClick = { onSelect(emptyList()) },
        )
        Token(
            text = conditionValueText(condition = condition),
            color = valueColour(condition = condition),
            selected = false,
            onClick = { onSelect(emptyList()) },
        )
        if (condition.ignoreCase) {
            FlagToken(text = "ignoreCase", color = AccentCyan)
        }
    }
}

/** A comparison row: the two operands around the operator, each drillable. */
@OptIn(ExperimentalLayoutApi::class)
@Suppress("FunctionNaming")
@Composable
internal fun ComparisonTokens(
    comparison: MutableBuilderComparison,
    selectedSteps: List<SelectionStep>?,
    onSelect: (List<SelectionStep>) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(space = 5.dp),
        verticalArrangement = Arrangement.spacedBy(space = 2.dp),
    ) {
        if (comparison.negated) {
            FlagToken(text = "not", color = AccentRed)
        }
        OperandTokens(
            operand = comparison.left,
            steps = listOf(SelectionStep.Left),
            selectedSteps = selectedSteps,
            onSelect = onSelect,
        )
        Token(
            text = comparison.operator,
            color = TextSecondary,
            selected = selectedSteps?.isEmpty() == true,
            onClick = { onSelect(emptyList()) },
        )
        OperandTokens(
            operand = comparison.right,
            steps = listOf(SelectionStep.Right),
            selectedSteps = selectedSteps,
            onSelect = onSelect,
        )
        if (comparison.ignoreCase) {
            FlagToken(text = "ignoreCase", color = AccentCyan)
        }
    }
}

/**
 * One operand, as tokens, with each level addressable.
 *
 * Renders the DSL the operand generates rather than a summary, so the row stays learnable: what is on
 * screen is what the file will say.
 */
@Suppress("FunctionNaming")
@Composable
internal fun OperandTokens(
    operand: BuilderOperand,
    steps: List<SelectionStep>,
    selectedSteps: List<SelectionStep>?,
    onSelect: (List<SelectionStep>) -> Unit,
) {
    val isSelected = selectedSteps == steps
    when (operand) {
        is BuilderOperand.FieldRef -> PathTokens(
            path = operand.path,
            steps = steps,
            selectedSteps = selectedSteps,
            onSelect = onSelect,
        )

        is BuilderOperand.Literal -> Token(
            text = OperandText.toDsl(operand = operand).ifBlank { "…" },
            color = if (operand.numeric) AccentOrange else AccentPurple,
            selected = isSelected,
            onClick = { onSelect(steps) },
        )

        is BuilderOperand.ListLiteral -> Token(
            text = OperandText.toDsl(operand = operand),
            color = AccentPurple,
            selected = isSelected,
            onClick = { onSelect(steps) },
        )

        is BuilderOperand.Aggregate -> {
            Token(
                text = "${operand.function}(",
                color = PrimaryBlueLight,
                selected = isSelected,
                onClick = { onSelect(steps) },
            )
            PathTokens(
                path = operand.path,
                steps = steps,
                selectedSteps = selectedSteps,
                onSelect = onSelect,
            )
            Punctuation(text = ")")
        }

        is BuilderOperand.Call -> CallTokens(
            call = operand,
            steps = steps,
            selected = isSelected,
            selectedSteps = selectedSteps,
            onSelect = onSelect,
        )

        is BuilderOperand.Calc -> CalcTokens(
            calc = operand,
            steps = steps,
            selectedSteps = selectedSteps,
            onSelect = onSelect,
        )
    }
}

/**
 * A function call: its name, then its arguments, each of which is an operand in its own right.
 *
 * Split out of [OperandTokens] for length only — an argument recurses straight back into it, so a
 * call nested inside a call is drawn by the same two functions taking turns.
 */
@Suppress("FunctionNaming")
@Composable
private fun CallTokens(
    call: BuilderOperand.Call,
    steps: List<SelectionStep>,
    selected: Boolean,
    selectedSteps: List<SelectionStep>?,
    onSelect: (List<SelectionStep>) -> Unit,
) {
    Token(
        text = "${call.function}(",
        color = PrimaryBlueLight,
        selected = selected,
        onClick = { onSelect(steps) },
    )
    call.args.forEachIndexed { index, arg ->
        if (index > 0) {
            Punctuation(text = ",")
        }
        OperandTokens(
            operand = arg,
            steps = steps + SelectionStep.Argument(index = index),
            selectedSteps = selectedSteps,
            onSelect = onSelect,
        )
    }
    Punctuation(text = ")")
}

/**
 * A calculation: its terms with the arithmetic between them.
 *
 * The brackets are drawn from the model's own `parenthesized` flag rather than inferred from
 * precedence, because that flag is what the generator writes — so what is on screen is what the file
 * will say, including a redundant pair the author put there on purpose.
 */
@Suppress("FunctionNaming")
@Composable
private fun CalcTokens(
    calc: BuilderOperand.Calc,
    steps: List<SelectionStep>,
    selectedSteps: List<SelectionStep>?,
    onSelect: (List<SelectionStep>) -> Unit,
) {
    if (calc.parenthesized) {
        Punctuation(text = "(")
    }
    calc.terms.forEachIndexed { index, term ->
        if (index > 0) {
            Punctuation(text = term.operator)
        }
        OperandTokens(
            operand = term.operand,
            steps = steps + SelectionStep.Term(index = index),
            selectedSteps = selectedSteps,
            onSelect = onSelect,
        )
    }
    if (calc.parenthesized) {
        Punctuation(text = ")")
    }
}

/**
 * A path, one token per segment, with what each segment carries shown beside it.
 *
 * The badges are the point: a silent filter, or a silent truncation that decides which elements the
 * filter then sees, is exactly what a reader must not have to open anything to discover.
 */
@Suppress("FunctionNaming")
@Composable
private fun PathTokens(
    path: List<BuilderPathStep>,
    steps: List<SelectionStep>,
    selectedSteps: List<SelectionStep>?,
    onSelect: (List<SelectionStep>) -> Unit,
) {
    path.forEachIndexed { depth, segment ->
        if (depth > 0) {
            Punctuation(text = ".")
        }
        val segmentSteps = steps + SelectionStep.Segment(index = depth)
        Token(
            text = segment.name.ifBlank { "…" },
            color = AccentCyan,
            selected = selectedSteps == segmentSteps,
            onClick = { onSelect(segmentSteps) },
        )
        segment.sort?.let { ordering ->
            DecorationBadge(
                text = "↕${ordering.member ?: "values"}${if (ordering.descending) "↓" else "↑"}",
            )
        }
        segment.slice?.let { bound ->
            DecorationBadge(text = "${if (bound.fromEnd) "last" else "first"} ${bound.count}")
        }
        val filterCount = segment.filters.size
        if (filterCount > 0) {
            DecorationBadge(text = if (filterCount == 1) "where" else "where $filterCount")
        }
    }
}

/** A clickable token. Selection is shown on the token, not on the row, so the target is obvious. */
@Suppress("FunctionNaming")
@Composable
private fun Token(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.body2,
        color = color,
        modifier = Modifier
            .clip(shape = RoundedCornerShape(size = 4.dp))
            .background(color = if (selected) PrimaryGlow else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 3.dp, vertical = 1.dp),
    )
}

/** Structural punctuation — not a target, because there is nothing behind it to edit. */
@Suppress("FunctionNaming")
@Composable
private fun Punctuation(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.body2,
        color = TextSecondary,
        modifier = Modifier.padding(horizontal = 1.dp),
    )
}

/** `not` and `ignoreCase`, shown inline as part of the expression rather than as separate controls. */
@Suppress("FunctionNaming")
@Composable
private fun FlagToken(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
        color = color,
        modifier = Modifier
            .clip(shape = RoundedCornerShape(size = 4.dp))
            .background(color = color.copy(alpha = 0.14f))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

/** What a path segment carries, visible while the Inspector is elsewhere. */
@Suppress("FunctionNaming")
@Composable
private fun DecorationBadge(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
        color = PrimaryBlueLight,
        modifier = Modifier
            .clip(shape = RoundedCornerShape(percent = 50))
            .background(color = PrimaryBlue.copy(alpha = 0.16f))
            .padding(horizontal = 5.dp),
    )
}

/** The value side of a simple condition, rendered the way the DSL will write it. */
private fun conditionValueText(condition: MutableBuilderCondition): String {
    return when {
        condition.listItems.isNotEmpty() -> condition.listItems.joinToString(
            separator = ", ",
            prefix = "[",
            postfix = "]",
        ) { item -> "\"$item\"" }

        condition.valueTo.isNotBlank() -> "${condition.value} ${condition.valueTo}"
        condition.value.isBlank() -> "…"
        else -> condition.value
    }
}

/** Numbers and booleans read as data; text reads as text. */
private fun valueColour(condition: MutableBuilderCondition): androidx.compose.ui.graphics.Color {
    val text = condition.value.trim()
    return when {
        condition.listItems.isNotEmpty() -> AccentPurple
        text.toDoubleOrNull() != null -> AccentOrange
        text == "true" || text == "false" -> AccentOrange
        else -> AccentPurple
    }
}

/** A statement row's text — an action, a `set` or an `add` — as one line. */
@Suppress("FunctionNaming")
@Composable
internal fun StatementText(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.body2,
        color = TextPrimary,
        modifier = Modifier
            .clip(shape = RoundedCornerShape(size = 4.dp))
            .background(color = if (selected) PrimaryGlow else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
    )
}
