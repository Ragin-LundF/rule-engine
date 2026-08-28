package ui.builder.outline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import ui.AccentRed
import ui.BgSurface
import ui.BorderColor
import ui.PrimaryBlue
import ui.PrimaryGlow
import ui.TextPrimary
import ui.TextSecondary
import ui.components.TinyButton

/**
 * The strip under the outline: the text this rule will become, and what is wrong with it.
 *
 * The Builder replaces the whole rule text on every edit, so what it is about to write is the one thing
 * an author most needs to be able to see without leaving the canvas. Switching to Code mode to check
 * shows it, but by then the edit is already on disk — and the round trip is what stops people checking.
 *
 * The selected row is highlighted here, which makes this the bridge between the two views: it is where
 * you learn which line of DSL the row you are editing corresponds to. That is how the outline teaches
 * the language rather than hiding it.
 *
 * Collapsed by default. It is reference material, not the work, and the canvas above it is the work.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
internal fun OutlineDock(
    dsl: String,
    selectedRowText: String?,
    diagnostics: List<String>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape = RoundedCornerShape(size = 8.dp))
            .background(color = BgSurface)
            .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(size = 8.dp)),
    ) {
        DockHeader(
            errorCount = diagnostics.size,
            expanded = expanded,
            onToggleExpanded = onToggleExpanded,
        )
        if (expanded) {
            DockBody(dsl = dsl, selectedRowText = selectedRowText, diagnostics = diagnostics)
        }
    }
}

/**
 * The always-visible line: what the dock holds, and how many problems.
 *
 * The count is on the header rather than inside the body on purpose — a diagnostic the author cannot
 * see until they expand a panel is a diagnostic that arrives after the mistake has been saved.
 */
@Suppress("FunctionNaming")
@Composable
private fun DockHeader(errorCount: Int, expanded: Boolean, onToggleExpanded: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        Text(
            text = "GENERATED DSL",
            style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
            color = TextSecondary,
        )
        if (errorCount > 0) {
            Text(
                text = if (errorCount == 1) "1 problem" else "$errorCount problems",
                style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
                color = AccentRed,
                modifier = Modifier
                    .clip(shape = RoundedCornerShape(percent = 50))
                    .background(color = AccentRed.copy(alpha = 0.14f))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().weight(weight = 1f),
            horizontalArrangement = Arrangement.End,
        ) {
            TinyButton(text = if (expanded) "hide" else "show", onClick = onToggleExpanded)
        }
    }
}

/** The text and the problems, once opened. */
@Suppress("FunctionNaming")
@Composable
private fun DockBody(dsl: String, selectedRowText: String?, diagnostics: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 220.dp)
            .verticalScroll(state = rememberScrollState())
            .padding(horizontal = 10.dp)
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(space = 6.dp),
    ) {
        Text(
            text = highlightRow(dsl = dsl, rowText = selectedRowText),
            style = MaterialTheme.typography.body2.copy(fontFamily = FontFamily.Monospace),
            color = TextPrimary,
            modifier = Modifier.horizontalScroll(state = rememberScrollState()),
        )
        diagnostics.forEach { message ->
            Text(
                text = "⚠ $message",
                style = MaterialTheme.typography.caption,
                color = AccentRed,
            )
        }
    }
}

/**
 * [dsl] with the line holding [rowText] given the selection background.
 *
 * Matches on the trimmed line so the generator's indentation and the `and` / `or` that joins the row to
 * the one above it do not have to be reproduced here. Two identical rows — legal, if pointless — both
 * highlight; picking one arbitrarily would be worse, because the highlight would then be a claim about
 * which row is selected that is wrong half the time.
 */
private fun highlightRow(dsl: String, rowText: String?): AnnotatedString {
    val needle = rowText?.trim()?.takeIf { text -> text.isNotEmpty() }
        ?: return AnnotatedString(text = dsl)

    return buildAnnotatedString {
        dsl.lines().forEachIndexed { index, line ->
            if (index > 0) {
                append("\n")
            }
            if (line.trim().endsWith(needle)) {
                withStyle(style = SpanStyle(background = PrimaryGlow, color = PrimaryBlue)) {
                    append(line)
                }
            } else {
                append(line)
            }
        }
    }
}
