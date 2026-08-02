package ruleengine.evaluator.compiled

import ruleengine.compiler.Compiler
import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.dsl.parser.Parser
import ruleengine.evaluator.RuleEngine
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.RuleContext
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins down which values a memoized value expression may be reused for.
 *
 * [EvaluationCache] is keyed by the compiled node alone, so the only thing separating one element's
 * aggregate from another's is that each context owns its cache. Both tests below match on a rule
 * whose result differs per element, which is exactly what a shared or stale cache would hide.
 */
class EvaluationCacheScopeTest {

    private val schema = FieldSchema(
        name = "cache-schema",
        fields = mapOf(
            FieldId(value = "orders") to FieldDefinition(
                id = FieldId(value = "orders"),
                type = FieldType.COLLECTION,
                fields = mapOf(
                    FieldId(value = "items") to FieldDefinition(
                        id = FieldId(value = "items"),
                        type = FieldType.COLLECTION
                    )
                )
            )
        )
    )

    private val rule = """
        rule "busy-orders" {
          when
            count(orders[count(items) > 2].items) > 0
          then
            flag "busy"
        }
    """.trimIndent()

    /**
     * The first order fails the predicate and the second passes. With one cache shared across
     * elements the second order would be answered with the first order's item count and the rule
     * would never match.
     */
    @Test
    fun `an aggregate inside a filter is recomputed for every element`() {
        val orders = listOf(
            mapOf("items" to listOf(1)),
            mapOf("items" to listOf(1, 2, 3))
        )

        assertEquals(
            expected = 1,
            actual = evaluate(orders = orders).matches.size,
            message = "the second order has three items and must match on its own count"
        )
    }

    /** And the reverse order, so the test cannot pass by accident on element ordering. */
    @Test
    fun `an aggregate inside a filter does not leak the first element's value`() {
        val orders = listOf(
            mapOf("items" to listOf(1, 2, 3)),
            mapOf("items" to listOf(1))
        )

        assertEquals(
            expected = 1,
            actual = evaluate(orders = orders).matches.size,
            message = "only the first order qualifies, so the match count stays one"
        )
    }

    /**
     * A context is documented as reusable, so a second run must recompute rather than answer from
     * what the first run memoized.
     */
    @Test
    fun `re-evaluating the same context recomputes cached values`() {
        val engine = RuleEngine(compiledRules = compile())
        val prepared = PreparedRuleContext.prepare(
            ctx = RuleContext.of("orders" to listOf(mapOf("items" to listOf(1, 2, 3)))),
            schema = schema
        )

        val first = engine.evaluate(prepared = prepared)
        val second = engine.evaluate(prepared = prepared)

        assertEquals(
            expected = first.matches.map { match -> match.ruleId },
            actual = second.matches.map { match -> match.ruleId },
            message = "the same record evaluated twice must produce the same matches"
        )
    }

    @Test
    fun `a collection whose elements all fail the predicate does not match`() {
        assertEquals(
            expected = 0,
            actual = evaluate(orders = listOf(mapOf("items" to listOf(1)))).matches.size,
            message = "no order has more than two items"
        )
    }

    private fun evaluate(orders: List<Map<String, Any?>>) = RuleEngine(compiledRules = compile()).evaluate(
        prepared = PreparedRuleContext.prepare(ctx = RuleContext.of("orders" to orders), schema = schema)
    )

    private fun compile() = Compiler.compileRules(asts = Parser(input = rule).parseRules(), schema = schema)
}
