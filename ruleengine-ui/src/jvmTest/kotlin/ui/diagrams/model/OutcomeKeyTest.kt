package ui.diagrams.model

import ruleengine.dsl.ast.ActionAst
import ruleengine.dsl.ast.ExtractionRefLiteral
import ruleengine.dsl.ast.NumberLiteral
import ruleengine.dsl.ast.RuleAst
import ruleengine.dsl.parser.Parser
import ui.diagrams.OutcomeKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OutcomeKeyTest {

    private val rules = Parser(input = WAREHOUSE_RULES).parseRules()

    private fun ruleNamed(id: String): RuleAst {
        return rules.single { rule -> rule.id == id }
    }

    @Test
    fun `the key is the action name joined to its whole first argument`() {
        val actions = ruleNamed(id = "transit-within-promise").actions

        assertEquals(
            expected = listOf("assessment:transit:green", "reason:delivered-within-two-days"),
            actual = actions.mapNotNull { action -> OutcomeKey.staticOutputKey(action = action) },
        )
    }

    /**
     * The property the outcome map exists to make visible. The key uses the *whole* first argument, so
     * two rules a reader would call "the transit decision" land in two different buckets and never
     * decide the same value. If this ever collapses to one key, the view starts claiming the two rules
     * compete when they do not.
     */
    @Test
    fun `two values under the same prefix are separate buckets`() {
        val green = ruleNamed(id = "transit-within-promise").actions.first()
        val red = ruleNamed(id = "transit-over-promise").actions.first()

        assertEquals(expected = "assessment:transit:green", actual = OutcomeKey.staticOutputKey(action = green))
        assertEquals(expected = "assessment:transit:red", actual = OutcomeKey.staticOutputKey(action = red))
    }

    @Test
    fun `every bucket in the fixture holds exactly one rule, so short-circuiting is inert`() {
        val rulesByKey = mutableMapOf<String, MutableList<String>>()
        rules.forEach { rule ->
            rule.actions.forEach { action ->
                val key = OutcomeKey.staticOutputKey(action = action) ?: return@forEach
                rulesByKey.getOrPut(key) { mutableListOf() } += rule.id
            }
        }

        assertEquals(expected = 14, actual = rulesByKey.size)
        assertEquals(
            expected = emptyList(),
            actual = rulesByKey.filterValues { bucket -> bucket.size > 1 }.keys.toList(),
            message = "No bucket may hold more than one rule in this fixture",
        )
    }

    @Test
    fun `a rule emitting two actions belongs to one bucket per action`() {
        val actions = ruleNamed(id = "premium-service-promise").actions

        assertEquals(
            expected = listOf("assessment:service:premium", "reason:gold-customer-on-express-service"),
            actual = actions.mapNotNull { action -> OutcomeKey.staticOutputKey(action = action) },
        )
    }

    @Test
    fun `the display family collapses the prefix of the value`() {
        val green = ruleNamed(id = "transit-within-promise").actions.first()
        val red = ruleNamed(id = "transit-over-promise").actions.first()

        assertEquals(expected = "assessment:transit", actual = OutcomeKey.displayFamily(action = green))
        assertEquals(expected = "assessment:transit", actual = OutcomeKey.displayFamily(action = red))
    }

    @Test
    fun `a value without a prefix falls back to the bare action name`() {
        val reason = ruleNamed(id = "transit-within-promise").actions.last()

        assertEquals(expected = "reason", actual = OutcomeKey.displayFamily(action = reason))
    }

    @Test
    fun `an action with no arguments produces no key`() {
        val action = ActionAst(name = "flag", arguments = emptyList())

        assertNull(actual = OutcomeKey.staticOutputKey(action = action))
        assertNull(actual = OutcomeKey.displayFamily(action = action))
    }

    /**
     * An extraction reference is only resolved per evaluation, so it never becomes a
     * `CompiledActionArgument.Static` and the engine leaves such a rule ungrouped. The view must do
     * the same rather than inventing a bucket named after the placeholder.
     */
    @Test
    fun `an extraction reference produces no key`() {
        val action = ActionAst(name = "label", arguments = listOf(ExtractionRefLiteral(groupIndex = 1)))

        assertNull(actual = OutcomeKey.staticOutputKey(action = action))
        assertNull(actual = OutcomeKey.displayFamily(action = action))
    }

    @Test
    fun `a numeric first argument is keyed by its literal text`() {
        val action = ActionAst(name = "score", arguments = listOf(NumberLiteral(value = "10")))

        assertEquals(expected = "score:10", actual = OutcomeKey.staticOutputKey(action = action))
        assertEquals(expected = "score", actual = OutcomeKey.displayFamily(action = action))
    }
}
