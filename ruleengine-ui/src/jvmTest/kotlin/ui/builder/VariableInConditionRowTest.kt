package ui.builder

import ruleengine.dsl.parser.Parser
import ui.builder.model.catalog.CatalogFieldInfo
import ui.builder.model.catalog.scalarPaths
import ui.builder.model.mutable.BuilderEditorState
import ui.builder.view.defaultOperatorFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Using a rule output variable in a plain condition row.
 *
 * The row used to filter every `$name` out of its field dropdown, so a variable published by a `set`
 * or an `add` could not be picked at all — the only way in was the advanced comparison row. The
 * filter was guarding something real: a named operator on a `$name` is read as a plain field
 * comparison and rejected as an unknown field, and a variable catalogued as `decimal` would bring
 * `equals` and `between` with it. The operator list is what guards that now.
 */
class VariableInConditionRowTest {

    private val fields = listOf(
        CatalogFieldInfo(id = "amount", type = "decimal"),
        CatalogFieldInfo(id = "purpose", type = "text"),
        CatalogFieldInfo(id = "\$topics", type = OperatorOptions.LIST_VARIABLE_TYPE),
        CatalogFieldInfo(id = "\$tier", type = "decimal"),
        CatalogFieldInfo(id = "\$note", type = "text"),
    )

    @Test
    fun `a condition row offers variables alongside schema fields`() {
        assertEquals(
            expected = listOf("amount", "purpose", "\$topics", "\$tier", "\$note"),
            actual = fields.scalarPaths().map { it.id },
        )
    }

    @Test
    fun `a list variable offers contains and nothing else`() {
        assertEquals(
            expected = listOf(OperatorOptions.CONTAINS),
            actual = OperatorOptions.forCatalogField(
                fieldId = "\$topics",
                fieldType = OperatorOptions.LIST_VARIABLE_TYPE,
            ),
        )
    }

    /** The trap the old exclusion existed for: `decimal` would otherwise bring `equals` and `between`. */
    @Test
    fun `a variable inferred as decimal still offers only symbolic comparisons`() {
        val operators = OperatorOptions.forCatalogField(fieldId = "\$tier", fieldType = "decimal")

        assertEquals(expected = OperatorOptions.COMPARISON_NUMERIC, actual = operators)
        assertFalse(actual = operators.contains(element = OperatorOptions.EQUALS))
        assertFalse(actual = operators.contains(element = OperatorOptions.BETWEEN))
    }

    @Test
    fun `a variable inferred as text does not offer the named text operators`() {
        val operators = OperatorOptions.forCatalogField(fieldId = "\$note", fieldType = "text")

        assertFalse(actual = operators.contains(element = OperatorOptions.STARTS_WITH))
        assertFalse(actual = operators.contains(element = OperatorOptions.REGEX))
    }

    @Test
    fun `a schema field is unaffected`() {
        assertEquals(
            expected = OperatorOptions.forField(fieldType = "text"),
            actual = OperatorOptions.forCatalogField(fieldId = "purpose", fieldType = "text"),
        )
    }

    // ── "does not contain" ────────────────────────────────────────────────────

    /**
     * There is no `containsNot` operator, and there does not need to be: the DSL spells a negated
     * condition `not <condition>`, and every row carries a NOT toggle. `not ${'$'}topics contains "x"` is
     * the guard the whole accumulator pattern is built on.
     */
    @Test
    fun `NOT plus contains is how a row says does not contain`() {
        val dsl = """
            rule "guarded" {
              description "d"
              when
                not ${'$'}topics contains "billing"
              then
                label "y"
            }
        """.trimIndent()

        val state = BuilderEditorState.fromBuilderRule(
            rule = RuleAstToBuilderMapper.map(rule = Parser(input = dsl).parseRules().single())
        )

        assertTrue(actual = state.conditionNodes.single().negated, message = "the row should read as negated")

        val regenerated = BuilderToRuleDsl.generate(state = state).orEmpty()
        assertTrue(
            actual = regenerated.contains(other = """not ${'$'}topics contains "billing""""),
            message = "round-trip changed the guard:\n$regenerated",
        )
    }

    /** Toggling NOT off leaves the positive test, so the one control covers both directions. */
    @Test
    fun `clearing NOT leaves a plain contains`() {
        val dsl = """
            rule "guarded" {
              description "d"
              when
                not ${'$'}topics contains "billing"
              then
                label "y"
            }
        """.trimIndent()
        val state = BuilderEditorState.fromBuilderRule(
            rule = RuleAstToBuilderMapper.map(rule = Parser(input = dsl).parseRules().single())
        )

        state.conditionNodes.single().negated = false

        val regenerated = BuilderToRuleDsl.generate(state = state).orEmpty()
        assertTrue(actual = regenerated.contains(other = """${'$'}topics contains "billing""""))
        assertFalse(actual = regenerated.contains(other = "not ${'$'}topics"))
    }

    // ── the pre-selected operator ─────────────────────────────────────────────

    /**
     * A new row, and a row whose field was just changed, must start on an operator the dropdown
     * actually offers. Starting on `equals` — the default for any field — showed a selection that was
     * not in the list and generated `${'$'}tier equals 2`, which the engine rejects.
     */
    @Test
    fun `a new row on a list variable starts on contains`() {
        val start = fields.scalarPaths().first { it.id == "${'$'}topics" }

        assertEquals(expected = OperatorOptions.CONTAINS, actual = defaultOperatorFor(field = start))
    }

    @Test
    fun `a new row on a scalar variable starts on a symbolic comparison`() {
        val start = fields.scalarPaths().first { it.id == "${'$'}tier" }

        assertEquals(expected = OperatorOptions.SYMBOL_EQUALS, actual = defaultOperatorFor(field = start))
    }

    @Test
    fun `the starting operator is always one the row offers`() {
        for (field in fields.scalarPaths()) {
            val offered = OperatorOptions.forCatalogField(
                fieldId = field.id,
                fieldType = field.type,
                schemaOperators = field.operators,
            )
            assertTrue(
                actual = defaultOperatorFor(field = field) in offered,
                message = "${field.id}: default ${defaultOperatorFor(field = field)} not in $offered",
            )
        }
    }

    /**
     * Every operator a variable row can offer has to produce a rule the engine accepts, which means
     * parsing to the expression path rather than to a plain field comparison.
     */
    @Test
    fun `every offered operator produces a rule that names no unknown field`() {
        val cases = listOf("\$topics" to OperatorOptions.LIST_VARIABLE_TYPE, "\$tier" to "decimal")
        for ((id, type) in cases) {
            for (operator in OperatorOptions.forCatalogField(fieldId = id, fieldType = type)) {
                val dsl = """
                    rule "r" {
                      description "d"
                      when
                        $id $operator "x"
                      then
                        label "y"
                    }
                """.trimIndent()
                val condition = Parser(input = dsl).parseRules().single().condition
                assertTrue(
                    actual = condition is ruleengine.dsl.ast.ComparisonExpressionAst,
                    message = "$id $operator took the legacy path: $condition",
                )
            }
        }
    }
}
