package ui.builder

import ui.builder.model.BuilderConditionNode
import ui.builder.model.BuilderOperand
import ui.builder.model.BuilderPathStep
import ui.builder.model.BuilderRule
import ui.builder.model.mutable.BuilderEditorState
import ui.builder.model.mutable.MutableBuilderCondition
import ui.builder.model.mutable.MutableConditionNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The derived row form, and the promote/demote round trip.
 *
 * This is the regression suite for a real defect: the button this replaced turned
 * `purpose contains "rent" ignoreCase` into `purpose >= "rent"` — it hard-coded the operator and
 * dropped the list, the second bound and `ignoreCase`. Since the Builder regenerates the whole rule
 * text on every edit, that was data loss in the file. Every case below therefore asserts the
 * *generated DSL*, not the model fields, because the file is what the author loses.
 */
class RowFormTest {

    // ── fixtures ──────────────────────────────────────────────────────────────

    private fun condition(
        field: String,
        operator: String,
        value: String = "",
        valueTo: String = "",
        listItems: List<String> = emptyList(),
        ignoreCase: Boolean = false,
        negated: Boolean = false,
    ): MutableBuilderCondition {
        return MutableBuilderCondition(
            id = "cond-1",
            field = field,
            operator = operator,
            value = value,
            valueTo = valueTo,
            listItems = listItems,
            ignoreCase = ignoreCase,
            negated = negated,
        )
    }

    private fun stateWith(node: BuilderConditionNode): BuilderEditorState {
        return BuilderEditorState.fromBuilderRule(
            rule = BuilderRule.Supported(
                id = "r",
                conditionNodes = listOf(node),
                actions = emptyList(),
            ),
        )
    }

    /** The generated `when` text of a single-row rule, which is what a mistake would corrupt. */
    private fun whenText(state: BuilderEditorState): String {
        val dsl = BuilderToRuleDsl.generate(state = state)
        assertNotNull(actual = dsl, message = "the row must generate DSL")
        return dsl.substringAfter(delimiter = "when").substringBefore(delimiter = "then").trim()
    }

    // ── what may be promoted, and what may not ────────────────────────────────

    @Test
    fun `a symbolic operator promotes`() {
        val subject = condition(field = "amount", operator = ">=", value = "300")
        assertNull(actual = RowForm.blockedPromotion(condition = subject))
    }

    @Test
    fun `equals promotes, because == means the same thing`() {
        val subject = condition(field = "country", operator = "equals", value = "de")
        assertNull(actual = RowForm.blockedPromotion(condition = subject))
    }

    @Test
    fun `every named-only operator is refused, with a reason naming it`() {
        val cases = mapOf(
            "contains" to condition(field = "purpose", operator = "contains", value = "rent"),
            "startsWith" to condition(field = "iban", operator = "startsWith", value = "DE"),
            "endsWith" to condition(field = "name", operator = "endsWith", value = "GmbH"),
            "regex" to condition(field = "iban", operator = "regex", value = "^DE"),
            "in" to condition(field = "country", operator = "in", listItems = listOf("de", "at")),
            "between" to condition(field = "amount", operator = "between", value = "1", valueTo = "9"),
            "containsAny" to condition(field = "tags", operator = "containsAny", listItems = listOf("vip")),
            "containsAll" to condition(field = "tags", operator = "containsAll", listItems = listOf("vip")),
        )
        cases.forEach { (operator, subject) ->
            val reason = RowForm.blockedPromotion(condition = subject)
            assertNotNull(actual = reason, message = "$operator must be refused")
            assertTrue(
                actual = reason.contains(other = operator),
                message = "the reason must name the operator, was: $reason",
            )
        }
    }

    @Test
    fun `a refused operator names the data that would be lost`() {
        val list = RowForm.blockedPromotion(
            condition = condition(field = "country", operator = "in", listItems = listOf("de", "at")),
        )
        assertNotNull(actual = list)
        assertTrue(actual = list.contains(other = "list of values"), message = list)

        val between = RowForm.blockedPromotion(
            condition = condition(field = "amount", operator = "between", value = "1", valueTo = "9"),
        )
        assertNotNull(actual = between)
        assertTrue(actual = between.contains(other = "second bound"), message = between)
    }

    // ── the round trip ────────────────────────────────────────────────────────

    @Test
    fun `promote then demote keeps a numeric row byte-identical`() {
        val state = stateWith(
            node = BuilderConditionNode.Condition(
                nodeId = "cond-1",
                field = "amount",
                operator = ">=",
                value = "300",
            ),
        )
        val before = whenText(state = state)

        val leaf = assertIs<MutableConditionNode.Leaf>(value = state.conditionNodes.first())
        val promoted = RowForm.toComparison(condition = leaf.inner)
        state.replaceNode(id = "cond-1", replacement = MutableConditionNode.ComparisonLeaf(inner = promoted))
        assertEquals(
            expected = before,
            actual = whenText(state = state),
            message = "promotion must not change the text",
        )

        assertTrue(actual = RowForm.normalizeRow(state = state, rowId = "cond-1"))
        assertEquals(expected = before, actual = whenText(state = state))
        assertIs<MutableConditionNode.Leaf>(value = state.conditionNodes.first())
    }

    @Test
    fun `promote then demote preserves ignoreCase and not`() {
        val state = stateWith(
            node = BuilderConditionNode.Condition(
                nodeId = "cond-1",
                field = "customer.tier",
                operator = "equals",
                value = "gold",
                ignoreCase = true,
                negated = true,
            ),
        )
        val leaf = assertIs<MutableConditionNode.Leaf>(value = state.conditionNodes.first())
        val promoted = RowForm.toComparison(condition = leaf.inner)
        state.replaceNode(id = "cond-1", replacement = MutableConditionNode.ComparisonLeaf(inner = promoted))

        // `equals` becomes `==` — the same meaning, the only operator that converts silently.
        val expected = """not customer.tier == "gold" ignoreCase"""
        assertEquals(expected = expected, actual = whenText(state = state))

        RowForm.normalizeRow(state = state, rowId = "cond-1")
        assertEquals(expected = expected, actual = whenText(state = state))
    }

    @Test
    fun `a dotted field survives promotion as separate path segments`() {
        val promoted = RowForm.toComparison(
            condition = condition(field = "existingLoans.lender.hub", operator = "==", value = "HAM"),
        )
        val left = assertIs<BuilderOperand.FieldRef>(value = promoted.left)
        assertEquals(
            expected = listOf("existingLoans", "lender", "hub"),
            actual = left.path.map { step -> step.name },
        )
    }

    @Test
    fun `a list right side survives promotion so the values are still there`() {
        val promoted = RowForm.toComparison(
            condition = condition(field = "country", operator = "==", listItems = listOf("de", "at", "ch")),
        )
        val right = assertIs<BuilderOperand.ListLiteral>(value = promoted.right)
        assertEquals(expected = listOf("de", "at", "ch"), actual = right.items)
    }

    // ── demotion, and when it must not happen ─────────────────────────────────

    @Test
    fun `a computed left side is never demoted`() {
        val state = stateWith(
            node = BuilderConditionNode.Comparison(
                nodeId = "cmp-1",
                left = BuilderOperand.Aggregate(
                    function = "count",
                    path = listOf(BuilderPathStep(name = "invoices")),
                ),
                operator = ">",
                right = BuilderOperand.Literal(text = "2", numeric = true),
            ),
        )
        assertFalse(actual = RowForm.normalizeRow(state = state, rowId = "cmp-1"))
        assertIs<MutableConditionNode.ComparisonLeaf>(value = state.conditionNodes.first())
        assertEquals(expected = "count(invoices) > 2", actual = whenText(state = state))
    }

    @Test
    fun `a filtered path is not a plain field, so it stays a comparison`() {
        val state = stateWith(
            node = BuilderConditionNode.Comparison(
                nodeId = "cmp-1",
                left = BuilderOperand.FieldRef(
                    path = listOf(
                        BuilderPathStep(
                            name = "invoices",
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
                    ),
                ),
                operator = ">",
                right = BuilderOperand.Literal(text = "0", numeric = true),
            ),
        )
        assertFalse(actual = RowForm.normalizeRow(state = state, rowId = "cmp-1"))
    }

    @Test
    fun `a variable is not a plain field, so it stays a comparison`() {
        val state = stateWith(
            node = BuilderConditionNode.Comparison(
                nodeId = "cmp-1",
                left = ui.builder.model.pathOperand(dotted = "\$debtToIncomeRatio"),
                operator = ">",
                right = BuilderOperand.Literal(text = "0.4", numeric = true),
            ),
        )
        assertFalse(actual = RowForm.normalizeRow(state = state, rowId = "cmp-1"))
        assertEquals(expected = "\$debtToIncomeRatio > 0.4", actual = whenText(state = state))
    }

    @Test
    fun `a plain field against a literal is demoted`() {
        val state = stateWith(
            node = BuilderConditionNode.Comparison(
                nodeId = "cmp-1",
                left = ui.builder.model.pathOperand(dotted = "amount"),
                operator = ">=",
                right = BuilderOperand.Literal(text = "300", numeric = true),
            ),
        )
        assertTrue(actual = RowForm.normalizeRow(state = state, rowId = "cmp-1"))
        assertIs<MutableConditionNode.Leaf>(value = state.conditionNodes.first())
        assertEquals(expected = "amount >= 300", actual = whenText(state = state))
    }

    @Test
    fun `demotion keeps a list right side as list items`() {
        val state = stateWith(
            node = BuilderConditionNode.Comparison(
                nodeId = "cmp-1",
                left = ui.builder.model.pathOperand(dotted = "country"),
                operator = "in",
                right = BuilderOperand.ListLiteral(items = listOf("de", "at")),
            ),
        )
        assertTrue(actual = RowForm.normalizeRow(state = state, rowId = "cmp-1"))
        val leaf = assertIs<MutableConditionNode.Leaf>(value = state.conditionNodes.first())
        assertEquals(expected = listOf("de", "at"), actual = leaf.inner.listItems.toList())
        assertEquals(expected = """country in ["de", "at"]""", actual = whenText(state = state))
    }

    @Test
    fun `a row nested in a group is normalized too`() {
        val state = stateWith(
            node = BuilderConditionNode.Group(
                nodeId = "grp-1",
                nodes = listOf(
                    BuilderConditionNode.Comparison(
                        nodeId = "cmp-9",
                        left = ui.builder.model.pathOperand(dotted = "amount"),
                        operator = ">=",
                        right = BuilderOperand.Literal(text = "300", numeric = true),
                    ),
                ),
            ),
        )
        assertTrue(actual = RowForm.normalizeRow(state = state, rowId = "cmp-9"))
    }

    // ── the classification the whole thing rests on ───────────────────────────

    @Test
    fun `isComputed identifies the kinds that force the comparison form`() {
        assertTrue(
            actual = RowForm.isComputed(
                operand = BuilderOperand.Aggregate(function = "sum", path = listOf(BuilderPathStep(name = "a"))),
            ),
        )
        assertTrue(actual = RowForm.isComputed(operand = BuilderOperand.Call(function = "abs", args = emptyList())))
        assertTrue(actual = RowForm.isComputed(operand = BuilderOperand.Calc(terms = emptyList())))
        assertFalse(actual = RowForm.isComputed(operand = ui.builder.model.pathOperand(dotted = "amount")))
        assertFalse(actual = RowForm.isComputed(operand = BuilderOperand.Literal(text = "1", numeric = true)))
    }
}
