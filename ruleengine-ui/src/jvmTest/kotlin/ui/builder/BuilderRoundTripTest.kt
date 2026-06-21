package ui.builder

import ruleengine.compiler.Validator
import ruleengine.core.domain.FieldDefinition
import ruleengine.core.domain.FieldId
import ruleengine.core.domain.FieldSchema
import ruleengine.core.domain.FieldType
import ruleengine.core.domain.OperatorId
import ruleengine.dsl.parser.Parser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Round-trip tests: DSL → BuilderModel → DSL → Parser → Validator.
 *
 * Verifies that [BuilderToRuleDsl.generate] produces text that the core pipeline
 * accepts as valid, and that [BuilderEditorState] correctly reflects edits.
 */
class BuilderRoundTripTest {

    // ── schema fixture ────────────────────────────────────────────────────────

    private val schema = FieldSchema(
        name = "test",
        fields = mapOf(
            FieldId("purpose") to FieldDefinition(
                id = FieldId("purpose"),
                type = FieldType.TEXT,
                operators = setOf(OperatorId("contains"), OperatorId("equals")),
            ),
            FieldId("amount") to FieldDefinition(
                id = FieldId("amount"),
                type = FieldType.DECIMAL,
                operators = setOf(OperatorId(">="), OperatorId("<="), OperatorId("between")),
            ),
            FieldId("tags") to FieldDefinition(
                id = FieldId("tags"),
                type = FieldType.STRING_SET,
                operators = setOf(OperatorId("containsAny")),
            ),
        ),
    )

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun parseAndValidate(dsl: String): ruleengine.compiler.ValidationResult {
        val asts = Parser(input = dsl).parseRules()
        return Validator.validate(asts = asts, schema = schema, actions = null)
    }

    private fun builderStateFromDsl(dsl: String): BuilderEditorState {
        val asts = Parser(input = dsl).parseRules()
        val rule = asts.firstOrNull() ?: error("No rule parsed")
        val builderRule = RuleAstToBuilderMapper.map(rule)
        return BuilderEditorState.fromBuilderRule(builderRule)
    }

    /** Convenience: unwrap the N-th top-level condition node as a leaf. */
    private fun leafCondition(state: BuilderEditorState, index: Int): MutableBuilderCondition {
        val node = state.conditionNodes[index]
        return (node as MutableConditionNode.Leaf).inner
    }

    /** Recursively collect all leaf join words (preserving order). */
    private fun allLeafJoins(state: BuilderEditorState): List<String> {
        return collectJoins(state.conditionNodes)
    }

    private fun collectJoins(nodes: List<MutableConditionNode>): List<String> {
        val result = mutableListOf<String>()
        for (node in nodes) {
            when (node) {
                is MutableConditionNode.Leaf -> result.add(node.inner.joinToPrevious)
                is MutableConditionNode.Group -> result.addAll(collectJoins(node.nodes))
            }
        }
        return result
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `text contains round-trip produces valid DSL`() {
        val original = """
            rule "rent-check" {
              when
                purpose contains "rent"
              then
                label "rent"
            }
        """.trimIndent()

        val state = builderStateFromDsl(original)
        assertIs<BuilderEditorState>(state)
        val generated = BuilderToRuleDsl.generate(state)
        assertNotNull(generated)

        val result = parseAndValidate(generated)
        assertTrue(result.isValid, "Generated DSL should be valid; diagnostics: ${result.diagnostics}")
    }

    @Test
    fun `decimal gte round-trip produces valid DSL`() {
        val original = """
            rule "high-amount" {
              when
                amount >= 500
              then
                flag "high"
            }
        """.trimIndent()

        val state = builderStateFromDsl(original)
        val generated = BuilderToRuleDsl.generate(state)
        assertNotNull(generated)

        val result = parseAndValidate(generated)
        assertTrue(result.isValid, "Generated DSL should be valid; diagnostics: ${result.diagnostics}")
    }

    @Test
    fun `multiple actions round-trip produces valid DSL`() {
        val original = """
            rule "multi-action" {
              when
                purpose contains "rent"
              then
                label "rent"
                category "housing"
            }
        """.trimIndent()

        val state = builderStateFromDsl(original)
        val generated = BuilderToRuleDsl.generate(state)
        assertNotNull(generated)

        val result = parseAndValidate(generated)
        assertTrue(result.isValid, "Generated DSL should be valid; diagnostics: ${result.diagnostics}")
        // Both actions must appear in generated text
        assertTrue(generated.contains("label"), "Generated DSL must contain 'label'")
        assertTrue(generated.contains("category"), "Generated DSL must contain 'category'")
    }

    @Test
    fun `editing value in BuilderEditorState changes generated DSL`() {
        val original = """
            rule "rent-check" {
              when
                amount >= 500
              then
                flag "high"
            }
        """.trimIndent()

        val state = builderStateFromDsl(original)
        assertIs<BuilderEditorState>(state)

        // Simulate user changing the value from 500 to 700
        leafCondition(state, index = 0).value = "700"

        val generated = BuilderToRuleDsl.generate(state)
        assertNotNull(generated)
        assertTrue(generated.contains("700"), "Generated DSL must contain updated value 700")

        val result = parseAndValidate(generated)
        assertTrue(result.isValid, "Edited DSL should be valid; diagnostics: ${result.diagnostics}")
    }

    @Test
    fun `between operator built manually round-trips to valid DSL`() {
        // Build a between condition directly in BuilderEditorState (no DSL parse needed)
        // because the mapper currently marks BetweenLiteral as unsupported.
        val state = BuilderEditorState.fromBuilderRule(
            BuilderRule.Supported(
                id = "mid-range",
                conditionNodes = listOf(
                    BuilderConditionNode.Condition(
                        nodeId = "c1",
                        field = "amount",
                        operator = "between",
                        value = "100",
                    ),
                ),
                actions = listOf(
                    BuilderAction(
                        id = "a1",
                        name = "flag",
                        arguments = listOf("mid"),
                    ),
                ),
            )
        )
        // Populate valueTo for the between editor
        leafCondition(state, index = 0).valueTo = "500"

        val generated = BuilderToRuleDsl.generate(state)
        assertNotNull(generated, "between state must generate DSL")
        assertTrue(generated.contains("between"), "Generated DSL must contain 'between'")
        assertTrue(generated.contains("100"), "Generated DSL must contain low bound")
        assertTrue(generated.contains("500"), "Generated DSL must contain high bound")

        val result = parseAndValidate(generated)
        assertTrue(result.isValid, "Generated between DSL should be valid; diagnostics: ${result.diagnostics}")
    }

    @Test
    fun `OrAst rule round-trips with per-link joins`() {
        val original = """
            rule "or-rule" {
              when
                purpose contains "rent"
                or amount >= 500
              then
                label "misc"
            }
        """.trimIndent()

        val state = builderStateFromDsl(original)
        assertTrue(!state.isLocked, "OrAst rule should be editable in BuilderEditorState")

        val joins = allLeafJoins(state)
        assertEquals(listOf("", "or"), joins)

        val generated = BuilderToRuleDsl.generate(state)
        assertNotNull(generated)

        val result = parseAndValidate(generated)
        assertTrue(result.isValid, "Generated Or DSL should be valid; diagnostics: ${result.diagnostics}")
    }

    @Test
    fun `mixed AND OR rule round-trips to valid DSL`() {
        val original = """
            rule "mixed-rule" {
              when
                purpose contains "rent"
                and amount >= 500
                or purpose equals "misc"
              then
                label "misc"
            }
        """.trimIndent()

        val state = builderStateFromDsl(original)
        assertTrue(!state.isLocked, "Mixed join rule should be editable in BuilderEditorState")

        val joins = allLeafJoins(state)
        assertEquals(listOf("", "and", "or"), joins)

        val generated = BuilderToRuleDsl.generate(state)
        assertNotNull(generated)

        val result = parseAndValidate(generated)
        assertTrue(result.isValid, "Generated mixed join DSL should be valid; diagnostics: ${result.diagnostics}")
    }

    @Test
    fun `OperatorOptions returns schema-restricted operators for decimal field`() {
        val ops = OperatorOptions.forField(
            fieldType = "decimal",
            schemaOperators = listOf(">=", "<="),
        )
        assertEquals(listOf(">=", "<="), ops)
    }

    // ── parenthesized grouping tests ──────────────────────────────────────────

    @Test
    fun `parenthesized (A OR B) AND C round-trip preserves grouping`() {
        val original = """
            rule "grouped-or-in-and" {
              when
                (purpose contains "rent" or amount >= 500)
                and purpose equals "misc"
              then
                label "grouped"
            }
        """.trimIndent()

        val state = builderStateFromDsl(original)
        assertTrue(!state.isLocked, "Parenthesized rule should be editable")

        // 2 top-level nodes: one Group, one leaf Condition
        assertEquals(expected = 2, actual = state.conditionNodes.size)

        val firstNode = state.conditionNodes[0]
        assertIs<MutableConditionNode.Group>(firstNode)
        assertEquals(expected = 2, actual = firstNode.nodes.size)
        assertEquals(expected = "", actual = firstNode.joinToPrevious)

        val secondNode = state.conditionNodes[1]
        assertIs<MutableConditionNode.Leaf>(secondNode)
        assertEquals(expected = "and", actual = secondNode.joinToPrevious)

        val generated = BuilderToRuleDsl.generate(state)
        assertNotNull(generated)
        assertTrue(generated.contains("("), "Generated DSL must have opening paren")
        assertTrue(generated.contains(")"), "Generated DSL must have closing paren")

        val result = parseAndValidate(generated)
        assertTrue(result.isValid, "Generated grouped DSL should be valid")
    }

    @Test
    fun `parenthesized A AND (B OR C) round-trip preserves grouping`() {
        val original = """
            rule "and-or" {
              when
                purpose contains "rent"
                and (amount >= 500 or amount <= 100)
              then
                label "check"
            }
        """.trimIndent()

        val state = builderStateFromDsl(original)
        assertTrue(!state.isLocked, "Parenthesized rule should be editable")

        // 2 top-level nodes: one leaf Condition, one Group
        assertEquals(expected = 2, actual = state.conditionNodes.size)

        val firstNode = state.conditionNodes[0]
        assertIs<MutableConditionNode.Leaf>(firstNode)
        assertEquals(expected = "", actual = firstNode.joinToPrevious)

        val secondNode = state.conditionNodes[1]
        assertIs<MutableConditionNode.Group>(secondNode)
        assertEquals(expected = "and", actual = secondNode.joinToPrevious)
        assertEquals(expected = 2, actual = secondNode.nodes.size)

        val generated = BuilderToRuleDsl.generate(state)
        assertNotNull(generated)
        assertTrue(generated.contains("("), "Generated DSL must have opening paren")
        assertTrue(generated.contains(")"), "Generated DSL must have closing paren")

        val result = parseAndValidate(generated)
        assertTrue(result.isValid, "Generated grouped DSL should be valid")
    }

    @Test
    fun `deeply nested ((A OR B) AND C) OR D round-trips correctly`() {
        val original = """
            rule "deep" {
              when
                ((purpose contains "rent" or amount >= 500)
                and purpose equals "misc")
                or amount <= 100
              then
                label "deep"
            }
        """.trimIndent()

        val state = builderStateFromDsl(original)
        assertTrue(!state.isLocked, "Deeply nested rule should be editable")

        // 2 top-level nodes: one Group, one leaf
        assertEquals(expected = 2, actual = state.conditionNodes.size, "Should have 2 top-level nodes")

        val outerGroup = state.conditionNodes[0]
        assertIs<MutableConditionNode.Group>(outerGroup)
        assertEquals(expected = "", actual = outerGroup.joinToPrevious)
        // Outer group must have 2 children: inner Group + leaf Condition
        assertEquals(expected = 2, actual = outerGroup.nodes.size)

        val innerGroup = outerGroup.nodes[0]
        assertIs<MutableConditionNode.Group>(innerGroup)
        assertEquals(expected = "", actual = innerGroup.joinToPrevious)
        assertEquals(expected = 2, actual = innerGroup.nodes.size)

        val innerLeaf = innerGroup.nodes[0]
        assertIs<MutableConditionNode.Leaf>(innerLeaf)
        assertEquals(expected = "", actual = innerLeaf.joinToPrevious)

        val innerLeaf2 = innerGroup.nodes[1]
        assertIs<MutableConditionNode.Leaf>(innerLeaf2)
        assertEquals(expected = "or", actual = innerLeaf2.joinToPrevious)

        val midLeaf = outerGroup.nodes[1]
        assertIs<MutableConditionNode.Leaf>(midLeaf)
        assertEquals(expected = "and", actual = midLeaf.joinToPrevious)

        val topLeaf = state.conditionNodes[1]
        assertIs<MutableConditionNode.Leaf>(topLeaf)
        assertEquals(expected = "or", actual = topLeaf.joinToPrevious)

        val generated = BuilderToRuleDsl.generate(state)
        assertNotNull(generated)

        val result = parseAndValidate(generated)
        assertTrue(result.isValid, "Generated deeply nested DSL should be valid; diagnostics: ${result.diagnostics}")
    }

    // ── group operation round-trip tests ──────────────────────────────────

    @Test
    fun `create group via state api and round-trip`() {
        val original = """
            rule "test" {
              when
                purpose contains "rent"
                and amount >= 500
                or purpose equals "misc"
              then
                label "test"
            }
        """.trimIndent()

        val state = builderStateFromDsl(original)
        // Group first two conditions
        state.groupConditions(ids = setOf(state.conditionNodes[0].id, state.conditionNodes[1].id))

        val generated = BuilderToRuleDsl.generate(state)
        assertNotNull(generated)

        val result = parseAndValidate(generated)
        assertTrue(result.isValid, "Grouped DSL should be valid: ${result.diagnostics}")
        assertTrue(generated.contains("("), "Generated DSL should contain '('")
        assertTrue(generated.contains(")"), "Generated DSL should contain ')'")
    }

    @Test
    fun `create empty group and add conditions inside via state api`() {
        val state = BuilderEditorState.fromBuilderRule(
            BuilderRule.Supported(
                id = "test",
                conditionNodes = listOf(
                    BuilderConditionNode.Group(
                        nodeId = "g1",
                        nodes = emptyList(),
                    ),
                ),
                actions = emptyList(),
            )
        )

        state.addConditionInside(groupId = "g1", defaultField = "purpose", defaultOperator = "equals")
        // Set value on the first condition inside the group
        val group = state.conditionNodes[0] as MutableConditionNode.Group
        (group.nodes[0] as MutableConditionNode.Leaf).inner.value = "rent"
        state.addConditionInside(groupId = "g1", defaultField = "amount", defaultOperator = ">=")
        (group.nodes[1] as MutableConditionNode.Leaf).inner.value = "500"

        val generated = BuilderToRuleDsl.generate(state)
        assertNotNull(generated)

        val result = parseAndValidate(generated)
        assertTrue(result.isValid, "DSL from empty-group creation should be valid: ${result.diagnostics}")
    }

    @Test
    fun `ungroup via state api and round-trip`() {
        val original = """
            rule "test" {
              when
                (purpose contains "rent" or amount >= 500)
                and purpose equals "misc"
              then
                label "test"
            }
        """.trimIndent()

        val state = builderStateFromDsl(original)
        assertEquals(expected = 2, actual = state.conditionNodes.size)

        state.ungroup(id = state.conditionNodes[0].id)

        val generated = BuilderToRuleDsl.generate(state)
        assertNotNull(generated)
        assertTrue(!generated.contains("("), "Ungrouped DSL should not contain parentheses")
        assertTrue(!generated.contains(")"), "Ungrouped DSL should not contain parentheses")

        val result = parseAndValidate(generated)
        assertTrue(result.isValid, "Ungrouped DSL should be valid: ${result.diagnostics}")
    }
}
