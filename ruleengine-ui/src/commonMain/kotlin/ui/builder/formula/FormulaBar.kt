package ui.builder.formula

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ui.AccentGreen
import ui.AccentRed
import ui.BgInput
import ui.BorderColor
import ui.PrimaryBlue
import ui.TextPrimary
import ui.TextSecondary
import ui.builder.formula.model.FormulaResult
import ui.builder.model.BuilderConditionNode
import ui.components.TinyButton

/**
 * The selected row as editable text.
 *
 * The two ways of building a rule teach each other here. The canvas shows structure and the Inspector
 * shows one part at a time, but neither teaches the language — and the language is what the author has
 * to read in the file, in a diff, in a code review. Typing `count(invoices) > 2` and watching the row
 * rebuild is the fastest way to learn what the visual editor is doing, and typing is faster than four
 * dropdowns for anyone who already knows.
 *
 * The safety property is that nothing is applied until it parses. The draft is local, the feedback is
 * live, and the rule is only touched on ⏎ with a clean parse — because the Builder rewrites the whole
 * rule text on every edit, so an expression applied half-understood is written to the file that way.
 *
 * [parse] arrives as a lambda rather than being called directly: the engine's parser is JVM-only and
 * this composable is in `commonMain`, and a lambda is the module's convention for that (dumb
 * composables, events out) — cheaper than an `expect`/`actual` pair for one function.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
fun FormulaBar(
    /** The selected row's current DSL, or null when nothing editable is selected. */
    text: String?,
    parse: (String) -> FormulaResult,
    onApply: (BuilderConditionNode) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (text == null) {
        EmptyFormulaBar(modifier = modifier)
        return
    }

    // Keyed on the incoming text, so selecting another row replaces the draft rather than leaving the
    // previous row's text sitting in the box over a different selection.
    var draft by remember(text) { mutableStateOf(value = text) }
    var feedback by remember(text) { mutableStateOf<FormulaResult?>(value = null) }

    val edited = draft.trim() != text.trim()

    fun apply() {
        val result = parse(draft)
        feedback = result
        if (result is FormulaResult.Parsed) {
            onApply(result.node)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape = RoundedCornerShape(size = 6.dp))
            .background(color = BgInput)
            .border(
                width = 1.dp,
                color = when {
                    feedback is FormulaResult.Failed -> AccentRed
                    edited -> PrimaryBlue
                    else -> BorderColor
                },
                shape = RoundedCornerShape(size = 6.dp),
            )
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(space = 3.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        ) {
            Text(
                text = "ƒ",
                style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.Bold),
                color = PrimaryBlue,
            )
            BasicTextField(
                value = draft,
                onValueChange = { value ->
                    draft = value
                    // Validate as typed, but only report a *failure* once the author stops being
                    // mid-word: `amount >` is not an error yet, it is an unfinished thought. So a live
                    // failure is held until they ask for it by pressing ⏎.
                    feedback = parse(value).takeIf { result -> result is FormulaResult.Parsed }
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.body2.copy(
                    color = TextPrimary,
                    fontFamily = FontFamily.Monospace,
                ),
                cursorBrush = SolidColor(value = PrimaryBlue),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { apply() }),
                modifier = Modifier.fillMaxWidth().weight(weight = 1f),
            )
            if (edited) {
                TinyButton(text = "apply ⏎", primary = true, onClick = { apply() })
                TinyButton(text = "revert", onClick = { draft = text })
            }
        }
        FormulaFeedback(feedback = feedback, edited = edited)
    }
}

/**
 * What the bar says under the box.
 *
 * A clean parse of an edited draft is worth confirming — it is the difference between "⏎ will do
 * something" and "⏎ will tell me I am wrong". An untouched row says nothing at all, because there is
 * nothing to report about text the author has not changed.
 */
@Suppress("FunctionNaming")
@Composable
private fun FormulaFeedback(feedback: FormulaResult?, edited: Boolean) {
    when {
        feedback is FormulaResult.Failed -> Text(
            text = "⚠ ${feedback.message}",
            style = MaterialTheme.typography.caption,
            color = AccentRed,
        )

        feedback is FormulaResult.Parsed && edited -> Text(
            text = "✓ reads cleanly — ⏎ to apply",
            style = MaterialTheme.typography.caption,
            color = AccentGreen,
        )

        else -> Unit
    }
}

/** The bar with nothing selected. Present rather than absent, so the layout does not jump. */
@Suppress("FunctionNaming")
@Composable
private fun EmptyFormulaBar(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape = RoundedCornerShape(size = 6.dp))
            .background(color = BgInput)
            .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(size = 6.dp))
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        Text(
            text = "ƒ",
            style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.Bold),
            color = TextSecondary,
        )
        Text(
            text = "Select a condition row to edit it as text",
            style = MaterialTheme.typography.caption,
            color = TextSecondary,
        )
    }
}
