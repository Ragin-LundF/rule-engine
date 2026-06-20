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
        state.conditions[0].value = "700"

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
                conditions = listOf(
                    BuilderCondition(
                        id = "c1",
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
        state.conditions[0].valueTo = "500"

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
        assertEquals("", state.conditions[0].joinToPrevious)
        assertEquals("or", state.conditions[1].joinToPrevious)
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
        val joins = state.conditions.map { it.joinToPrevious }
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
}
