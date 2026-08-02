package ui.builder

import ruleengine.compiler.Validator
import ruleengine.core.domain.dto.OperatorId
import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.core.errors.Severity
import ruleengine.dsl.ast.AssignmentKindAst
import ruleengine.dsl.parser.Parser
import ui.builder.model.BuilderOperand
import ui.builder.model.BuilderPathStep
import ui.builder.model.catalog.CatalogFieldInfo
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

    // ── add clauses ───────────────────────────────────────────────────────────

    @Test
    fun `an add clause survives the builder round-trip`() {
        val original = """
            rule "billing" {
              description "d"
              when
                amount >= 1
              then
                add "billing" to topics
            }
        """.trimIndent()

        val state = builderStateFromDsl(dsl = original)

        val row = state.variables.single()
        assertEquals(expected = "topics", actual = row.name)
        assertEquals(expected = AssignmentKindAst.ADD, actual = row.kind)

        val regenerated = BuilderToRuleDsl.generate(state = state).orEmpty()
        assertTrue(
            actual = regenerated.contains(other = """add "billing" to topics"""),
            message = "expected an add clause, got:\n$regenerated",
        )
    }

    /**
     * The kinds must not be normalised into each other. An `add` written back as a `set` would turn
     * an accumulator into a scalar and break every guard reading it.
     */
    @Test
    fun `a set and an add in one block keep their kinds and order`() {
        val original = """
            rule "mixed" {
              description "d"
              when
                amount >= 1
              then
                add "billing" to topics
                set tier = 2
            }
        """.trimIndent()

        val state = builderStateFromDsl(dsl = original)

        assertEquals(
            expected = listOf(AssignmentKindAst.ADD, AssignmentKindAst.SET),
            actual = state.variables.map { row -> row.kind },
        )

        val regenerated = BuilderToRuleDsl.generate(state = state).orEmpty()
        assertTrue(
            actual = regenerated.indexOf(string = """add "billing" to topics""") <
                regenerated.indexOf(string = "set tier = 2"),
            message = "expected the add first, got:\n$regenerated",
        )
    }

    @Test
    fun `a guard reading a list variable survives the builder round-trip`() {
        val original = """
            rule "guarded" {
              description "d"
              when
                not ${'$'}topics contains "billing"
                and amount >= 1
              then
                add "billing" to topics
            }
        """.trimIndent()

        val regenerated = BuilderToRuleDsl.generate(state = builderStateFromDsl(dsl = original)).orEmpty()

        assertTrue(
            actual = regenerated.contains(other = """not ${'$'}topics contains "billing""""),
            message = "expected the guard to survive, got:\n$regenerated",
        )
    }

    /** A variable an `add` builds is typed as a list, which is what makes the row offer `contains`. */
    @Test
    fun `the operand catalog types an accumulator as a list variable`() {
        val files = listOf(
            RuleSource(
                relativePath = "topics.rule",
                rules = Parser(
                    input = """
                        rule "billing" {
                          description "d"
                          when
                            amount >= 1
                          then
                            add "billing" to topics
                            set tier = 2
                        }
                    """.trimIndent()
                ).parseRules(),
            )
        )

        val catalog = builderCatalogVariablesFrom(files = files, uptoRuleId = null)
            .associate { info -> info.id to info.type }

        assertEquals(expected = OperatorOptions.LIST_VARIABLE_TYPE, actual = catalog["${'$'}topics"])
        assertEquals(expected = "decimal", actual = catalog["${'$'}tier"])
    }

    /**
     * The engine lets a rule guard on the list it fills in, so the operand picker has to offer that
     * list while editing that very rule — otherwise the row cannot resolve `${'$'}topics`, falls back to
     * the text operators, and offers only `==` and `!=` for a membership test.
     */
    @Test
    fun `the catalog offers a list the edited rule is itself the first to write`() {
        val files = listOf(
            RuleSource(
                relativePath = "topics.rule",
                rules = Parser(
                    input = """
                        rule "billing-from-refund" {
                          description "d"
                          when
                            not ${'$'}topics contains "billing"
                          then
                            add "billing" to topics
                        }
                    """.trimIndent()
                ).parseRules(),
            )
        )

        val catalog = builderCatalogVariablesFrom(files = files, uptoRuleId = "billing-from-refund")
            .associate { info -> info.id to info.type }

        assertEquals(expected = OperatorOptions.LIST_VARIABLE_TYPE, actual = catalog["${'$'}topics"])
    }

    /** And the row must then offer `contains` rather than the text fallback. */
    @Test
    fun `a guard row on that list offers contains`() {
        val fields = listOf(
            CatalogFieldInfo(id = "${'$'}topics", type = OperatorOptions.LIST_VARIABLE_TYPE),
        )

        val operators = OperandRules.operatorsFor(
            left = BuilderOperand.FieldRef(path = listOf(BuilderPathStep(name = "${'$'}topics"))),
            right = BuilderOperand.Literal(text = "billing", numeric = false),
            fields = fields,
        )

        assertEquals(expected = listOf(OperatorOptions.CONTAINS), actual = operators)
    }

    /** A `set` in an `else` block is in scope for the engine, so the picker must offer it too. */
    @Test
    fun `the catalog offers variables written in an else branch`() {
        val files = listOf(
            RuleSource(
                relativePath = "topics.rule",
                rules = Parser(
                    input = """
                        rule "evidence" {
                          description "d"
                          when
                            amount >= 1
                          then
                            add "has-evidence" to topics
                          else
                            set fallbackTier = 1
                        }
                        rule "reader" {
                          description "d"
                          when
                            amount >= 1
                          then
                            label "x"
                        }
                    """.trimIndent()
                ).parseRules(),
            )
        )

        val catalog = builderCatalogVariablesFrom(files = files, uptoRuleId = "reader")
            .associate { info -> info.id to info.type }

        assertEquals(expected = OperatorOptions.LIST_VARIABLE_TYPE, actual = catalog["${'$'}topics"])
        assertEquals(expected = "decimal", actual = catalog["${'$'}fallbackTier"])
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
