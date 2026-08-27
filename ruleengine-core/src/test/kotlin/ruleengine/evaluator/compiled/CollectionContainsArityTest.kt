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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `contains` over a collection-valued path means membership however many elements a record carries.
 *
 * `FieldAccessCompiledValueExpression.collapse` reduces a selection of exactly one element to a
 * scalar, so before the path declared its own shape the same condition was a membership test for a
 * record with two matching elements and a *substring* test for a record with one — `"prem"` matching
 * `"premium"` only in the second. The compiled path now carries `yieldsCollection`, and `contains`
 * reads that instead of the runtime type.
 *
 * A bare projection such as `orders.tag contains "x"` cannot express this: `contains` keeps the
 * legacy named-operator path deliberately, and that path rejects reading through a collection. The
 * reachable forms are the ones carrying a filter, a slice or a sort, which is where the bug lived.
 */
class CollectionContainsArityTest {

    private val schema = FieldSchema(
        name = "contains-arity-schema",
        fields = mapOf(
            FieldId(value = "orders") to FieldDefinition(
                id = FieldId(value = "orders"),
                type = FieldType.COLLECTION,
                fields = mapOf(
                    FieldId(value = "tag") to FieldDefinition(
                        id = FieldId(value = "tag"),
                        type = FieldType.TEXT
                    ),
                    FieldId(value = "total") to FieldDefinition(
                        id = FieldId(value = "total"),
                        type = FieldType.DECIMAL
                    )
                )
            ),
            FieldId(value = "purpose") to FieldDefinition(
                id = FieldId(value = "purpose"),
                type = FieldType.TEXT
            )
        )
    )

    private val onePremium = arrayOf<Pair<String, Any?>>(
        "orders" to listOf(mapOf("tag" to "premium", "total" to 10))
    )
    private val twoTags = arrayOf<Pair<String, Any?>>(
        "orders" to listOf(mapOf("tag" to "premium", "total" to 10), mapOf("tag" to "standard", "total" to 20))
    )

    // ── the regression: one selected element must not become a substring test ─

    @Test
    fun `a filter selecting one element is a membership test`() {
        assertFalse(
            actual = evaluate(condition = """orders[total > 0].tag contains "prem"""", fields = onePremium),
            message = "one selected element still means membership: \"prem\" is a substring, not a member"
        )
    }

    @Test
    fun `a filter selecting several elements is a membership test`() {
        assertFalse(
            actual = evaluate(condition = """orders[total > 0].tag contains "prem"""", fields = twoTags),
            message = "the multi-element case has always been membership and must not change"
        )
    }

    @Test
    fun `a filter narrowing many elements down to one is a membership test`() {
        assertFalse(
            actual = evaluate(condition = """orders[total < 15].tag contains "prem"""", fields = twoTags),
            message = "how many elements survive the filter must not decide what the operator means"
        )
    }

    @Test
    fun `a slice selecting one element is a membership test`() {
        assertFalse(
            actual = evaluate(condition = """take(orders, 1).tag contains "prem"""", fields = twoTags),
            message = "a slice is collection-valued for the same reason a filter is"
        )
    }

    // ── membership still matches what the collection actually holds ───────────

    @Test
    fun `a filter selecting one element matches an exact member`() {
        assertTrue(
            actual = evaluate(condition = """orders[total > 0].tag contains "premium"""", fields = onePremium),
            message = "an exact member matches whether one element survives the filter or many"
        )
    }

    @Test
    fun `a filter selecting several elements matches an exact member`() {
        assertTrue(
            actual = evaluate(condition = """orders[total > 0].tag contains "standard"""", fields = twoTags),
            message = "membership over several elements keeps working"
        )
    }

    // ── plain text keeps the substring reading ────────────────────────────────

    @Test
    fun `a plain text field still means substring`() {
        assertTrue(
            actual = evaluate(
                condition = """purpose contains "rent"""",
                fields = arrayOf("purpose" to "monthly rent payment")
            ),
            message = "`contains` on a text field is a substring test and must stay one"
        )
    }

    @Test
    fun `a plain text field does not match an absent substring`() {
        assertFalse(
            actual = evaluate(
                condition = """purpose contains "rent"""",
                fields = arrayOf("purpose" to "monthly salary")
            ),
            message = "the negative substring path must stay covered"
        )
    }

    private fun evaluate(condition: String, fields: Array<Pair<String, Any?>>): Boolean {
        val rule = """
            rule "contains-arity-test" {
              when
                $condition
              then
                flag "ok"
            }
        """.trimIndent()
        val compiled = Compiler.compileRules(asts = Parser(input = rule).parseRules(), schema = schema)
        val prepared = PreparedRuleContext.prepare(ctx = RuleContext.of(*fields), schema = schema)
        return RuleEngine(compiledRules = compiled).evaluate(prepared = prepared).matches.isNotEmpty()
    }
}
