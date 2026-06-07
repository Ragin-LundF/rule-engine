package ruleengine.compiler

import ruleengine.core.domain.FieldDefinition
import ruleengine.core.domain.FieldId
import ruleengine.core.domain.FieldSchema
import ruleengine.core.domain.FieldType
import ruleengine.core.domain.OperatorId
import ruleengine.core.normalizer.NormalizerRegistry
import ruleengine.dsl.parser.Parser
import ruleengine.evaluator.RuleEngine
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.RuleContext
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tests for the `between` operator (inclusive range check: low <= field <= high).
 * Supported on DECIMAL and INTEGER field types.
 */
class BetweenOperatorTest {

    private val schema = FieldSchema(
        name = "test",
        fields = mapOf(
            FieldId(value = "amount") to FieldDefinition(
                id = FieldId(value = "amount"), type = FieldType.DECIMAL,
                normalizers = emptyList(),
                operators = setOf(OperatorId(value = "between"), OperatorId(value = "gt"), OperatorId(value = "lt"))
            ),
            FieldId(value = "count") to FieldDefinition(
                id = FieldId(value = "count"), type = FieldType.INTEGER,
                normalizers = emptyList(),
                operators = setOf(OperatorId(value = "between"), OperatorId(value = "equals"))
            )
        )
    )

    private fun engine(ruleText: String): RuleEngine {
        val asts = Parser(input = ruleText).parseRules()
        val validation = Validator.validate(asts, schema)
        assertTrue(actual = validation.isValid, message = "Validation failed: ${validation.diagnostics}")
        val compiled = Compiler.compileRules(asts, schema, NormalizerRegistry.default)
        return RuleEngine(compiledRules = compiled, schema = schema)
    }

    private fun ctx(amount: String? = null, count: Int? = null): PreparedRuleContext {
        val pairs = buildList {
            if (amount != null) add("amount" to amount)
            if (count != null) add("count" to count)
        }
        return PreparedRuleContext.prepare(ctx = RuleContext.of(*pairs.toTypedArray()), schema = schema)
    }

    // ── Decimal between ───────────────────────────────────────────────────

    @Test
    fun `decimal between - values inside range match`() {
        val e = engine(
            ruleText = """
                        rule "normal-range" {
                          when amount between 100 5000
                          then label "normal"
                        }
                    """.trimIndent()
        )
        assertTrue(actual = e.evaluate(prepared = ctx(amount = "100")).matches.isNotEmpty())    // lower bound inclusive
        assertTrue(actual = e.evaluate(prepared = ctx(amount = "5000")).matches.isNotEmpty())   // upper bound inclusive
        assertTrue(actual = e.evaluate(prepared = ctx(amount = "2500")).matches.isNotEmpty())   // midpoint
        assertTrue(actual = e.evaluate(prepared = ctx(amount = "100.01")).matches.isNotEmpty()) // just above lower
    }

    @Test
    fun `decimal between - values outside range do not match`() {
        val e = engine(
            ruleText = """
                        rule "normal-range" {
                          when amount between 100 5000
                          then label "normal"
                        }
                    """.trimIndent()
        )
        assertTrue(actual = e.evaluate(prepared = ctx(amount = "99.99")).matches.isEmpty())   // just below lower
        assertTrue(actual = e.evaluate(prepared = ctx(amount = "5000.01")).matches.isEmpty()) // just above upper
        assertTrue(actual = e.evaluate(prepared = ctx(amount = "-100")).matches.isEmpty())    // negative
    }

    @Test
    fun `decimal between - negative range works`() {
        val e = engine(
            ruleText = """
                        rule "small-negative" {
                          when amount between -5 -1
                          then label "small-neg"
                        }
                    """.trimIndent()
        )
        assertTrue(actual = e.evaluate(prepared = ctx(amount = "-5")).matches.isNotEmpty())
        assertTrue(actual = e.evaluate(prepared = ctx(amount = "-1")).matches.isNotEmpty())
        assertTrue(actual = e.evaluate(prepared = ctx(amount = "-3")).matches.isNotEmpty())
        assertTrue(actual = e.evaluate(prepared = ctx(amount = "-6")).matches.isEmpty())
        assertTrue(actual = e.evaluate(prepared = ctx(amount = "0")).matches.isEmpty())
    }

    @Test
    fun `decimal between combined with other condition`() {
        val e = engine(
            ruleText = """
                        rule "chargeback-small" {
                          when
                            amount between -500 -1
            
                          then
                            label "small-chargeback"
                        }
                    """.trimIndent()
        )
        assertTrue(actual = e.evaluate(prepared = ctx(amount = "-250")).matches.isNotEmpty())
        assertTrue(actual = e.evaluate(prepared = ctx(amount = "-500")).matches.isNotEmpty())
        assertTrue(actual = e.evaluate(prepared = ctx(amount = "-501")).matches.isEmpty())
        assertTrue(actual = e.evaluate(prepared = ctx(amount = "1")).matches.isEmpty())
    }

    // ── Integer between ───────────────────────────────────────────────────

    @Test
    fun `integer between - values inside range match`() {
        val e = engine(
            ruleText = """
                        rule "structuring" {
                          when count between 5 20
                          then flag "structuring"
                        }
                    """.trimIndent()
        )
        assertTrue(actual = e.evaluate(prepared = ctx(count = 5)).matches.isNotEmpty())   // lower bound inclusive
        assertTrue(actual = e.evaluate(prepared = ctx(count = 20)).matches.isNotEmpty())  // upper bound inclusive
        assertTrue(actual = e.evaluate(prepared = ctx(count = 12)).matches.isNotEmpty())  // midpoint
    }

    @Test
    fun `integer between - values outside range do not match`() {
        val e = engine(
            """
            rule "structuring" {
              when count between 5 20
              then flag "structuring"
            }
        """.trimIndent()
        )
        assertTrue(actual = e.evaluate(prepared = ctx(count = 4)).matches.isEmpty())
        assertTrue(actual = e.evaluate(prepared = ctx(count = 21)).matches.isEmpty())
        assertTrue(actual = e.evaluate(prepared = ctx(count = 0)).matches.isEmpty())
    }

    @Test
    fun `integer between combined with decimal between`() {
        val e = engine(
            ruleText = """
                        rule "suspicious" {
                          when
                            count between 5 20
                            and amount between 8000 9999
            
                          then
                            flag "aml-structuring"
                        }
                    """.trimIndent()
        )
        // Both conditions satisfied
        assertTrue(actual = e.evaluate(prepared = ctx(amount = "9500", count = 12)).matches.isNotEmpty())
        // count outside range
        assertTrue(actual = e.evaluate(prepared = ctx(amount = "9500", count = 3)).matches.isEmpty())
        // amount outside range
        assertTrue(actual = e.evaluate(prepared = ctx(amount = "10000", count = 12)).matches.isEmpty())
    }
}

