package ruleengine.compiler

import ruleengine.core.errors.CompilationException
import ruleengine.dsl.parser.Parser
import ruleengine.evaluator.compiled.CompiledActionArgument
import ruleengine.schema.FieldSchemaLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Every [CompilationException] must name the rule it came from.
 *
 * The operator layer has always taken a `ruleId`, but the compiler fed it a helper that returned a constant
 * `null`, so every failure read `Compilation failed for rule <unknown>` no matter which rule broke. These
 * tests pin the id to the failure for each way compilation can fail.
 *
 * The action argument cases cover the second half of the same fix: a literal that the compiler could not
 * translate used to become either the AST node's `toString()` or a silent `null`.
 */
class CompilerErrorReportingTest {

    private val schema = FieldSchemaLoader.loadFromString(
        content = """
            schema: compiler-errors-v1

            fields:
              iban:
                type: text
              amount:
                type: decimal
              transitDays:
                type: integer
              tags:
                type: string_set
              parcels:
                type: collection
                fields:
                  status:
                    type: text
                  weightKg:
                    type: decimal
        """.trimIndent()
    )

    private fun compile(rule: String) {
        Compiler.compileRules(asts = Parser(input = rule).parseRules(), schema = schema)
    }

    private fun compileSingleAction(rule: String): List<CompiledActionArgument> {
        val compiled = Compiler.compileRules(asts = Parser(input = rule).parseRules(), schema = schema)
        return compiled.single().actions.single().arguments
    }

    // --- the rule id reaches every failure site ---

    @Test
    fun `unknown field in a condition names the rule`() {
        val failure = assertFailsWith<CompilationException> {
            compile(
                rule = """
                    rule "unknown-field" {
                      when
                        ibna equals "DE00"
                      then
                        label "x"
                    }
                """.trimIndent()
            )
        }
        assertEquals(expected = "unknown-field", actual = failure.ruleId)
        assertTrue(
            actual = failure.message?.contains("Compilation failed for rule unknown-field") == true,
            message = "Expected the message to name the rule, got: ${failure.message}"
        )
    }

    @Test
    fun `condition reading through a collection names the rule`() {
        val failure = assertFailsWith<CompilationException> {
            compile(
                rule = """
                    rule "crosses-collection" {
                      when
                        parcels.weightKg > 5
                      then
                        label "x"
                    }
                """.trimIndent()
            )
        }
        assertEquals(expected = "crosses-collection", actual = failure.ruleId)
    }

    @Test
    fun `operator level failure names the rule`() {
        // TextRegexOperator rejects the invalid pattern; the id has to survive the hop into the operator.
        val failure = assertFailsWith<CompilationException> {
            compile(
                rule = """
                    rule "bad-regex" {
                      when
                        iban regex "([A-Z"
                      then
                        label "x"
                    }
                """.trimIndent()
            )
        }
        assertEquals(expected = "bad-regex", actual = failure.ruleId)
    }

    @Test
    fun `wrong literal for a boolean-only operator names the rule`() {
        val failure = assertFailsWith<CompilationException> {
            compile(
                rule = """
                    rule "bad-string-set" {
                      when
                        tags containsAny 42
                      then
                        label "x"
                    }
                """.trimIndent()
            )
        }
        assertEquals(expected = "bad-string-set", actual = failure.ruleId)
    }

    @Test
    fun `extraction on an unknown field names the rule`() {
        val failure = assertFailsWith<CompilationException> {
            compile(
                rule = """
                    rule "bad-extraction-field" {
                      when
                        iban equals "DE00"
                      then
                        extract ibna regex("(\d+)", 1) label ${'$'}1
                    }
                """.trimIndent()
            )
        }
        assertEquals(expected = "bad-extraction-field", actual = failure.ruleId)
    }

    @Test
    fun `invalid extraction pattern names the rule`() {
        val failure = assertFailsWith<CompilationException> {
            compile(
                rule = """
                    rule "bad-extraction-pattern" {
                      when
                        iban equals "DE00"
                      then
                        extract iban regex("([A-Z", 1) label ${'$'}1
                    }
                """.trimIndent()
            )
        }
        assertEquals(expected = "bad-extraction-pattern", actual = failure.ruleId)
    }

    @Test
    fun `unknown aggregate function names the rule`() {
        // 'median' is a real AggregateFunctionName; 'stddev' is not.
        val failure = assertFailsWith<CompilationException> {
            compile(
                rule = """
                    rule "unknown-function" {
                      when
                        stddev(parcels.weightKg) > 5
                      then
                        label "x"
                    }
                """.trimIndent()
            )
        }
        assertEquals(expected = "unknown-function", actual = failure.ruleId)
    }

    // --- ignoreCase in a filter segment is rejected instead of silently dropped ---
    //
    // Only a named operator reaches compileFilterCondition: 'status == "paid"' parses as a
    // ComparisonExpressionAst, while 'status equals "paid"' parses as a legacy ConditionAst, which is the
    // node that carries the ignoreCase modifier. ('status == "paid" ignoreCase' does not parse at all.)

    @Test
    fun `ignoreCase inside a filter segment is rejected`() {
        val failure = assertFailsWith<CompilationException> {
            compile(
                rule = """
                    rule "filter-ignore-case" {
                      when
                        count(parcels[status equals "paid" ignoreCase]) > 0
                      then
                        label "x"
                    }
                """.trimIndent()
            )
        }
        assertEquals(expected = "filter-ignore-case", actual = failure.ruleId)
        assertTrue(
            actual = failure.message?.contains("ignoreCase") == true,
            message = "Expected the message to name the unsupported modifier, got: ${failure.message}"
        )
    }

    /**
     * A filter comparison must compile under every spelling of its operator.
     *
     * `compileFilterCondition` used to match the raw symbols (`==`, `>`, `greater_than`) even though it
     * branches on the output of `OperatorUtils.normalizeOperator`, which canonicalises `==`/`=`/`eq` to
     * `equals` and `>` to `gt`. Only the four relational aliases happened to line up, so every equality
     * filter written with a named operator failed with "Operator 'equals' is not supported".
     */
    @Test
    fun `filter segment compiles for every spelling of a supported operator`() {
        val filters = listOf(
            """status equals "paid"""",
            """status eq "paid"""",
            """status = "paid"""",
            "weightKg > 5",
            "weightKg gt 5",
            "weightKg >= 5",
            "weightKg gte 5",
            "weightKg < 5",
            "weightKg lt 5",
            "weightKg <= 5",
            "weightKg lte 5"
        )
        for ((index, filter) in filters.withIndex()) {
            compile(
                rule = """
                    rule "filter-$index" {
                      when
                        count(parcels[$filter]) > 0
                      then
                        label "x"
                    }
                """.trimIndent()
            )
        }
    }

    /**
     * `startsWith` has no [ruleengine.dsl.ast.ComparisonOperatorAst] to compile to, so a filter cannot
     * carry it. `contains` deliberately is not the example any more: it has one, and a filter using it
     * compiles and matches — see `LegacyFilterPredicateTest`.
     *
     * `Validator` reports this as a diagnostic too, which is how an author normally meets it; the throw
     * is the backstop for compiling without validating first.
     */
    @Test
    fun `unsupported operator in a filter segment names the rule`() {
        val failure = assertFailsWith<CompilationException> {
            compile(
                rule = """
                    rule "filter-bad-operator" {
                      when
                        count(parcels[status startsWith "pai"]) > 0
                      then
                        label "x"
                    }
                """.trimIndent()
            )
        }
        assertEquals(expected = "filter-bad-operator", actual = failure.ruleId)
    }

    // --- action arguments keep their values ---

    @Test
    fun `list argument keeps every element as a value`() {
        val arguments = compileSingleAction(
            rule = """
                rule "list-arg" {
                  when
                    iban equals "DE00"
                  then
                    label ["a", 1, ["b", 2]]
                }
            """.trimIndent()
        )
        val static = assertIs<CompiledActionArgument.Static>(value = arguments.single())
        assertEquals(
            expected = listOf("a", "1", listOf("b", "2")),
            actual = static.value,
            message = "A non-string list element must stay a value, not become the AST node's toString()"
        )
    }

    @Test
    fun `boolean argument survives compilation`() {
        val arguments = compileSingleAction(
            rule = """
                rule "boolean-arg" {
                  when
                    iban equals "DE00"
                  then
                    extract iban regex("(\d+)", 1) label true
                }
            """.trimIndent()
        )
        val static = assertIs<CompiledActionArgument.Static>(value = arguments.single())
        assertEquals(expected = true, actual = static.value)
    }

    @Test
    fun `extraction reference resolves against its extract clause`() {
        val arguments = compileSingleAction(
            rule = """
                rule "extraction-ref" {
                  when
                    iban equals "DE00"
                  then
                    extract iban regex("(\d+)", 1) label ${'$'}1
                }
            """.trimIndent()
        )
        assertIs<CompiledActionArgument.ExtractionRef>(value = arguments.single())
    }
}
