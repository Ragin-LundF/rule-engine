package ruleengine.integration

import ruleengine.builder.LoadedRuleEngine
import ruleengine.builder.RuleEngineBuilder
import ruleengine.core.domain.dto.RuleBranch
import ruleengine.jackson.JacksonUtil
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The `missing-values` bundle: checks E ("events") and F ("fee ratio") of the 1.15.0 spec, run against
 * variations of one base record.
 *
 * Every rule declares `not_exists`, so an undecided condition is told apart from a decided `FAIL`.
 * Entries: `lenient` (orEmpty / isPresent), `strict` (the same checks without them, i.e. 1.14.0),
 * `optional` (the source marker), `schema-defaults` (`missing: empty` in the schema) and `compat`
 * (`missingCollections: empty` on the manifest entry).
 */
class MissingValuesIntegrationTest {

    private val bundle = Path.of("src/test/resources/missing-values")

    private fun engine(entryId: String): LoadedRuleEngine {
        return RuleEngineBuilder.fromManifestEntry(manifestPath = bundle.resolve("manifest.yaml"), entryId = entryId)
    }

    private val lenient = engine(entryId = "lenient")
    private val strict = engine(entryId = "strict")

    private val pass = RuleBranch.THEN to "PASS"
    private val fail = RuleBranch.ELSE to "FAIL"
    private val undecided = RuleBranch.NOT_EXISTS to "MISSING"

    @Test
    fun `every entry loads without warnings`() {
        listOf("lenient", "strict", "optional", "schema-defaults", "compat").forEach { entryId ->
            val warnings = engine(entryId = entryId).warnings
            assertTrue(actual = warnings.isEmpty(), message = "$entryId: $warnings")
        }
    }

    /** T1. */
    @Test
    fun `the base record passes both checks either way`() {
        listOf(lenient, strict).forEach { engine ->
            assertEquals(expected = mapOf("events" to pass, "fee-ratio" to pass), actual = outcomes(engine = engine))
        }
    }

    /** T2, T3: a removed or null list means "no events" with orEmpty, and is undecided without it. */
    @Test
    fun `missing unclassified events pass with orEmpty and stay undecided without it`() {
        listOf(base().without("events", "unclassified"), base().with("events", "unclassified", value = null))
            .forEach { input ->
                assertEquals(expected = pass, actual = outcomes(engine = lenient, input = input)["events"])
                assertEquals(expected = undecided, actual = outcomes(engine = strict, input = input)["events"])
            }
    }

    /** T4: with confirmed removed only P2 has a total, so the check fails — decided, not undecided. */
    @Test
    fun `missing confirmed events fail as a decided result`() {
        val input = base().without("events", "confirmed")

        assertEquals(expected = fail, actual = outcomes(engine = lenient, input = input)["events"])
        assertEquals(expected = undecided, actual = outcomes(engine = strict, input = input)["events"])
    }

    /** T5, T7: no keys at all, whether the lists or the whole object are missing. */
    @Test
    fun `no event lists at all fail with orEmpty`() {
        val emptyEvents = base().with("events", value = emptyMap<String, Any?>())
        val noEvents = base().without("events")

        listOf(emptyEvents, noEvents).forEach { input ->
            assertEquals(expected = fail, actual = outcomes(engine = lenient, input = input)["events"])
            assertEquals(expected = undecided, actual = outcomes(engine = strict, input = input)["events"])
        }
    }

    /** T6: an empty list is data either way. */
    @Test
    fun `an empty unclassified list passes either way`() {
        val input = base().with("events", "unclassified", value = emptyList<Any?>())

        assertEquals(expected = pass, actual = outcomes(engine = lenient, input = input)["events"])
        assertEquals(expected = pass, actual = outcomes(engine = strict, input = input)["events"])
    }

    /** T8: missing fee rows mean a fee total of 0. */
    @Test
    fun `missing fee rows pass with orEmpty and stay undecided without it`() {
        val input = base().with("fees", value = emptyMap<String, Any?>())

        assertEquals(expected = pass, actual = outcomes(engine = lenient, input = input)["fee-ratio"])
        assertEquals(expected = undecided, actual = outcomes(engine = strict, input = input)["fee-ratio"])
    }

    /** T9: a missing fees object stays a failure — the parent is checked strictly with isPresent. */
    @Test
    fun `a missing fees object fails closed`() {
        val input = base().without("fees")

        assertEquals(expected = fail, actual = outcomes(engine = lenient, input = input)["fee-ratio"])
        assertEquals(expected = undecided, actual = outcomes(engine = strict, input = input)["fee-ratio"])
    }

    /** T10, T11: an extra row with a null or unreadable amount is skipped, as aggregates always did. */
    @Test
    fun `an extra fee row without a readable amount is skipped`() {
        listOf(null, "abc").forEach { amount ->
            val input = base().with("fees", "rows", value = feeRows() + mapOf("period" to "P1", "amount" to amount))

            assertEquals(expected = pass, actual = outcomes(engine = lenient, input = input)["fee-ratio"])
            assertEquals(expected = pass, actual = outcomes(engine = strict, input = input)["fee-ratio"])
        }
    }

    /** T23: `optional` on one source; the other source stays strict. */
    @Test
    fun `an optional source may be missing while the other one may not`() {
        val optional = engine(entryId = "optional")

        assertEquals(
            expected = pass,
            actual = outcomes(engine = optional, input = base().without("events", "unclassified"))["events"]
        )
        assertEquals(
            expected = undecided,
            actual = outcomes(engine = optional, input = base().without("events", "confirmed"))["events"]
        )
    }

    /** T22: `missing: empty` on events.unclassified lets the plain strict rule pass. */
    @Test
    fun `a schema default lets a strict rule read a missing list as empty`() {
        val defaults = engine(entryId = "schema-defaults")
        val input = base().without("events", "unclassified")

        assertEquals(expected = pass, actual = outcomes(engine = defaults, input = input)["events"])
    }

    /**
     * T24: `fees` reads as `{}` whether it is absent or `null` — present, not missing — but its `rows`
     * declare no default and stay missing, so the fee check is undecided.
     */
    @Test
    fun `a schema default on an object makes it present but does not reach members without one`() {
        val defaults = engine(entryId = "schema-defaults")

        listOf(base().without("fees"), base().with("fees", value = null)).forEach { input ->
            val outcomes = outcomes(engine = defaults, input = input)

            assertEquals(expected = pass, actual = outcomes["fees-present"])
            assertEquals(expected = fail, actual = outcomes["fees-missing"])
            assertEquals(expected = undecided, actual = outcomes["fee-ratio"])
        }
    }

    /** A null list reads like an absent one under a schema default. */
    @Test
    fun `a schema default treats a null list like an absent one`() {
        val defaults = engine(entryId = "schema-defaults")

        assertEquals(
            expected = pass,
            actual = outcomes(
                engine = defaults,
                input = base().with("events", "unclassified", value = null),
            )["events"],
        )
    }

    /** A string_set root declared `missing: empty` is an empty set: the guard decides instead of staying open. */
    @Test
    fun `a schema default on a string set root decides the periods guard`() {
        val input = base().without("periods")

        val defaults = engine(entryId = "schema-defaults")

        assertEquals(expected = fail, actual = outcomes(engine = defaults, input = input)["events"])
        assertEquals(expected = undecided, actual = outcomes(engine = strict, input = input)["events"])
    }

    /**
     * T25: the 1.13 reading for nested collections — `fees: {}` and a removed `events.unclassified`
     * read as empty, but a removed root object still leaves the rule undecided.
     */
    @Test
    fun `missingCollections empty reads nested lists as empty but not a missing root`() {
        val compat = engine(entryId = "compat")
        val nestedMissing = base().with("fees", value = emptyMap<String, Any?>()).without("events", "unclassified")

        assertEquals(
            expected = mapOf("events" to pass, "fee-ratio" to pass),
            actual = outcomes(engine = compat, input = nestedMissing)
        )
        assertEquals(
            expected = undecided,
            actual = outcomes(engine = compat, input = base().without("events"))["events"],
        )
    }

    /** T20: without the setting the same entry is strict, so the setting is what changes the result. */
    @Test
    fun `without missingCollections the same rules stay undecided`() {
        val nestedMissing = base().with("fees", value = emptyMap<String, Any?>()).without("events", "unclassified")

        assertEquals(
            expected = mapOf("events" to undecided, "fee-ratio" to undecided),
            actual = outcomes(engine = strict, input = nestedMissing)
        )
    }

    private fun outcomes(
        engine: LoadedRuleEngine,
        input: Map<String, Any?> = base(),
    ): Map<String, Pair<RuleBranch, Any?>> {
        return engine.evaluate(input = input).matches.associate { match ->
            match.ruleId to (match.branch to match.actions.single().arguments.single())
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun base(): Map<String, Any?> {
        return JacksonUtil.jsonMapper.readValue(bundle.resolve("base.json").readText(), Map::class.java)
            as Map<String, Any?>
    }

    @Suppress("UNCHECKED_CAST")
    private fun feeRows(): List<Map<String, Any?>> {
        return (base()["fees"] as Map<String, Any?>)["rows"] as List<Map<String, Any?>>
    }

    /** A copy with the value at [path] removed; the input itself is never changed. */
    private fun Map<String, Any?>.without(vararg path: String): Map<String, Any?> {
        return edited(path = path.toList()) { map, key -> map.remove(key) }
    }

    /** A copy with [value] at [path]. */
    private fun Map<String, Any?>.with(vararg path: String, value: Any?): Map<String, Any?> {
        return edited(path = path.toList()) { map, key -> map[key] = value }
    }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.edited(
        path: List<String>,
        change: (MutableMap<String, Any?>, String) -> Unit
    ): Map<String, Any?> {
        val copy = LinkedHashMap(this)
        if (path.size == 1) {
            change(copy, path.single())
            return copy
        }
        val child = copy[path.first()] as Map<String, Any?>
        copy[path.first()] = child.edited(path = path.drop(n = 1), change = change)
        return copy
    }
}
