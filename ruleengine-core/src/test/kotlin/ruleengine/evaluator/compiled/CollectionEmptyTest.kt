package ruleengine.evaluator.compiled

import ruleengine.compiler.Compiler
import ruleengine.compiler.Validator
import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.core.errors.Severity
import ruleengine.dsl.parser.Parser
import ruleengine.evaluator.RuleEngine
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.RuleContext
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `isEmpty` tells a collection that arrived empty from one that did not arrive.
 *
 * `isAvailable` deliberately answers `false` to both — "does the record carry this at all" has one
 * answer for an absent collection and an empty one. `isEmpty` is the third answer, so the two together
 * name all three states.
 *
 * `count(x) == 0` reaches the same conclusion for a collection the record *carries*, but it is
 * undecided for one it does not — an aggregate propagates a missing value, while these two consume it.
 * That is what makes only these two usable as a guard.
 */
class CollectionEmptyTest {

    private val schema = FieldSchema(
        name = "empty-schema",
        fields = mapOf(
            FieldId(value = "orders") to FieldDefinition(
                id = FieldId(value = "orders"),
                type = FieldType.COLLECTION,
                fields = mapOf(
                    FieldId(value = "total") to FieldDefinition(
                        id = FieldId(value = "total"),
                        type = FieldType.DECIMAL
                    )
                )
            ),
            FieldId(value = "tags") to FieldDefinition(
                id = FieldId(value = "tags"),
                type = FieldType.STRING_SET
            )
        )
    )

    // ── the three states of a collection ──────────────────────────────────────

    @Test
    fun `a collection that arrived empty is empty`() {
        assertTrue(
            actual = evaluate(condition = "isEmpty(orders)", fields = arrayOf("orders" to emptyList<Any>())),
            message = "present and holding no elements is exactly what isEmpty answers true to"
        )
    }

    @Test
    fun `a collection that did not arrive is not empty`() {
        assertFalse(
            actual = evaluate(condition = "isEmpty(orders)", fields = arrayOf("other" to 1)),
            message = "an absent collection is not an empty one — that is the whole distinction"
        )
    }

    @Test
    fun `a collection holding elements is not empty`() {
        assertFalse(
            actual = evaluate(
                condition = "isEmpty(orders)",
                fields = arrayOf("orders" to listOf(mapOf("total" to 10)))
            ),
            message = "elements present means not empty"
        )
    }

    @Test
    fun `a null collection is not empty`() {
        assertFalse(
            actual = evaluate(condition = "isEmpty(orders)", fields = arrayOf("orders" to null)),
            message = "null is absent, and absent is not empty"
        )
    }

    // ── isAvailable and isEmpty together name all three states ────────────────

    @Test
    fun `isAvailable and isEmpty disagree only on the empty case`() {
        val empty = arrayOf<Pair<String, Any?>>("orders" to emptyList<Any>())
        val absent = arrayOf<Pair<String, Any?>>("other" to 1)

        assertFalse(
            actual = evaluate(condition = "isAvailable(orders)", fields = empty),
            message = "isAvailable stays false for an empty collection — that behaviour is unchanged"
        )
        assertTrue(
            actual = evaluate(condition = "isEmpty(orders)", fields = empty),
            message = "isEmpty is what separates the empty case from the absent one"
        )
        assertFalse(
            actual = evaluate(condition = "isAvailable(orders)", fields = absent),
            message = "both answer false for an absent collection"
        )
        assertFalse(
            actual = evaluate(condition = "isEmpty(orders)", fields = absent),
            message = "both answer false for an absent collection"
        )
    }

    /**
     * `count(x) == 0` now answers the same question `isEmpty(x)` does, because `count` no longer
     * invents a `0` for a collection the record does not carry.
     *
     * `isEmpty` is still the clearer spelling, and the only one that says *decided* rather than
     * *undecided* for an absent collection — which is what lets it guard a rule.
     */
    @Test
    fun `count agrees with isEmpty on a present collection`() {
        assertTrue(
            actual = evaluate(
                condition = "count(orders) == 0",
                fields = arrayOf("orders" to emptyList<Any>())
            ),
            message = "count of an empty collection is 0"
        )
        assertFalse(
            actual = evaluate(condition = "count(orders) == 0", fields = arrayOf("other" to 1)),
            message = "an absent collection has no count, so the comparison is undecided, not true"
        )
    }

    // ── decorated and projected paths ─────────────────────────────────────────

    @Test
    fun `a filter selecting nothing is empty`() {
        assertTrue(
            actual = evaluate(
                condition = "isEmpty(orders[total > 100])",
                fields = arrayOf("orders" to listOf(mapOf("total" to 10)))
            ),
            message = "a filter that selects no element leaves an empty collection"
        )
    }

    @Test
    fun `a filter selecting something is not empty`() {
        assertFalse(
            actual = evaluate(
                condition = "isEmpty(orders[total > 100])",
                fields = arrayOf("orders" to listOf(mapOf("total" to 500)))
            ),
            message = "a filter that selects an element leaves a non-empty collection"
        )
    }

    @Test
    fun `a projected path over an empty collection is empty`() {
        assertTrue(
            actual = evaluate(
                condition = "isEmpty(orders.total)",
                fields = arrayOf("orders" to emptyList<Any>())
            ),
            message = "read whole or projected, an empty collection answers the same"
        )
    }

    @Test
    fun `a string set that arrived empty is empty`() {
        assertTrue(
            actual = evaluate(condition = "isEmpty(tags)", fields = arrayOf("tags" to emptyList<String>())),
            message = "a string_set holds many values too, so emptiness means the same thing"
        )
    }

    // ── validation ────────────────────────────────────────────────────────────

    @Test
    fun `isEmpty over something that is not a path is rejected`() {
        val diagnostics = validate(condition = "isEmpty(count(orders))")

        assertTrue(
            actual = diagnostics.any { diagnostic ->
                diagnostic.severity == Severity.ERROR && diagnostic.message.contains(other = "isEmpty()")
            },
            message = "an aggregate has already lost the empty-versus-absent distinction: $diagnostics"
        )
    }

    @Test
    fun `isEmpty over a declared collection validates`() {
        assertTrue(
            actual = validate(condition = "isEmpty(orders)").none { it.severity == Severity.ERROR },
            message = "the ordinary form must produce no error"
        )
    }

    private fun ruleFor(condition: String) = """
        rule "empty-test" {
          description "checks emptiness"
          when
            $condition
          then
            flag "ok"
        }
    """.trimIndent()

    private fun validate(condition: String) =
        Validator.validate(asts = Parser(input = ruleFor(condition = condition)).parseRules(), schema = schema)
            .diagnostics

    private fun evaluate(condition: String, fields: Array<Pair<String, Any?>>): Boolean {
        val compiled = Compiler.compileRules(
            asts = Parser(input = ruleFor(condition = condition)).parseRules(),
            schema = schema,
        )
        val prepared = PreparedRuleContext.prepare(ctx = RuleContext.of(*fields), schema = schema)
        return RuleEngine(compiledRules = compiled).evaluate(prepared = prepared).matches.isNotEmpty()
    }
}
