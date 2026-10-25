package ui.workbench.workspace

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ui.TextPrimary
import ui.TextSecondary
import ui.components.PrimaryButton
import ui.components.SecondaryButton
import ui.components.SectionDivider
import ui.components.TinyButton
import ui.components.rotateVertically
import ui.tester.TestInputField
import ui.tester.model.TestInputState
import ui.workbench.model.workspace.ContextScope

/**
 * The context column: the data the rule is being tried against, what it does with it, and what can be
 * named at the current selection.
 *
 * It answers the questions the editor does not — "what will this do to a real record" and "what can I
 * write here" — so the author never has to leave the row to find out. It is not an editing surface: the
 * editor is under the row. The width is fixed by the shell; everything in here wraps or truncates
 * inside it.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
fun RuleContextPanel(
    ruleId: String,
    testInput: TestInputState,
    onJsonChange: (String) -> Unit,
    onRun: () -> Unit,
    onLoadJson: () -> Unit,
    scope: ContextScope,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!expanded) {
        CollapsedContext(onToggleExpanded = onToggleExpanded, modifier = modifier)
        return
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag(tag = WorkspaceLayout.CONTEXT_TAG)
            .verticalScroll(state = rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ContextTitle(text = "Test with a record", modifier = Modifier.weight(weight = 1f))
            TinyButton(text = "⟩", onClick = onToggleExpanded)
        }
        TestInputField(state = testInput, onJsonChange = onJsonChange, height = RECORD_HEIGHT)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(space = 8.dp, alignment = Alignment.End),
        ) {
            SecondaryButton(text = "Load JSON…", onClick = onLoadJson)
            PrimaryButton(
                text = if (testInput.isRunning) "Running…" else "Run",
                enabled = !testInput.isRunning && testInput.inputJson.isNotBlank(),
                onClick = onRun,
            )
        }

        SectionDivider()
        RuleOutcomeSection(ruleId = ruleId, outcome = testInput.outcome)

        SectionDivider()
        ScopeSection(scope = scope)

        SectionDivider()
        KeysSection()
    }
}

/** The heading every section of the column starts with. */
@Suppress("FunctionNaming")
@Composable
internal fun ContextTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.subtitle2.copy(fontWeight = FontWeight.SemiBold),
        color = TextPrimary,
        modifier = modifier,
    )
}

/** The folded column: a strip that opens it again. */
@Suppress("FunctionNaming")
@Composable
private fun CollapsedContext(onToggleExpanded: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag(tag = WorkspaceLayout.CONTEXT_TAG)
            .clickable(onClick = onToggleExpanded),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TinyButton(text = "⟨", onClick = onToggleExpanded, modifier = Modifier.padding(top = 6.dp))
        Text(
            text = "CONTEXT",
            style = MaterialTheme.typography.caption.copy(letterSpacing = 1.5.sp),
            color = TextSecondary,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(top = 16.dp).rotateVertically(),
        )
    }
}

/** Tall enough for a small record without pushing the outcome below the fold at 720 px. */
private val RECORD_HEIGHT = 140.dp
