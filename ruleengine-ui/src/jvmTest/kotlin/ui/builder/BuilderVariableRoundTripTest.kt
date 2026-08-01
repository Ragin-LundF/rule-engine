package ui.builder

import ruleengine.compiler.Validator
import ruleengine.core.domain.dto.OperatorId
import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.core.errors.Severity
import ruleengine.dsl.parser.Parser
import ui.builder.model.BuilderOperand
import ui.builder.model.mutable.BuilderEditorState
import ui.diagrams.model.RuleSource
import ui.workbench.builderCatalogVariablesFrom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Round-trip of `set` clauses and `$name` reads through the visual Builder, plus the scope rule the
 * operand picker follows.
 */
class BuilderVariableRoundTripTest {

    private val schema = FieldSchema(
        name = "test",
        fields = mapOf(
            FieldId("amount") to FieldDefinition(
                id = FieldId("amount"),
                type = FieldType.DECIMAL,
                operators = setOf(OperatorId(">="), OperatorId("<=")),
            ),
            FieldId("orders") to FieldDefinition(
                id = FieldId("orders"),
                type = FieldType.COLLECTION,
            ),
        ),
    )

    @Test
    fun `a set clause survives the builder round-trip`() {
        val original = """
            rule "total" {
              description "d"
              when
                amount >= 1
              then
                set orderTotal = sum(orders.amount)
            }
        """.trimIndent()

        val state = builderStateFromDsl(dsl = original)

        assertEquals(expected = listOf("orderTotal"), actual = state.variables.map { it.name })
        assertIs<BuilderOperand.Aggregate>(value = state.variables[0].expression)

        val regenerated = BuilderToRuleDsl.generate(state = state).orEmpty()
        assertTrue(
            actual = regenerated.contains(other = "set orderTotal = sum(orders.amount)"),
            message = "unexpected DSL: $regenerated"
        )
        assertNoErrors(dsl = regenerated)
    }

    @Test
    fun `a variable read in a condition survives the builder round-trip`() {
        val original = """
            rule "writer" {
              description "d"
              when
                amount >= 1
              then
                set threshold = 100
            }
            rule "reader" {
              description "d"
              when
                ${'$'}threshold <= amount
              then
                set flagged = true
            }
        """.trimIndent()

        val readerState = builderStateFromDsl(dsl = original, ruleIndex = 1)
        val regenerated = BuilderToRuleDsl.generate(state = readerState).orEmpty()

        assertTrue(
            actual = regenerated.contains(other = "\$threshold <= amount"),
            message = "unexpected DSL: $regenerated"
        )
    }

    @Test
    fun `a variable used as an action argument is not quoted`() {
        val original = """
            rule "score" {
              description "d"
              when
                amount >= 1
              then
                set risk = amount * 2
                label ${'$'}risk
            }
        """.trimIndent()

        val state = builderStateFromDsl(dsl = original)
        val regenerated = BuilderToRuleDsl.generate(state = state).orEmpty()

        assertTrue(
            actual = regenerated.contains(other = "label \$risk"),
            message = "unexpected DSL: $regenerated"
        )
        // Re-parsing must yield the variable argument again, not a quoted string literal.
        val reparsed = Parser(input = regenerated).parseRules().single()
        assertEquals(expected = "VariableRefLiteral", actual = reparsed.actions[0].arguments[0]::class.simpleName)
    }

    @Test
    fun `a currency-looking literal stays quoted`() {
        assertEquals(expected = "\"\$100\"", actual = OperandText.quoteUnlessNumeric(value = "\$100"))
        assertEquals(expected = "\$total", actual = OperandText.quoteUnlessNumeric(value = "\$total"))
    }

    @Test
    fun `the operand catalog offers only variables assigned by earlier rules`() {
        val files = listOf(
            RuleSource(relativePath = "a.rule", rules = Parser(input = FIRST_FILE).parseRules()),
            RuleSource(relativePath = "b.rule", rules = Parser(input = SECOND_FILE).parseRules()),
        )

        assertEquals(
            expected = emptyList(),
            actual = builderCatalogVariablesFrom(files = files, uptoRuleId = "first").map { it.id },
            message = "the first rule sees nothing yet",
        )
        assertEquals(
            expected = listOf("\$alpha"),
            actual = builderCatalogVariablesFrom(files = files, uptoRuleId = "second").map { it.id },
            message = "the second rule sees only what the first published",
        )
        assertEquals(
            expected = listOf("\$alpha", "\$beta"),
            actual = builderCatalogVariablesFrom(files = files, uptoRuleId = null).map { it.id },
            message = "no target rule means the whole entry",
        )
    }

    @Test
    fun `an aggregate assignment is typed numeric so ordering operators stay available`() {
        val files = listOf(
            RuleSource(relativePath = "a.rule", rules = Parser(input = FIRST_FILE).parseRules()),
        )

        val alpha = builderCatalogVariablesFrom(files = files, uptoRuleId = null).single()
        assertEquals(expected = "decimal", actual = alpha.type)
        assertTrue(actual = OperatorOptions.SYMBOL_GTE in OperatorOptions.forField(fieldType = alpha.type))
    }

    @Test
    fun `an untyped variable offers every symbolic comparison`() {
        assertEquals(
            expected = OperatorOptions.COMPARISON_NUMERIC,
            actual = OperatorOptions.forField(fieldType = OperatorOptions.VARIABLE_TYPE),
        )
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun builderStateFromDsl(dsl: String, ruleIndex: Int = 0): BuilderEditorState {
        val rule = Parser(input = dsl).parseRules()[ruleIndex]
        return BuilderEditorState.fromBuilderRule(RuleAstToBuilderMapper.map(rule))
    }

    private fun assertNoErrors(dsl: String) {
        val result = Validator.validate(asts = Parser(input = dsl).parseRules(), schema = schema, actions = null)
        assertTrue(
            actual = result.diagnostics.none { it.severity == Severity.ERROR },
            message = "unexpected errors: ${result.diagnostics}"
        )
    }

    private companion object {
        val FIRST_FILE: String = """
            rule "first" {
              description "d"
              when
                amount >= 1
              then
                set alpha = sum(orders.amount)
            }
        """.trimIndent()

        val SECOND_FILE: String = """
            rule "second" {
              description "d"
              when
                ${'$'}alpha > 0
              then
                set beta = 1
            }
        """.trimIndent()
    }
}
