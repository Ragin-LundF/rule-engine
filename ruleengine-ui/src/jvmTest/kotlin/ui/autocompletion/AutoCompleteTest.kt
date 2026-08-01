package ui.autocompletion

import ruleengine.core.domain.dto.FieldDefinition
import ruleengine.core.domain.dto.FieldId
import ruleengine.core.domain.dto.FieldType
import ui.DslCursorContext
import ui.DslSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AutoCompleteTest {

    /**
     * The completions offered in a `when` block with nothing typed yet.
     *
     * These used to go through `buildAllCompletions`, a second entry point no production code
     * called. The aggregate list it returned is the same one `buildWhenGeneralCompletions` returns,
     * so the assertions were about live behaviour reached through dead code — they now take the
     * route the editor actually takes.
     */
    private fun whenCompletions(): List<CompletionItem> {
        return buildContextualCompletions(
            context = DslCursorContext(section = DslSection.WHEN),
            schema = null,
            actionSchema = null,
        )
    }

    // --- aggregate functions in when block ---

    @Test
    fun `the when block offers every aggregate function`() {
        val items = whenCompletions()
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
        val items = whenCompletions()
        val aggregates = items.filter { it.hint == "aggregate" }
        assertTrue(actual = aggregates.size == 7, message = "Expected 7 aggregate completions, got: ${aggregates.size}")
    }

    @Test
    fun `aggregate function insertText contains parentheses`() {
        val items = whenCompletions()
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
        val items = whenCompletions()
        val aggregates = items.filter { it.hint == "aggregate" }
        aggregates.forEach { item ->
            assertTrue(
                actual = item.kind == CompletionKind.OPERATOR,
                message = "Expected OPERATOR kind for ${item.label}, got: ${item.kind}"
            )
        }
    }

    // --- value placeholders ---

    @Test
    fun `date placeholder is a quoted ISO value when no format is declared`() {
        val def = FieldDefinition(id = FieldId(value = "createdAt"), type = FieldType.DATE)
        val placeholder = valuePlaceholderForOperator(op = "equals", def = def)
        assertEquals(expected = "\"2024-01-31\"", actual = placeholder)
    }

    @Test
    fun `date placeholder follows the declared format`() {
        val def = FieldDefinition(id = FieldId(value = "dueDate"), type = FieldType.DATE, format = "dd.MM.yyyy")
        assertEquals(
            expected = "\"31.01.2024\"",
            actual = valuePlaceholderForOperator(op = "equals", def = def),
        )
    }

    @Test
    fun `date_time placeholder carries a time component`() {
        val def = FieldDefinition(id = FieldId(value = "bookedAt"), type = FieldType.DATE_TIME)
        assertTrue(
            actual = valuePlaceholderForOperator(op = "gt", def = def).contains("T"),
            message = "expected an ISO date-time placeholder",
        )
    }

    /** `between` used to insert the numeric `0 100` for every type, which no date field can compile. */
    @Test
    fun `between placeholder on a date field is a pair of quoted dates`() {
        val def = FieldDefinition(id = FieldId(value = "dueDate"), type = FieldType.DATE, format = "dd.MM.yyyy")
        assertEquals(
            expected = "\"31.01.2024\" \"31.01.2024\"",
            actual = valuePlaceholderForOperator(op = "between", def = def),
        )
    }

    @Test
    fun `between placeholder on a numeric field stays numeric`() {
        val def = FieldDefinition(id = FieldId(value = "amount"), type = FieldType.DECIMAL)
        assertEquals(expected = "0 100", actual = valuePlaceholderForOperator(op = "between", def = def))
    }

    @Test
    fun `date_time gets the date operator defaults`() {
        assertEquals(expected = DATE_OPS, actual = defaultOperatorsForType(fieldType = FieldType.DATE_TIME))
    }

    /** The engine tests a string set for membership only; `contains` is rejected at compile time. */
    @Test
    fun `string set defaults are membership operators only`() {
        assertEquals(
            expected = listOf("containsAny", "containsAll"),
            actual = defaultOperatorsForType(fieldType = FieldType.STRING_SET),
        )
    }
}
