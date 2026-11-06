package ui.builder

import ruleengine.dsl.parser.Parser
import ui.builder.model.BuilderRule
import ui.builder.model.mutable.BuilderEditorState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * `orEmpty`, `isPresent` and the `optional` source marker survive a Builder edit: the Builder rewrites
 * the whole rule, so anything it dropped would be deleted from the file. Compared as parsed rules, the
 * convention of `AdvancedExpressionRoundTripTest`, so only a change in meaning fails.
 */
class MissingValuesBuilderRoundTripTest {

    private val eventsSource = "orEmpty(events.confirmed)[period in periods].count, " +
        "orEmpty(events.unclassified)[period in periods].count"
    private val optionalSource = "events.confirmed[period in periods].count, " +
        "optional events.unclassified[period in periods].count"

    private fun events(sources: String): String {
        return """
            rule "events" {
              description "Every period has at most one event, and not every period has one."
              when
                count(periods) > 0
                and count(sumByKey("period", $sources)) == count(periods)
                and max(sumByKey("period", $sources)) <= 1
                and min(sumByKey("period", $sources)) < 1
              then outcome "PASS"
              else outcome "FAIL"
            }
        """.trimIndent()
    }

    private val feeRatio = """
        rule "fee-ratio" {
          description "Fees are below five percent of income over the periods."
          when
            count(periods) > 0
            and isPresent(fees)
            and abs(sum(orEmpty(fees.rows)[period in periods].amount)) / sum(income.rows[period in periods].amount) < 0.05
          then outcome "PASS"
          else outcome "FAIL"
        }
    """.trimIndent()

    @Test
    fun `the events check with orEmpty round-trips`() {
        assertRoundTrips(dsl = events(sources = eventsSource))
    }

    @Test
    fun `the fee-ratio check with orEmpty and isPresent round-trips`() {
        assertRoundTrips(dsl = feeRatio)
    }

    @Test
    fun `the events check with an optional source round-trips`() {
        val generated = assertRoundTrips(dsl = events(sources = optionalSource))

        assertTrue(actual = generated.contains(other = "optional events.unclassified"), message = generated)
    }

    private fun assertRoundTrips(dsl: String): String {
        val original = Parser(input = dsl).parseRules().single()
        val builderRule = RuleAstToBuilderMapper.map(rule = original)
        assertTrue(actual = builderRule is BuilderRule.Supported, message = "Builder locked: $builderRule")

        val generated = assertNotNull(
            actual = BuilderToRuleDsl.generate(state = BuilderEditorState.fromBuilderRule(rule = builderRule)),
        )

        assertEquals(expected = original, actual = Parser(input = generated).parseRules().single(), message = generated)
        return generated
    }
}
