package ruleengine.compiler

import ruleengine.core.errors.Severity
import ruleengine.dsl.parser.Parser
import ruleengine.evaluator.RuleEngine
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.RuleContext
import ruleengine.schema.FieldSchemaLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * An `alias:` is a second name for one field, usable at any nesting depth, and the whole chain — validator,
 * compiler, prepared context — has to agree on the canonical path it stands for.
 *
 * These tests assert on evaluated values rather than on "validates clean", because the bugs they cover were
 * silent: an alias deep in a value expression used to compile a segment named after the alias, so it read a
 * map key that does not exist and produced a missing value with no diagnostic at all.
 */
class NestedAliasResolutionTest {

    private val schema = FieldSchemaLoader.loadFromString(
        content = """
            schema: nested-alias-v1

            fields:
              reports:
                type: object
                fields:
                  income:
                    type: object
                    fields:
                      daysOfReport:
                        type: integer
                        alias: TRANSACTION_HISTORY_DAYS
                      accountData:
                        type: collection
                        alias: ACCOUNTS
                        fields:
                          accountType:
                            type: text
                            alias: ACCOUNT_TYPE
              orders:
                type: collection
                alias: purchases
                fields:
                  total:
                    type: decimal
                    alias: orderTotal
        """.trimIndent()
    )

    private val input: Map<String, Any?> = mapOf(
        "reports" to mapOf(
            "income" to mapOf(
                "daysOfReport" to 90,
                "accountData" to listOf(
                    mapOf("accountType" to "CHECKING"),
                    mapOf("accountType" to "SAVINGS")
                )
            )
        ),
        "orders" to listOf(
            mapOf("total" to 10),
            mapOf("total" to 20)
        )
    )

    private fun ruleFor(condition: String): String = """
        rule "alias" {
          when
            $condition
          then
            assessment "ok"
        }
    """.trimIndent()

    private fun validate(condition: String): ValidationResult {
        return Validator.validate(asts = Parser(input = ruleFor(condition = condition)).parseRules(), schema = schema)
    }

    /** Runs the full pipeline and reports whether the rule matched. */
    private fun matches(condition: String): Boolean {
        val asts = Parser(input = ruleFor(condition = condition)).parseRules()
        val result = Validator.validate(asts = asts, schema = schema)
        assertTrue(
            actual = result.isValid,
            message = "'$condition' should validate, got: ${result.diagnostics}"
        )
        val engine = RuleEngine(compiledRules = Compiler.compileRules(asts = asts, schema = schema))
        val context = RuleContext.of(entries = input.entries.map { it.key to it.value }.toTypedArray())
        val prepared = PreparedRuleContext.prepare(ctx = context, schema = schema)
        return engine.evaluate(prepared = prepared).matches.isNotEmpty()
    }

    private fun errors(condition: String) =
        validate(condition = condition).diagnostics.filter { it.severity == Severity.ERROR }

    // --- a bare alias in a plain condition ---

    @Test
    fun `bare alias of an object-nested scalar evaluates like its declared path`() {
        assertTrue(actual = matches(condition = "TRANSACTION_HISTORY_DAYS >= 85"), message = "90 >= 85")
        assertFalse(actual = matches(condition = "TRANSACTION_HISTORY_DAYS >= 95"), message = "90 >= 95")
        assertEquals(
            expected = matches(condition = "reports.income.daysOfReport >= 85"),
            actual = matches(condition = "TRANSACTION_HISTORY_DAYS >= 85"),
            message = "Both spellings name the same field, so they must evaluate identically"
        )
    }

    @Test
    fun `alias in the position of the segment it renames still resolves`() {
        assertTrue(actual = matches(condition = "reports.income.TRANSACTION_HISTORY_DAYS >= 85"))
    }

    @Test
    fun `bare alias of a collection member is one error naming the collection`() {
        val errors = errors(condition = """ACCOUNT_TYPE equals "CHECKING"""")
        assertEquals(expected = 1, actual = errors.size, message = "Expected one error, got: $errors")
        assertTrue(
            actual = errors.first().message.contains("collection 'reports.income.accountData'"),
            message = "The error must name the collection the alias reads through, got: ${errors.first().message}"
        )
    }

    // --- an alias inside a filter predicate ---

    @Test
    fun `alias in a filter predicate matches the element it describes`() {
        assertTrue(
            actual = matches(condition = """count(reports.income.accountData[ACCOUNT_TYPE == "CHECKING"]) == 1"""),
            message = "'ACCOUNT_TYPE' is the alias of the declared member 'accountType'"
        )
        assertTrue(
            actual = matches(condition = """count(ACCOUNTS[ACCOUNT_TYPE == "CHECKING"]) == 1"""),
            message = "The collection may be reached by its own alias too"
        )
    }

    @Test
    fun `alias in a legacy filter predicate matches the element it describes`() {
        assertTrue(actual = matches(condition = "count(orders[orderTotal > 15]) == 1"))
    }

    // --- an alias inside an aggregate projection ---

    @Test
    fun `alias projected by an aggregate sums the declared member`() {
        assertTrue(actual = matches(condition = "sum(orders.orderTotal) == 30"), message = "10 + 20")
        assertFalse(
            actual = matches(condition = "sum(orders.orderTotal) == 0"),
            message = "A sum of 0 would mean the projection read a key that does not exist"
        )
    }

    @Test
    fun `alias of a collection is countable as the root of a path`() {
        assertTrue(actual = matches(condition = "count(purchases) == 2"), message = "'purchases' aliases 'orders'")
        assertTrue(
            actual = matches(condition = "count(ACCOUNTS) == 2"),
            message = "'ACCOUNTS' aliases the nested collection 'reports.income.accountData'"
        )
    }

    @Test
    fun `alias of a nested collection projects its members`() {
        assertTrue(actual = matches(condition = """count(ACCOUNTS[accountType == "SAVINGS"]) == 1"""))
    }
}
