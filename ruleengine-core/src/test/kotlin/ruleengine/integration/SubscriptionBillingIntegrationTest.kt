package ruleengine.integration

import ruleengine.builder.RuleEngineBuilder
import ruleengine.jackson.JacksonUtil
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The `subscription-billing` sample, run rather than merely displayed.
 *
 * The same bundle ships in the UI gallery. A sample nobody executes drifts from the engine one
 * release at a time, so this evaluates it against a record whose two accounts are chosen to land on
 * opposite sides of every rule.
 */
class SubscriptionBillingIntegrationTest {

    private val bundle = Path.of("src/test/resources/subscription-billing")

    private val engine = RuleEngineBuilder.fromManifestEntry(
        manifestPath = bundle.resolve("manifest.yaml"),
        entryId = "account-review"
    )

    private val result = engine.evaluate(input = readInput())

    @Test
    fun `the sample loads without warnings`() {
        assertTrue(actual = engine.warnings.isEmpty(), message = "got: ${engine.warnings}")
    }

    @Test
    fun `each account is evaluated on its own`() {
        assertEquals(
            expected = listOf("acc-established", "acc-new"),
            actual = result.members.map { member -> member.key }
        )
    }

    /** REQ-01, REQ-04, REQ-06 — the established account is the clean one. */
    @Test
    fun `the established account matches exposure, tenure and the sanity check`() {
        assertEquals(
            expected = listOf("priority-exposure", "established-account", "line-item-sanity"),
            actual = matchedRules(member = "acc-established"),
            message = "12000 owed by priority customers, registered in January, and nothing wrong"
        )
    }

    /** REQ-02, REQ-03, REQ-05, REQ-06 — the new account fails on everything else. */
    @Test
    fun `the new account matches the failure rules instead`() {
        assertEquals(
            expected = listOf("recent-login-failures", "negative-month", "balance-drift", "line-item-sanity"),
            actual = matchedRules(member = "acc-new"),
            message = "three failed logins, a month in the red, 1900 of drift and a zero-quantity item"
        )
    }

    /** The sanity rule fires on both accounts, through opposite branches. */
    @Test
    fun `the sanity check reports which branch each account took`() {
        assertEquals(
            expected = listOf("flag" to "clean"),
            actual = actionsOf(member = "acc-established", ruleId = "line-item-sanity")
        )
        assertEquals(
            expected = listOf("review" to "line-items"),
            actual = actionsOf(member = "acc-new", ruleId = "line-item-sanity")
        )
    }

    /** The membership source is a document field, and its match depends on the declared normalizers. */
    @Test
    fun `a scoped rule reads a normalized document field`() {
        assertTrue(
            actual = matchedRules(member = "acc-established").contains(element = "priority-exposure"),
            message = "the 8000 invoice is written ' ACME ' and only trim + lowercase make it match"
        )
        assertTrue(
            actual = !matchedRules(member = "acc-new").contains(element = "priority-exposure"),
            message = "the new account owes 2000, well short of the threshold"
        )
    }

    @Test
    fun `every match names the account it came from`() {
        assertTrue(
            actual = result.matches.all { match -> match.scopeMember != null },
            message = "a scoped run must tag every match"
        )
        assertEquals(
            expected = result.members.sumOf { member -> member.result.matches.size },
            actual = result.matches.size,
            message = "the flat list is the concatenation, nothing added or dropped"
        )
    }

    private fun matchedRules(member: String): List<String> {
        return result.members.single { evaluation -> evaluation.key == member }
            .result.matches.map { match -> match.ruleId }
    }

    private fun actionsOf(member: String, ruleId: String): List<Pair<String, Any?>> {
        return result.members.single { evaluation -> evaluation.key == member }
            .result.matches.single { match -> match.ruleId == ruleId }
            .actions.map { action -> action.name to action.arguments.single() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun readInput(): Map<String, Any?> = JacksonUtil.jsonMapper.readValue(
        bundle.resolve("input.json").readText(),
        Map::class.java
    ) as Map<String, Any?>
}
