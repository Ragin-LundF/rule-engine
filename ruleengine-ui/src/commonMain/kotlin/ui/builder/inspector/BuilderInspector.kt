package ui.builder.inspector

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ruleengine.core.domain.dto.RuleBranch
import ui.builder.BuilderToRuleDsl
import ui.builder.OperandText
import ui.builder.OperatorOptions
import ui.builder.RowForm
import ui.builder.model.BuilderFilter
import ui.builder.model.BuilderPathStep
import ui.builder.model.catalog.BuilderCatalog
import ui.builder.model.catalog.CatalogActionInfo
import ui.builder.model.mutable.BuilderEditorState
import ui.builder.model.selection.SelectionStep
import ui.builder.selection.ResolutionScope
import ui.builder.selection.SelectionResolver
import ui.builder.selection.SelectionTarget
import ui.components.SectionDivider

/**
 * The builder's single editing surface.
 *
 * Everything selected inside a rule is edited here — a row, either side of it, an argument, a term, a
 * path segment, a `where` filter, an action, an assignment, an `extract` clause. One panel, because the
 * alternative is what the builder used to do: a panel per level, opened underneath the row, one at a
 * time, with the row itself pushed off screen by the time you reached the fourth.
 *
 * Depth is navigation. [steps] says where in the row's operand tree the editor is pointed;
 * [onSelect] moves it. Nothing expands.
 *
 * Every edit calls [onDslChange] with freshly generated rule text, because the Builder's contract is
 * that the file is regenerated on every keystroke — see `BuilderToRuleDsl`.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
fun BuilderInspector(
    state: BuilderEditorState,
    anchor: InspectorAnchor,
    steps: List<SelectionStep>,
    fields: BuilderCatalog,
    actions: List<CatalogActionInfo>,
    onSelect: (List<SelectionStep>) -> Unit,
    onSelectNode: (String) -> Unit,
    onDslChange: (String) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val target = resolve(state = state, anchor = anchor, steps = steps, catalog = fields)

    fun emit() {
        // Re-derive the row's form first: an operand edit can have made a comparison expressible as a
        // simple condition, and the two must never disagree with the text.
        if (anchor is InspectorAnchor.Condition) {
            RowForm.normalizeRow(state = state, rowId = anchor.conditionId)
        }
        BuilderToRuleDsl.generate(state = state)?.let(onDslChange)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(state = rememberScrollState())
            .padding(all = 12.dp),
    ) {
        if (steps.isNotEmpty()) {
            SelectionTrail(
                anchorLabel = anchorLabel(state = state, anchor = anchor),
                steps = steps,
                labelAt = { index ->
                    stepLabel(
                        step = steps[index],
                        target = resolve(
                            state = state,
                            anchor = anchor,
                            steps = steps.take(n = index + 1),
                            catalog = fields,
                        ),
                    )
                },
                onNavigate = onSelect,
            )
            SectionDivider()
        }

        if (target == null) {
            InspectorNote(
                text = "That selection no longer exists — it was edited away. Pick a row to carry on.",
                warning = true,
            )
            return@Column
        }

        TargetEditor(
            target = target,
            state = state,
            fields = fields,
            actions = actions,
            steps = steps,
            onEdited = ::emit,
            onSelect = onSelect,
            onSelectNode = onSelectNode,
            onMessage = onMessage,
        )

        if (anchor is InspectorAnchor.Condition) {
            ExpressionOutline(
                state = state,
                anchorId = anchor.conditionId,
                steps = steps,
                onNavigate = onSelect,
            )
        }
    }
}

/**
 * Dispatches to the editor for whatever the selection resolved to.
 *
 * Split in two along the line the selection itself draws: a *row* target is anchored and mutable in
 * place, while a *leaf* target came out of the walk with a setter attached.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun TargetEditor(
    target: SelectionTarget,
    state: BuilderEditorState,
    fields: BuilderCatalog,
    actions: List<CatalogActionInfo>,
    steps: List<SelectionStep>,
    onEdited: () -> Unit,
    onSelect: (List<SelectionStep>) -> Unit,
    onSelectNode: (String) -> Unit,
    onMessage: (String) -> Unit,
) {
    val drill: (SelectionStep) -> Unit = { step -> onSelect(steps + step) }
    when (target) {
        is SelectionTarget.Condition -> ConditionEditor(
            condition = target.condition,
            state = state,
            fields = fields,
            onEdited = onEdited,
            onSelect = { next -> onSelect(steps + next) },
            onMessage = onMessage,
        )

        is SelectionTarget.Comparison -> ComparisonEditor(
            comparison = target.comparison,
            fields = fields,
            onEdited = onEdited,
            onSelect = { next -> onSelect(steps + next) },
        )

        is SelectionTarget.Group -> GroupEditor(
            group = target.group,
            state = state,
            onEdited = onEdited,
            onSelectNode = onSelectNode,
        )

        is SelectionTarget.Action -> ActionEditor(
            action = target.action,
            actions = actions,
            fields = fields,
            onEdited = onEdited,
            onDrill = drill,
        )

        is SelectionTarget.Assignment -> AssignmentEditor(
            assignment = target.assignment,
            fields = fields,
            onEdited = onEdited,
            onDrill = drill,
        )

        // A leaf carries the catalog its own names resolve against — the element's inside a `where` —
        // so it deliberately does not take the ambient `fields`.
        else -> LeafTargetEditor(
            target = target,
            steps = steps,
            onEdited = onEdited,
            onSelect = onSelect,
        )
    }
}

/** The targets the selection walk returns with a setter: an operand, a segment, a filter, an extract. */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun LeafTargetEditor(
    target: SelectionTarget,
    steps: List<SelectionStep>,
    onEdited: () -> Unit,
    onSelect: (List<SelectionStep>) -> Unit,
) {
    val drill: (SelectionStep) -> Unit = { step -> onSelect(steps + step) }
    when (target) {
        is SelectionTarget.Operand -> OperandEditor(
            operand = target.operand,
            fields = target.scope.catalog,
            write = target.write,
            onEdited = onEdited,
            onDrill = drill,
            // A path inside this operand starts at its own root, so the filter of its segment at
            // `depth` is two steps down from here.
            onDrillFilter = { depth, index ->
                onSelect(steps + SelectionStep.Segment(index = depth) + SelectionStep.Filter(index = index))
            },
        )

        is SelectionTarget.Segment -> SegmentEditor(
            segment = target.segment,
            write = target.write,
            scope = target.scope,
            onEdited = onEdited,
            // The selection already ends at this segment, so only the filter step is appended.
            onDrillFilter = { _, index -> onSelect(steps + SelectionStep.Filter(index = index)) },
        )

        is SelectionTarget.Filter -> FilterEditor(
            filter = target.filter,
            write = target.write,
            onEdited = onEdited,
            onDrill = drill,
        )

        is SelectionTarget.Extraction -> ExtractionEditor(
            extraction = target.extraction,
            fields = target.scope.catalog,
            write = target.write,
            onEdited = onEdited,
        )

        else -> InspectorNote(text = "Nothing to edit here.")
    }
}

/**
 * One path segment on its own, reached by drilling from a path.
 *
 * The same card the path list renders, so a segment looks the same whether it is read in place or
 * opened on its own.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun SegmentEditor(
    segment: BuilderPathStep,
    write: (BuilderPathStep) -> Unit,
    scope: ResolutionScope,
    onEdited: () -> Unit,
    onDrillFilter: (depth: Int, filterIndex: Int) -> Unit,
) {
    DslEcho(text = OperandText.pathToDsl(path = listOf(segment)))
    PathSteps(
        path = listOf(segment),
        fields = scope.catalog,
        onPathChanged = { updated ->
            updated.firstOrNull()?.let { first ->
                write(first)
                onEdited()
            }
        },
        onDrillFilter = onDrillFilter,
        // The segments in front of this one, so the card resolves at its real depth rather than
        // against the schema's top-level fields.
        prefix = scope.prefix,
        // `write` replaces this one step; an appended segment would be dropped on the way back.
        canAppend = false,
    )
}

/** One `where` restriction: a comparison one level down, so its sides are operands too. */
@Suppress("FunctionNaming")
@Composable
private fun FilterEditor(
    filter: BuilderFilter,
    write: (BuilderFilter) -> Unit,
    onEdited: () -> Unit,
    onDrill: (SelectionStep) -> Unit,
) {
    fun update(value: BuilderFilter) {
        write(value)
        onEdited()
    }

    DslEcho(text = "[ ${OperandText.filterToDsl(filter = filter)} ]")

    InspectorSection(title = "Left")
    OperandCard(
        operand = filter.left,
        onDrill = { onDrill(SelectionStep.Left) },
        label = "a member of the element — or a document field, which the element shadows",
    )
    InspectorField(label = "Operator") {
        InspectorOptions(
            options = OperatorOptions.FILTER_OPERATORS,
            selected = filter.operator,
            onSelect = { selected -> update(filter.copy(operator = selected)) },
        )
    }
    InspectorSection(title = "Right")
    OperandCard(
        operand = filter.right,
        onDrill = { onDrill(SelectionStep.Right) },
        label = if (filter.operator == OperatorOptions.IN) {
            "a written-out list, or the bare name of another field — a bare name stays a membership " +
                "test rather than becoming text"
        } else {
            null
        },
    )
    InspectorNote(
        text = "A filter has no ignoreCase: normalize the member in the schema instead.",
    )
}

/** What the selection is anchored on — a row, or a statement in one branch. */
sealed interface InspectorAnchor {
    /** A condition row, by id. */
    data class Condition(val conditionId: String) : InspectorAnchor

    /** An action or assignment row of one branch. */
    data class Statement(val branch: RuleBranch, val statementId: String) : InspectorAnchor
}

private fun resolve(
    state: BuilderEditorState,
    anchor: InspectorAnchor,
    steps: List<SelectionStep>,
    catalog: BuilderCatalog = BuilderCatalog.Empty,
): SelectionTarget? {
    return when (anchor) {
        is InspectorAnchor.Condition -> SelectionResolver.resolveCondition(
            state = state,
            conditionId = anchor.conditionId,
            steps = steps,
            catalog = catalog,
        )

        is InspectorAnchor.Statement -> SelectionResolver.resolveStatement(
            state = state,
            branch = anchor.branch,
            statementId = anchor.statementId,
            steps = steps,
            catalog = catalog,
        )
    }
}

private fun anchorLabel(state: BuilderEditorState, anchor: InspectorAnchor): String {
    val target = resolve(state = state, anchor = anchor, steps = emptyList())
    return when (target) {
        is SelectionTarget.Condition -> target.condition.field.ifBlank { "condition" }
        is SelectionTarget.Comparison -> "comparison"
        is SelectionTarget.Group -> "( group )"
        is SelectionTarget.Action -> target.action.name.ifBlank { "action" }
        is SelectionTarget.Assignment -> target.assignment.name.ifBlank { "assignment" }
        else -> "row"
    }
}
