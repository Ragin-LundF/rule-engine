package ui.tester

import ui.tester.model.RuleResult
import ui.tester.model.displayLabel
import ui.tester.model.rowKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * How a result names and identifies itself once an entry is scoped.
 *
 * Both properties exist because `ruleId` stops being unique: the same rule runs once per member, so
 * the rows, the "variables set" summary and the "actions emitted" summary would each show two
 * identical headings, and expanding one member's copy of a rule would expand every member's.
 */
class RuleResultLabelTest {

    private fun result(ruleId: String, member: String? = null) = RuleResult(
        ruleId = ruleId,
        matched = true,
        actions = emptyList(),
        traceRows = emptyList(),
        scopeMember = member,
    )

    @Test
    fun `an unscoped result is named by its rule id alone`() {
        val plain = result(ruleId = "priority-exposure")

        assertEquals(expected = "priority-exposure", actual = plain.displayLabel)
        assertEquals(expected = "priority-exposure", actual = plain.rowKey)
    }

    @Test
    fun `a scoped result names the member it came from`() {
        val scoped = result(ruleId = "priority-exposure", member = "acc-1")

        assertEquals(expected = "acc-1 · priority-exposure", actual = scoped.displayLabel)
    }

    @Test
    fun `two members of the same rule are told apart`() {
        val first = result(ruleId = "balance-drift", member = "acc-1")
        val second = result(ruleId = "balance-drift", member = "acc-2")

        assertNotEquals(illegal = first.displayLabel, actual = second.displayLabel)
        assertNotEquals(
            illegal = first.rowKey,
            actual = second.rowKey,
            message = "one key for both would expand every member's copy at once",
        )
    }
}
