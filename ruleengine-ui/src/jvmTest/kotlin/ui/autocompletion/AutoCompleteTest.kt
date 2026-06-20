package ui.autocompletion

import kotlin.test.Test
import kotlin.test.assertTrue

class AutoCompleteTest {

    // --- aggregate functions in when block ---

    @Test
    fun `buildAllCompletions includes all aggregate functions`() {
        val items = buildAllCompletions(schema = null, actionSchema = null)
        val labels = items.map { it.label }
        assertTrue(actual = labels.any { it.startsWith("count") }, message = "Missing count, got: $labels")
        assertTrue(actual = labels.any { it.startsWith("sum") }, message = "Missing sum, got: $labels")
        assertTrue(actual = labels.any { it.startsWith("subtract") }, message = "Missing subtract, got: $labels")
        assertTrue(actual = labels.any { it.startsWith("avg") }, message = "Missing avg, got: $labels")
        assertTrue(actual = labels.any { it.startsWith("median") }, message = "Missing median, got: $labels")
        assertTrue(actual = labels.any { it.startsWith("max") }, message = "Missing max, got: $labels")
        assertTrue(actual = labels.any { it.startsWith("min") }, message = "Missing min, got: $labels")
    }

    @Test
    fun `aggregate function completions have aggregate hint`() {
        val items = buildAllCompletions(schema = null, actionSchema = null)
        val aggregates = items.filter { it.hint == "aggregate" }
        assertTrue(actual = aggregates.size == 7, message = "Expected 7 aggregate completions, got: ${aggregates.size}")
    }

    @Test
    fun `aggregate function insertText contains parentheses`() {
        val items = buildAllCompletions(schema = null, actionSchema = null)
        val aggregates = items.filter { it.hint == "aggregate" }
        aggregates.forEach { item ->
            assertTrue(
                actual = item.insertText.contains("(") && item.insertText.contains(")"),
                message = "Expected parentheses in insertText for ${item.label}, got: ${item.insertText}"
            )
        }
    }

    @Test
    fun `aggregate function completions have OPERATOR kind`() {
        val items = buildAllCompletions(schema = null, actionSchema = null)
        val aggregates = items.filter { it.hint == "aggregate" }
        aggregates.forEach { item ->
            assertTrue(
                actual = item.kind == CompletionKind.OPERATOR,
                message = "Expected OPERATOR kind for ${item.label}, got: ${item.kind}"
            )
        }
    }
}
