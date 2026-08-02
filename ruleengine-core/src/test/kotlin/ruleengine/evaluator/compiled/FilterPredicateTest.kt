package ruleengine.evaluator.compiled

import ruleengine.compiler.Compiler
import ruleengine.compiler.Validator
import ruleengine.core.domain.dto.NormalizerId
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
 * What a filter predicate can express, asserted on evaluation rather than on diagnostics.
 *
 * Every case here used to look correct and not work. A predicate written in the legacy
 * `field op literal` form — which is what the parser produces inside `[...]` for every operator but
 * `==` and `!=` — compiled a dotted name into a single segment whose *name* contained a dot, so
 * `[origin.scans > 2]` parsed, validated, compiled, and then silently answered false for every
 * element. A boolean combination validated and then failed to compile.
 *
 * Diagnostics cannot tell the difference between "selects nothing" and "selects nothing because it can
 * never match", which is why these assert on what the engine returns.
 */
class FilterPredicateTest {

    private val schema = FieldSchema(
        name = "parcels-schema",
        fields = mapOf(
            FieldId(value = "parcels") to FieldDefinition(
                id = FieldId(value = "parcels"),
                type = FieldType.COLLECTION,
                fields = mapOf(
                    FieldId(value = "code") to FieldDefinition(
                        id = FieldId(value = "code"),
                        type = FieldType.TEXT,
                        normalizers = listOf(NormalizerId(value = "trim"), NormalizerId(value = "lowercase"))
                    ),
                    FieldId(value = "origin") to FieldDefinition(
                        id = FieldId(value = "origin"),
                        type = FieldType.OBJECT,
                        fields = mapOf(
                            FieldId(value = "hub") to FieldDefinition(
                                id = FieldId(value = "hub"),
                                type = FieldType.TEXT
                            ),
                            FieldId(value = "scans") to FieldDefinition(
                                id = FieldId(value = "scans"),
                                type = FieldType.INTEGER
                            )
                        )
                    )
                )
            )
        )
    )

    private fun parcel(hub: String, scans: Int, code: String = "a") =
        mapOf("code" to code, "origin" to mapOf("hub" to hub, "scans" to scans))

    @Test
    fun `a legacy predicate on a nested member matches the element that satisfies it`() {
        assertTrue(
            actual = evaluate(
                condition = "count(parcels[origin.scans > 2]) > 0",
                parcels = listOf(parcel(hub = "HAM", scans = 5))
            ),
            message = "origin.scans is 5, so the predicate must select the parcel"
        )
    }

    @Test
    fun `a legacy predicate on a nested member rejects the element that does not`() {
        assertFalse(
            actual = evaluate(
                condition = "count(parcels[origin.scans > 2]) > 0",
                parcels = listOf(parcel(hub = "HAM", scans = 1))
            ),
            message = "origin.scans is 1, so nothing must be selected"
        )
    }

    /**
     * The guard against the old failure mode reappearing: if the dotted name were compiled as one
     * segment again, both tests above would pass vacuously as false. This one can only pass when the
     * path is really walked.
     */
    @Test
    fun `a legacy predicate on a nested member discriminates between elements`() {
        assertTrue(
            actual = evaluate(
                condition = "count(parcels[origin.scans > 2]) == 1",
                parcels = listOf(parcel(hub = "HAM", scans = 5), parcel(hub = "BER", scans = 1))
            ),
            message = "exactly one parcel has more than two scans"
        )
    }

    @Test
    fun `contains works in a legacy filter predicate`() {
        assertTrue(
            actual = evaluate(
                condition = """count(parcels[origin.hub contains "AM"]) > 0""",
                parcels = listOf(parcel(hub = "HAM", scans = 1))
            ),
            message = "'HAM' contains 'AM'"
        )
    }

    /** A legacy predicate normalizes its literal against the member's declared normalizers. */
    @Test
    fun `a legacy predicate applies the member's normalizers`() {
        assertTrue(
            actual = evaluate(
                condition = """count(parcels[code contains "ac"]) > 0""",
                parcels = listOf(parcel(hub = "HAM", scans = 1, code = "  ACME  "))
            ),
            message = "trim + lowercase are declared on code, so '  ACME  ' must contain \"ac\""
        )
    }

    // ── boolean combinations ──────────────────────────────────────────────────
    //
    // These used to validate and then fail to compile. The documented workaround — chaining
    // `[a][b]` — only ever expressed `and`, so `or` and `not` had no spelling at all.

    @Test
    fun `and inside a filter requires both halves of the same element`() {
        val parcels = listOf(parcel(hub = "HAM", scans = 5), parcel(hub = "BER", scans = 5))

        assertTrue(
            actual = evaluate(
                condition = """count(parcels[origin.hub == "HAM" and origin.scans > 2]) == 1""",
                parcels = parcels
            ),
            message = "only the HAM parcel satisfies both halves"
        )
    }

    /**
     * The distinction chaining cannot express: `and` selects one element meeting both, `or` selects
     * elements meeting either.
     */
    @Test
    fun `or inside a filter selects elements meeting either half`() {
        val parcels = listOf(parcel(hub = "HAM", scans = 1), parcel(hub = "BER", scans = 5))

        assertTrue(
            actual = evaluate(
                condition = """count(parcels[origin.hub == "HAM" or origin.scans > 2]) == 2""",
                parcels = parcels
            ),
            message = "one parcel matches on hub, the other on scans"
        )
    }

    @Test
    fun `not inside a filter inverts the predicate`() {
        val parcels = listOf(parcel(hub = "HAM", scans = 5), parcel(hub = "BER", scans = 1))

        assertTrue(
            actual = evaluate(
                condition = "count(parcels[not origin.scans > 2]) == 1",
                parcels = parcels
            ),
            message = "exactly one parcel has two scans or fewer"
        )
    }

    /** A member declared with a dotted id keeps being named that way, rather than being split. */
    @Test
    fun `a flat dotted declaration still resolves as one member`() {
        val flatSchema = FieldSchema(
            name = "flat-schema",
            fields = mapOf(
                FieldId(value = "parcels") to FieldDefinition(
                    id = FieldId(value = "parcels"),
                    type = FieldType.COLLECTION,
                    fields = mapOf(
                        FieldId(value = "origin.scans") to FieldDefinition(
                            id = FieldId(value = "origin.scans"),
                            type = FieldType.INTEGER
                        )
                    )
                )
            )
        )

        assertTrue(
            actual = evaluate(
                condition = "count(parcels[origin.scans > 2]) > 0",
                parcels = listOf(mapOf("origin.scans" to 5)),
                schema = flatSchema
            ),
            message = "the element carries a member literally named 'origin.scans'"
        )
    }

    private fun evaluate(
        condition: String,
        parcels: List<Map<String, Any?>>,
        schema: FieldSchema = this.schema,
    ): Boolean {
        val rule = """
            rule "legacy-filter" {
              when
                $condition
              then
                flag "ok"
            }
        """.trimIndent()
        val asts = Parser(input = rule).parseRules()

        // Asserted here so a rule that evaluates correctly but no longer validates cannot pass
        // unnoticed — the two have to agree on which predicates are legal.
        val errors = Validator.validate(asts = asts, schema = schema)
            .diagnostics.filter { it.severity == Severity.ERROR }
        assertTrue(actual = errors.isEmpty(), message = "'$condition' must validate: ${errors.map { it.message }}")

        val compiled = Compiler.compileRules(asts = asts, schema = schema)
        val prepared = PreparedRuleContext.prepare(ctx = RuleContext.of("parcels" to parcels), schema = schema)
        return RuleEngine(compiledRules = compiled).evaluate(prepared = prepared).matches.isNotEmpty()
    }
}
