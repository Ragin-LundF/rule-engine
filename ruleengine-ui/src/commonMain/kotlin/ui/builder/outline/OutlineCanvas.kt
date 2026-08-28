package ui.builder.outline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ruleengine.core.domain.dto.RuleBranch
import ui.AccentOrange
import ui.BgElevated
import ui.BorderColor
import ui.PrimaryBlue
import ui.PrimaryGlow
import ui.TextPrimary
import ui.TextSecondary
import ui.builder.BuilderToRuleDsl
import ui.builder.OperatorOptions
import ui.builder.components.row.PlainTextField
import ui.builder.model.BuilderLockKind
import ui.builder.model.BuilderOperand
import ui.builder.model.catalog.CatalogActionInfo
import ui.builder.model.mutable.BuilderEditorState
import ui.builder.model.selection.SelectionStep
import ui.builder.selection.SelectionResolver
import ui.components.SectionDivider
import ui.components.TinyButton

/**
 * The builder's reading layout: one rule as a continuous outline.
 *
 * What this replaces was a vertical stack of bordered cards — DESCRIPTION, WHEN, THEN, and one more for
 * each optional branch — inside a scroll view. A rule with all three branches was five cards, so WHEN
 * scrolled out of sight while its outcome was being edited and the three outcomes could never be
 * compared with each other.
 *
 * Here the whole rule reads top to bottom in one flow, at one line per row. Editing happens in the
 * Inspector, so nothing in this canvas expands and the row being worked on never moves.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
fun OutlineCanvas(
    state: BuilderEditorState,
    catalogActions: List<CatalogActionInfo>,
    selectedNodeId: String?,
    selectedStatementId: String?,
    selectedSteps: List<SelectionStep>?,
    onSelectNode: (String, List<SelectionStep>) -> Unit,
    onSelectStatement: (RuleBranch, String) -> Unit,
    onDslChange: (String) -> Unit,
    onMessage: (String) -> Unit,
    onRenameRule: (oldId: String, newId: String) -> Unit = { _, _ -> },
    /** Problems with the rule as it stands, already filtered to this rule by the caller. */
    diagnostics: List<String> = emptyList(),
    modifier: Modifier = Modifier,
) {
    // Which rows are picked for grouping. Held here rather than in the selection: it is a transient
    // gesture, not a thing the Inspector describes, and it is cleared by every structural edit.
    val picked = remember(state) { mutableStateListOf<String>() }

    fun emit() {
        BuilderToRuleDsl.generate(state = state)?.let(onDslChange)
    }

    // The dock is closed until asked for, and remembers that per rule: an author who opens it is
    // usually checking one specific rule's text, not turning it on for the session.
    var dockExpanded by remember(state) { mutableStateOf(value = false) }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(weight = 1f)
                .verticalScroll(state = rememberScrollState())
                .padding(all = 16.dp),
        ) {
            OutlineBody(
                state = state,
                catalogActions = catalogActions,
                picked = picked,
                selectedNodeId = selectedNodeId,
                selectedStatementId = selectedStatementId,
                selectedSteps = selectedSteps,
                onSelectNode = onSelectNode,
                onSelectStatement = onSelectStatement,
                onRenameRule = onRenameRule,
                onEdited = ::emit,
                onMessage = onMessage,
            )
        }

        // Outside the scroll on purpose: a panel that says what is wrong is no use if it scrolls away
        // from the row that is wrong.
        OutlineDock(
            dsl = BuilderToRuleDsl.generate(state = state).orEmpty(),
            selectedRowText = selectedNodeId
                ?.let { id -> SelectionResolver.findNode(nodes = state.conditionNodes, id = id) }
                ?.let { node -> BuilderToRuleDsl.renderRow(node = node) },
            diagnostics = diagnostics,
            expanded = dockExpanded,
            onToggleExpanded = { dockExpanded = !dockExpanded },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        )
    }
}

/**
 * The rule itself, top to bottom: header, description, conditions, then each outcome block.
 *
 * Split from [OutlineCanvas] so that function is just the frame — the scrolling half, the dock, and
 * what the dock is told. This half is the part that changes when the DSL grows a clause.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun OutlineBody(
    state: BuilderEditorState,
    catalogActions: List<CatalogActionInfo>,
    picked: MutableList<String>,
    selectedNodeId: String?,
    selectedStatementId: String?,
    selectedSteps: List<SelectionStep>?,
    onSelectNode: (String, List<SelectionStep>) -> Unit,
    onSelectStatement: (RuleBranch, String) -> Unit,
    onRenameRule: (oldId: String, newId: String) -> Unit,
    onEdited: () -> Unit,
    onMessage: (String) -> Unit,
) {
    RuleHeader(state = state, onRenameRule = onRenameRule)

    if (state.isLocked) {
        LockedNote(kind = state.lockKind, reason = state.lockReason)
        return
    }

    DescriptionField(state = state, onEdited = onEdited)

    WhenSection(
        state = state,
        picked = picked,
        selectedNodeId = selectedNodeId,
        selectedSteps = selectedSteps,
        onSelectNode = onSelectNode,
        onEdited = {
            picked.clear()
            onEdited()
        },
        onTogglePick = { id ->
            if (id in picked) picked.remove(id) else picked.add(id)
        },
        onMessage = onMessage,
    )

    // One divider before each section that exists, so the parts of a rule read as parts. `when` and the
    // three outcomes answer different questions, and at the 10dp that used to separate them a rule with
    // all four was one unbroken column of rows.
    val optional = listOf(RuleBranch.ELSE, RuleBranch.NOT_EXISTS)

    SectionDivider()

    BranchOutline(
        state = state,
        branch = RuleBranch.THEN,
        catalogActions = catalogActions,
        selectedStatementId = selectedStatementId,
        onSelectStatement = onSelectStatement,
        onEdited = onEdited,
        onMessage = onMessage,
    )

    optional.filter { branch -> state.hasBranch(branch = branch) }.forEach { branch ->
        SectionDivider()
        BranchOutline(
            state = state,
            branch = branch,
            catalogActions = catalogActions,
            selectedStatementId = selectedStatementId,
            onSelectStatement = onSelectStatement,
            onEdited = onEdited,
            onMessage = onMessage,
        )
    }

    // The branches this rule does not have go under **one** divider, at the foot, rather than getting a
    // section break each. A branch that does not exist yet is not a section — it is an offer to create
    // one — and giving each offer its own break made the empty half of a rule heavier on the page than
    // the half carrying its logic.
    val missing = optional.filterNot { branch -> state.hasBranch(branch = branch) }
    if (missing.isNotEmpty()) {
        SectionDivider()
        AddBranchRows(
            branches = missing,
            catalogActions = catalogActions,
            state = state,
            onEdited = onEdited,
        )
    }
}

/**
 * The rule's id and status — and the id is where it is renamed.
 *
 * Rename is here rather than behind a button because the id is already on screen and a rule's id is
 * the one part of it that reads as a title. The old view spent a `✎ Rename` button and a second
 * text field on the same edit. Committing on Enter or on losing focus is what makes that safe: a
 * half-typed id would otherwise be written to the manifest on every keystroke.
 */
@Suppress("FunctionNaming")
@Composable
private fun RuleHeader(state: BuilderEditorState, onRenameRule: (oldId: String, newId: String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        if (state.ruleId.isBlank()) {
            Text(text = "no rule selected", style = MaterialTheme.typography.h6, color = TextSecondary)
        } else {
            RuleIdField(ruleId = state.ruleId, onRenameRule = onRenameRule)
        }
        if (state.isLocked && state.lockKind != BuilderLockKind.NO_RULE_SELECTED) {
            Text(
                text = "code only",
                style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
                color = AccentOrange,
            )
        }
    }
}

/**
 * The editable id.
 *
 * The draft is local and keyed on [ruleId], so an edit elsewhere that changes the rule under the
 * cursor replaces the draft rather than fighting it. A blank or unchanged id commits nothing —
 * `set  = …` and a duplicate id are both rules the manifest cannot hold.
 */
@Suppress("FunctionNaming")
@Composable
private fun RuleIdField(ruleId: String, onRenameRule: (oldId: String, newId: String) -> Unit) {
    var draft by remember(key1 = ruleId) { mutableStateOf(value = ruleId) }

    fun commit() {
        val trimmed = draft.trim()
        if (trimmed.isNotBlank() && trimmed != ruleId) {
            onRenameRule(ruleId, trimmed)
        } else {
            draft = ruleId
        }
    }

    BasicTextField(
        value = draft,
        onValueChange = { text -> draft = text },
        singleLine = true,
        textStyle = MaterialTheme.typography.h6.copy(color = TextPrimary),
        cursorBrush = SolidColor(value = PrimaryBlue),
        keyboardActions = KeyboardActions(onDone = { commit() }),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        modifier = Modifier
            .widthIn(min = 80.dp)
            .onFocusChanged { focus -> if (!focus.isFocused) commit() },
    )
}

/**
 * The `description` clause.
 *
 * Sits above the conditions because that is where it belongs in the generated text, and because someone
 * arriving at a rule should read what it is for before reading how it decides.
 */
@Suppress("FunctionNaming")
@Composable
private fun DescriptionField(state: BuilderEditorState, onEdited: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        PlainTextField(
            value = state.description,
            placeholder = "What is this rule for? — the one clause written for a human",
            onValueChange = { text ->
                state.description = text
                onEdited()
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The condition block: the grouping bar when rows are picked, then the rows. */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun WhenSection(
    state: BuilderEditorState,
    picked: MutableList<String>,
    selectedNodeId: String?,
    selectedSteps: List<SelectionStep>?,
    onSelectNode: (String, List<SelectionStep>) -> Unit,
    onEdited: () -> Unit,
    onTogglePick: (String) -> Unit,
    onMessage: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        Text(
            text = "WHEN",
            style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
            color = PrimaryBlue,
        )
        Text(
            text = "all of these, in order — AND binds tighter than OR",
            style = MaterialTheme.typography.caption,
            color = TextSecondary,
        )
    }

    if (picked.size >= 1) {
        GroupingBar(
            count = picked.size,
            onGroup = {
                state.groupConditions(ids = picked.toSet())
                onEdited()
            },
            onClear = { picked.clear() },
        )
    }

    OutlineRows(
        nodes = state.conditionNodes,
        state = state,
        depth = 0,
        selectedNodeId = selectedNodeId,
        selectedSteps = selectedSteps,
        picked = picked,
        onSelectNode = onSelectNode,
        onTogglePick = onTogglePick,
        onEdited = onEdited,
        onMessage = onMessage,
    )

    if (state.conditionNodes.isEmpty()) {
        Text(
            text = "(no conditions yet)",
            style = MaterialTheme.typography.caption,
            color = TextSecondary,
            modifier = Modifier.padding(start = 22.dp),
        )
    }

    WhenAddRow(state = state, onEdited = onEdited)
}

/**
 * The grouping bar, shown while rows are picked.
 *
 * Replaces a checkbox in every row: the column stood there permanently for the one gesture that needed
 * it, and it competed with the expression for the reader's attention. The pick is on the selected row's
 * own `⊕`, and this bar is the confirmation.
 */
@Suppress("FunctionNaming")
@Composable
private fun GroupingBar(count: Int, onGroup: () -> Unit, onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(start = 22.dp, top = 2.dp, bottom = 4.dp)
            .clip(shape = RoundedCornerShape(percent = 50))
            .background(color = PrimaryGlow)
            .border(
                width = 1.dp,
                color = PrimaryBlue.copy(alpha = 0.4f),
                shape = RoundedCornerShape(percent = 50),
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        Text(
            text = if (count == 1) "1 row picked" else "$count rows picked",
            style = MaterialTheme.typography.caption,
            color = PrimaryBlue,
        )
        TinyButton(text = "Wrap in ( )", primary = true, onClick = onGroup)
        TinyButton(text = "clear", onClick = onClear)
    }
}

/** The three ways to add to a `when` block. */
@Suppress("FunctionNaming")
@Composable
private fun WhenAddRow(state: BuilderEditorState, onEdited: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 22.dp, top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 6.dp),
    ) {
        TinyButton(
            text = "+ condition",
            onClick = {
                state.addCondition()
                onEdited()
            },
        )
        TinyButton(
            text = "+ computed",
            onClick = {
                state.addComparison(
                    left = BuilderOperand.Aggregate(function = "count", path = emptyList()),
                    operator = OperatorOptions.COMPARISON_NUMERIC.first(),
                    right = BuilderOperand.Literal(text = "0", numeric = true),
                )
                onEdited()
            },
        )
    }
}

/** A rule the Builder will not rewrite, with the reason and the text it holds. */
@Suppress("FunctionNaming")
@Composable
private fun LockedNote(kind: BuilderLockKind, reason: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape = RoundedCornerShape(size = 8.dp))
            .background(color = BgElevated)
            .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(size = 8.dp))
            .padding(all = 14.dp),
        verticalArrangement = Arrangement.spacedBy(space = 6.dp),
    ) {
        Text(
            text = when (kind) {
                BuilderLockKind.NO_RULE_SELECTED -> "Select a rule to edit it here."
                else -> "⚠ This rule can only be edited in the code view"
            },
            style = MaterialTheme.typography.subtitle1,
            color = TextPrimary,
        )
        if (kind != BuilderLockKind.NO_RULE_SELECTED) {
            Text(text = reason, style = MaterialTheme.typography.body2, color = TextSecondary)
            Text(
                text = "Switch to Code mode to edit it. Nothing here will rewrite it.",
                style = MaterialTheme.typography.caption,
                color = TextSecondary,
            )
        }
    }
}
