package ruleengine.integration

import ruleengine.builder.RuleEngineBuilder
import ruleengine.core.domain.dto.RuleBranch
import ruleengine.core.errors.RuleEngineBuildException
import ruleengine.jackson.JacksonUtil
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Per-member evaluation driven by a manifest `scope`, loaded from real files — REQ-07.
 *
 * The bundle deliberately exercises the parts a scoped run cannot fake: the rules name account
 * fields directly, one reads a document-level string set through the fallback, and the two accounts
 * differ in which rules they match.
 */
class ScopedEvaluationIntegrationTest {

    private val bundle = Path.of("src/test/resources/scoped-accounts")

    private val input: Map<String, Any?> = readInput()

    @Test
    fun `every account is evaluated once`() {
        val result = evaluate()

        assertEquals(expected = 2, actual = result.members.size)
        assertEquals(
            expected = listOf("acc-1", "acc-2"),
            actual = result.members.map { member -> member.key },
            message = "the declared id member identifies each account"
        )
        assertEquals(expected = listOf(0, 1), actual = result.members.map { member -> member.index })
    }

    @Test
    fun `an account's rules see its own fields`() {
        val result = evaluate()

        assertEquals(
            expected = listOf("priority-exposure", "established-account"),
            actual = result.members.first().result.matches.map { match -> match.ruleId },
            message = "acc-1 has 12000 from priority customers and was registered in January"
        )
        assertTrue(
            actual = result.members.last().result.matches.isEmpty(),
            message = "acc-2 has 500 from priority customers and registered in May"
        )
    }

    /** `priorityCustomerIds` and `reviewDate` belong to the document, not to an account. */
    @Test
    fun `a scoped rule reads document-level fields`() {
        val result = evaluate()
        val matched = result.members.first().result.matches.map { match -> match.ruleId }

        assertTrue(
            actual = matched.contains(element = "priority-exposure"),
            message = "the membership source is a document field, reachable only through the fallback"
        )
        assertTrue(
            actual = matched.contains(element = "established-account"),
            message = "the second date operand is a document field"
        )
    }

    /** The declared normalizers must apply to a collection member inside a scoped run too. */
    @Test
    fun `normalizers apply inside a scoped member`() {
        val result = evaluate()

        assertTrue(
            actual = result.members.first().result.matches.any { match -> match.ruleId == "priority-exposure" },
            message = "the 8000 invoice is written ' ACME ' and only trim + lowercase make it match"
        )
    }

    @Test
    fun `the flat match list carries the member each match came from`() {
        val result = evaluate()

        assertEquals(
            expected = listOf("acc-1" to "priority-exposure", "acc-1" to "established-account"),
            actual = result.matches.map { match -> match.scopeMember to match.ruleId },
            message = "matches stay flat and in member order, tagged with their member"
        )
        assertTrue(actual = result.matches.all { match -> match.branch == RuleBranch.THEN })
    }

    /** Per-member state stays per member; the top level says nothing about any one account. */
    @Test
    fun `document-level variables and stoppedBy stay empty for a scoped run`() {
        val result = evaluate()

        assertTrue(actual = result.variables.isEmpty())
        assertEquals(expected = null, actual = result.stoppedBy)
    }

    @Test
    fun `an unscoped entry is unaffected`() {
        val engine = RuleEngineBuilder.fromManifestEntry(
            manifestPath = Path.of("src/test/resources/warehouse-shipments/manifest.yaml"),
            entryId = "shipment-assessment"
        )

        assertTrue(
            actual = engine.members().isEmpty(),
            message = "a manifest without 'scope' must produce no member breakdown at all"
        )
    }

    @Test
    fun `a scope naming a non-collection is rejected at load time`() {
        val failure = assertFailsWith<RuleEngineBuildException> {
            RuleEngineBuilder.fromManifestEntry(
                manifestPath = bundle.resolve("invalid-scope-manifest.yaml"),
                entryId = "bad-scope"
            )
        }

        assertTrue(
            actual = failure.message?.contains(other = "not a collection") == true,
            message = "got: ${failure.message}"
        )
    }

    @Test
    fun `a scope naming an unknown field is rejected at load time`() {
        val failure = assertFailsWith<RuleEngineBuildException> {
            RuleEngineBuilder.fromManifestEntry(
                manifestPath = bundle.resolve("unknown-scope-manifest.yaml"),
                entryId = "unknown-scope"
            )
        }

        assertTrue(
            actual = failure.message?.contains(other = "is not a field of the schema") == true,
            message = "got: ${failure.message}"
        )
    }

    @Test
    fun `an input without the scoped collection evaluates nothing and does not fail`() {
        val result = engine().evaluate(input = mapOf("reviewDate" to "2024-06-01"))

        assertTrue(actual = result.members.isEmpty())
        assertFalse(actual = result.matches.isNotEmpty())
    }

    private fun evaluate() = engine().evaluate(input = input)

    private fun engine() = RuleEngineBuilder.fromManifestEntry(
        manifestPath = bundle.resolve("manifest.yaml"),
        entryId = "account-review"
    )

    private fun ruleengine.builder.LoadedRuleEngine.members() = evaluate(input = input).members

    @Suppress("UNCHECKED_CAST")
    private fun readInput(): Map<String, Any?> = JacksonUtil.jsonMapper.readValue(
        bundle.resolve("input.json").readText(),
        Map::class.java
    ) as Map<String, Any?>
}
