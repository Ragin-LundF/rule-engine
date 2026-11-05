package ruleengine.integration

import ruleengine.builder.RuleEngineBuilder
import ruleengine.core.domain.dto.RuleBranch
import ruleengine.jackson.JacksonUtil
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The `period-balance` bundle: a whole per-key check across two collections in one rule, loaded
 * from a real manifest and run against the re-req's examples.
 */
class PeriodBalanceIntegrationTest {

    private val bundle = Path.of("src/test/resources/period-balance")

    private val engine = RuleEngineBuilder.fromManifestEntry(
        manifestPath = bundle.resolve("manifest.yaml"),
        entryId = "period-balance"
    )

    @Test
    fun `the bundle loads without warnings`() {
        assertTrue(actual = engine.warnings.isEmpty(), message = "got: ${engine.warnings}")
    }

    /** E1: two of three listed periods positive, P4 joined but not listed. */
    @Test
    fun `two of three positive periods pass`() {
        assertEquals(expected = RuleBranch.THEN to "PASS", actual = outcomeFor(input = "e1-basic.json"))
    }

    /** E8: no listed periods, so the guard is decided false. */
    @Test
    fun `no periods fail through else`() {
        assertEquals(expected = RuleBranch.ELSE to "FAIL", actual = outcomeFor(input = "e8-no-periods.json"))
    }

    /** E9: without debits the join is missing, so the rule is undecided rather than passing. */
    @Test
    fun `missing debits take the not_exists branch`() {
        assertEquals(
            expected = RuleBranch.NOT_EXISTS to "MISSING",
            actual = outcomeFor(input = "e9-debits-missing.json")
        )
    }

    /** E10: empty debits are valid data — every period has debit 0. */
    @Test
    fun `empty debits pass`() {
        assertEquals(expected = RuleBranch.THEN to "PASS", actual = outcomeFor(input = "e10-debits-empty.json"))
    }

    /** E12: 24 periods, 12 positive — no upper limit on the number of periods. */
    @Test
    fun `twenty-four periods with half positive pass`() {
        assertEquals(expected = RuleBranch.THEN to "PASS", actual = outcomeFor(input = "e12-24-periods.json"))
    }

    private fun outcomeFor(input: String): Pair<RuleBranch, Any?> {
        val match = engine.evaluate(input = readInput(name = input)).matches.single()
        return match.branch to match.actions.single().arguments.single()
    }

    @Suppress("UNCHECKED_CAST")
    private fun readInput(name: String): Map<String, Any?> {
        return JacksonUtil.jsonMapper.readValue(
            bundle.resolve("inputs").resolve(name).readText(),
            Map::class.java
        ) as Map<String, Any?>
    }
}
