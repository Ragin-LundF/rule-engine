package ui.builder.inspector

import ruleengine.compiler.Validator
import ruleengine.core.domain.dto.OperatorId
import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.dsl.parser.Parser
import ui.builder.BuilderToRuleDsl
import ui.builder.OperandRules
import ui.builder.RowForm
import ui.builder.model.BuilderConditionNode
import ui.builder.model.BuilderOperand
import ui.builder.model.BuilderPathStep
import ui.builder.model.BuilderRule
import ui.builder.model.BuilderTerm
import ui.builder.model.catalog.BuilderCatalog
import ui.builder.model.catalog.CatalogFieldInfo
import ui.builder.model.mutable.BuilderEditorState
import ui.builder.model.mutable.MutableConditionNode
import ui.builder.model.selection.SelectionStep
import ui.builder.selection.SelectionResolver
import ui.builder.selection.SelectionTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What an inspector edit does to the rule file.
 *
 * The inspector is now the only editing surface, and the Builder regenerates the whole rule text on
 * every edit — so the question that matters is not "did the model change" but "is the text the engine
 * would load still the text the author meant". Every case here therefore ends at
 * `BuilderToRuleDsl.generate`, and most of them push the result back through the real `Parser` and
 * `Validator`.
 *
 * These exercise the same code paths the composables call; they do not render Compose. The editors are
 * thin over `SelectionResolver`, `RowForm` and `OperandRules`, which is where the behaviour lives.
 */
class InspectorEditRoundTripTest {

    // ── schema fixture ────────────────────────────────────────────────────────

    private val schema = FieldSchema(
        name = "test",
        fields = mapOf(
            FieldId("amount") to FieldDefinition(
                id = FieldId("amount"),
                type = FieldType.DECIMAL,
                operators = setOf(OperatorId("gte"), OperatorId("gt"), OperatorId("equals")),
            ),
            FieldId("purpose") to FieldDefinition(
                id = FieldId("purpose"),
                type = FieldType.TEXT,
                operators = setOf(OperatorId("contains"), OperatorId("equals")),
            ),
            FieldId("invoices") to FieldDefinition(
                id = FieldId("invoices"),
                type = FieldType.COLLECTION,
                fields = mapOf(
                    FieldId("amount") to FieldDefinition(
                        id = FieldId("amount"),
                        type = FieldType.DECIMAL,
                        operators = setOf(OperatorId("gt")),
                    ),
                    FieldId("status") to FieldDefinition(
                        id = FieldId("status"),
                        type = FieldType.TEXT,
                        operators = setOf(OperatorId("equals")),
                    ),
                ),
            ),
            FieldId("payments") to FieldDefinition(
                id = FieldId("payments"),
                type = FieldType.COLLECTION,
                fields = mapOf(
                    FieldId("amount") to FieldDefinition(
                        id = FieldId("amount"),
                        type = FieldType.DECIMAL,
                        operators = setOf(OperatorId("gt")),
                    ),
                ),
            ),
        ),
    )

    private val catalog: BuilderCatalog = BuilderCatalog.of(fields = listOf(
        CatalogFieldInfo(id = "amount", type = "decimal"),
        CatalogFieldInfo(id = "purpose", type = "text"),
        CatalogFieldInfo(
            id = "invoices",
            type = "collection",
            nestedFields = listOf(
                CatalogFieldInfo(id = "amount", type = "decimal"),
                CatalogFieldInfo(id = "status", type = "text"),
            ),
        ),
        CatalogFieldInfo(
            id = "payments",
            type = "collection",
            nestedFields = listOf(CatalogFieldInfo(id = "amount", type = "decimal")),
        ),
    ))

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun whenText(state: BuilderEditorState): String {
        val dsl = BuilderToRuleDsl.generate(state = state)
        assertNotNull(actual = dsl, message = "the rule must generate DSL")
        return dsl.substringAfter(delimiter = "when").substringBefore(delimiter = "then").trim()
    }

    /** Parses and validates the generated rule, which is the only claim that really counts. */
    private fun assertEngineAccepts(state: BuilderEditorState) {
        val dsl = BuilderToRuleDsl.generate(state = state)
        assertNotNull(actual = dsl)
        val parsed = Parser(input = dsl).parseRules()
        val result = Validator.validate(asts = parsed, schema = schema, actions = null)
        assertTrue(
            actual = result.isValid,
            message = "the engine rejected the generated rule:\n$dsl\n${result.diagnostics}",
        )
    }

    /** `abs(sum(invoices.amount) - sum(payments.amount)) > 1000`, the four-level fixture. */
    private fun deepState(): BuilderEditorState {
        val aggregate = { collection: String ->
            BuilderOperand.Aggregate(
                function = "sum",
                path = listOf(BuilderPathStep(name = collection), BuilderPathStep(name = "amount")),
            )
        }
        return BuilderEditorState.fromBuilderRule(
            rule = BuilderRule.Supported(
                id = "balance-drift",
                conditionNodes = listOf(
                    BuilderConditionNode.Comparison(
                        nodeId = "cmp-1",
                        left = BuilderOperand.Call(
                            function = "abs",
                            args = listOf(
                                BuilderOperand.Calc(
                                    terms = listOf(
                                        BuilderTerm(operator = "", operand = aggregate("invoices")),
                                        BuilderTerm(operator = "-", operand = aggregate("payments")),
                                    ),
                                ),
                            ),
                        ),
                        operator = ">",
                        right = BuilderOperand.Literal(text = "1000", numeric = true),
                    ),
                ),
                actions = listOf(
                    ui.builder.model.BuilderAction(id = "act-1", name = "review", arguments = listOf("\"drift\"")),
                ),
            ),
        )
    }

    private fun operandAt(state: BuilderEditorState, steps: List<SelectionStep>): SelectionTarget.Operand {
        val target = SelectionResolver.resolveCondition(
            state = state,
            conditionId = "cmp-1",
            steps = steps,
        )
        return assertIs(value = target)
    }

    // ── editing at depth reaches the file ─────────────────────────────────────

    @Test
    fun `changing a reduction four levels down regenerates the whole rule`() {
        val state = deepState()
        val operand = operandAt(
            state = state,
            steps = listOf(SelectionStep.Left, SelectionStep.Argument(index = 0), SelectionStep.Term(index = 1)),
        )
        val aggregate = assertIs<BuilderOperand.Aggregate>(value = operand.operand)

        operand.write(aggregate.copy(function = "avg"))

        assertEquals(
            expected = "abs(sum(invoices.amount) - avg(payments.amount)) > 1000",
            actual = whenText(state = state),
        )
        assertEngineAccepts(state = state)
    }

    @Test
    fun `switching a side's kind is reversible through the operand it carried`() {
        val state = deepState()
        val left = operandAt(state = state, steps = listOf(SelectionStep.Left))

        // Field, then back to Function: the path inside must survive the trip.
        val asField = OperandRules.defaultOperand(
            kind = OperandRules.OperandKind.FIELD,
            fields = catalog,
            previous = left.operand,
        )
        left.write(asField)
        assertEquals(expected = "invoices.amount > 1000", actual = whenText(state = state))

        val backToCall = OperandRules.defaultOperand(
            kind = OperandRules.OperandKind.FUNCTION,
            fields = catalog,
            previous = operandAt(state = state, steps = listOf(SelectionStep.Left)).operand,
        )
        operandAt(state = state, steps = listOf(SelectionStep.Left)).write(backToCall)
        assertEquals(expected = "abs(invoices.amount) > 1000", actual = whenText(state = state))
    }

    @Test
    fun `adding a where filter to a segment reaches the generated path`() {
        val state = BuilderEditorState.fromBuilderRule(
            rule = BuilderRule.Supported(
                id = "paid-total",
                conditionNodes = listOf(
                    BuilderConditionNode.Comparison(
                        nodeId = "cmp-1",
                        left = BuilderOperand.Aggregate(
                            function = "sum",
                            path = listOf(
                                BuilderPathStep(name = "invoices"),
                                BuilderPathStep(name = "amount"),
                            ),
                        ),
                        operator = ">",
                        right = BuilderOperand.Literal(text = "100", numeric = true),
                    ),
                ),
                actions = listOf(
                    ui.builder.model.BuilderAction(id = "act-1", name = "review", arguments = listOf("\"x\"")),
                ),
            ),
        )
        val segmentTarget = SelectionResolver.resolveCondition(
            state = state,
            conditionId = "cmp-1",
            steps = listOf(SelectionStep.Left, SelectionStep.Segment(index = 0)),
        )
        val segment = assertIs<SelectionTarget.Segment>(value = segmentTarget)

        segment.write(
            segment.segment.copy(
                decorations = listOf(
                    ui.builder.model.BuilderPathDecoration.Filter(
                        filter = ui.builder.model.filter(
                            field = "status",
                            operator = "==",
                            value = "paid",
                        ),
                    ),
                ),
            ),
        )

        assertEquals(
            expected = """sum(invoices[status == "paid"].amount) > 100""",
            actual = whenText(state = state),
        )
        assertEngineAccepts(state = state)
    }

    // ── the derived form, as the inspector drives it ──────────────────────────

    @Test
    fun `making both sides plain again demotes the row to a simple condition`() {
        val state = deepState()
        operandAt(state = state, steps = listOf(SelectionStep.Left)).write(
            ui.builder.model.pathOperand(dotted = "amount"),
        )
        // What the inspector calls after any operand edit.
        val changed = RowForm.normalizeRow(state = state, rowId = "cmp-1")

        assertTrue(actual = changed, message = "a plain field against a literal must demote")
        assertIs<MutableConditionNode.Leaf>(value = state.conditionNodes.first())
        assertEquals(expected = "amount > 1000", actual = whenText(state = state))
        assertEngineAccepts(state = state)
    }

    @Test
    fun `a computed side keeps the row a comparison`() {
        val state = deepState()
        assertTrue(actual = !RowForm.normalizeRow(state = state, rowId = "cmp-1"))
        assertIs<MutableConditionNode.ComparisonLeaf>(value = state.conditionNodes.first())
    }

    @Test
    fun `promoting a named-only operator is refused and the rule is untouched`() {
        val state = BuilderEditorState.fromBuilderRule(
            rule = BuilderRule.Supported(
                id = "rent",
                conditionNodes = listOf(
                    BuilderConditionNode.Condition(
                        nodeId = "cond-1",
                        field = "purpose",
                        operator = "contains",
                        value = "rent",
                        ignoreCase = true,
                    ),
                ),
                actions = listOf(
                    ui.builder.model.BuilderAction(id = "act-1", name = "label", arguments = listOf("\"r\"")),
                ),
            ),
        )
        val before = whenText(state = state)
        val leaf = assertIs<MutableConditionNode.Leaf>(value = state.conditionNodes.first())

        val reason = RowForm.blockedPromotion(condition = leaf.inner)

        assertNotNull(actual = reason, message = "contains has no value-expression form")
        assertTrue(actual = reason.contains(other = "contains"))
        assertEquals(expected = before, actual = whenText(state = state))
        assertEngineAccepts(state = state)
    }

    // ── the guards, as the canvases consult them ──────────────────────────────

    @Test
    fun `the last condition cannot be removed`() {
        val state = deepState()
        val reason = state.blockedRemoval(id = "cmp-1")
        assertNotNull(actual = reason)
        assertTrue(actual = reason.contains(other = "at least one condition"), message = reason)
    }

    @Test
    fun `the last then outcome cannot be removed`() {
        val state = deepState()
        val reason = state.blockedRemoval(id = "act-1")
        assertNotNull(actual = reason)
        assertTrue(actual = reason.contains(other = "at least one outcome"), message = reason)
    }

    @Test
    fun `a second outcome makes the first removable`() {
        val state = deepState()
        state.addAction(defaultName = "flag", defaultArgCount = 1)
        assertEquals(expected = null, actual = state.blockedRemoval(id = "act-1"))
    }
}
