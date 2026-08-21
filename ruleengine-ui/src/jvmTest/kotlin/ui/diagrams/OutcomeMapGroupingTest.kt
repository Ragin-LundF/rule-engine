package ui.diagrams

import ruleengine.core.domain.dto.RuleBranch
import ruleengine.dsl.parser.Parser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The grouping behind the outcome map: which rules the view says can decide the same value.
 *
 * Asserted against the real function rather than a hand-rolled copy, because the claim the view makes is
 * exactly this grouping — and the last time it was wrong (every branch but `then` was ignored) a test
 * that rebuilt the grouping itself agreed with it.
 */
class OutcomeMapGroupingTest {

    @Test
    fun `a then and an else outcome of one rule land in different buckets`() {
        val buckets = groupByFamily(rules = parse(TIERED)).getValue("assessment")

        assertEquals(expected = listOf("assessment:GREEN", "assessment:RED"), actual = buckets.keys.toList())
        assertEquals(expected = RuleBranch.THEN, actual = buckets.getValue("assessment:GREEN").single().branch)
        assertEquals(expected = RuleBranch.ELSE, actual = buckets.getValue("assessment:RED").single().branch)
    }

    @Test
    fun `a not_exists outcome is grouped too`() {
        val buckets = groupByFamily(rules = parse(THREE_BRANCH)).getValue("assessment")

        assertEquals(
            expected = RuleBranch.NOT_EXISTS,
            actual = buckets.getValue("assessment:UNKNOWN").single().branch,
        )
    }

    /**
     * The bug this change fixes: one rule decides `RED` from its `else`, another from its `then`, and the
     * view used to show a bucket of one because it only read `then` blocks.
     */
    @Test
    fun `a value decided by one rule's else and another's then is one bucket of two rules`() {
        val bucket = groupByFamily(rules = parse(TIERED + "\n" + OUTRIGHT_RED))
            .getValue("assessment")
            .getValue("assessment:RED")

        assertEquals(expected = 2, actual = bucket.distinctRuleCount())
        assertEquals(
            expected = listOf("tier" to RuleBranch.ELSE, "blocked" to RuleBranch.THEN),
            actual = bucket.map { source -> source.rule.id to source.branch },
        )
    }

    /** Exactly one branch of a rule ever runs, so a rule reaching a bucket twice is not competition. */
    @Test
    fun `one rule producing the same value from two branches counts once`() {
        val bucket = groupByFamily(
            rules = parse(
                """
                rule "always" {
                  description "d"
                  when
                    amount >= 1000
                  then
                    assessment "GREEN"
                  else
                    assessment "GREEN"
                }
                """.trimIndent()
            )
        ).getValue("assessment").getValue("assessment:GREEN")

        assertEquals(expected = 2, actual = bucket.size, message = "both branches are listed")
        assertEquals(expected = 1, actual = bucket.distinctRuleCount(), message = "but one rule decides it")
    }

    @Test
    fun `a rule with only a then block groups exactly as it did before`() {
        val buckets = groupByFamily(rules = parse(OUTRIGHT_RED)).getValue("assessment")

        assertEquals(expected = listOf("assessment:RED"), actual = buckets.keys.toList())
        assertEquals(expected = RuleBranch.THEN, actual = buckets.getValue("assessment:RED").single().branch)
    }

    @Test
    fun `branches with no actions contribute nothing`() {
        val sources = outcomeSourcesOf(rule = parse(OUTRIGHT_RED).single())

        assertEquals(expected = listOf(RuleBranch.THEN), actual = sources.map { source -> source.branch })
    }

    @Test
    fun `a branch whose only action has a non-static argument is not groupable`() {
        val rule = parse(
            """
            rule "reports" {
              description "d"
              when
                amount >= 1000
              then
                set why = "big"
                assessment ${'$'}why
            }
            """.trimIndent()
        ).single()

        assertTrue(
            actual = outcomeSourcesOf(rule = rule).none { source -> source.hasKey() },
            message = "a variable argument is only known at evaluation time",
        )
        assertEquals(expected = emptyMap(), actual = groupByFamily(rules = listOf(rule)))
    }

    /** A rule groupable through one branch only is still accounted for, by that branch. */
    @Test
    fun `a rule with one groupable branch appears through that branch`() {
        val rule = parse(
            """
            rule "mixed" {
              description "d"
              when
                amount >= 1000
              then
                set why = "big"
                assessment ${'$'}why
              else
                assessment "RED"
            }
            """.trimIndent()
        ).single()

        val buckets = groupByFamily(rules = listOf(rule)).getValue("assessment")

        assertEquals(expected = listOf("assessment:RED"), actual = buckets.keys.toList())
        assertEquals(expected = RuleBranch.ELSE, actual = buckets.getValue("assessment:RED").single().branch)
        assertTrue(actual = outcomeSourcesOf(rule = rule).any { source -> source.hasKey() })
    }

    private fun parse(dsl: String) = Parser(input = dsl).parseRules()

    private companion object {
        val TIERED = """
            rule "tier" {
              description "d"
              when
                amount >= 1000
              then
                assessment "GREEN"
              else
                assessment "RED"
            }
        """.trimIndent()

        val THREE_BRANCH = """
            rule "tier" {
              description "d"
              when
                amount >= 1000
              then
                assessment "GREEN"
              else
                assessment "RED"
              not_exists
                assessment "UNKNOWN"
            }
        """.trimIndent()

        val OUTRIGHT_RED = """
            rule "blocked" {
              description "d"
              when
                country == "xx"
              then
                assessment "RED"
            }
        """.trimIndent()
    }
}
