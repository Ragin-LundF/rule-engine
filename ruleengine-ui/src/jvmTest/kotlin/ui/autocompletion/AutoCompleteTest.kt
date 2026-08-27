package ui.autocompletion

import ruleengine.core.domain.OperatorNames
import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.evaluator.compiled.DslFunctions
import ui.autocompletion.model.CompletionItem
import ui.autocompletion.model.CompletionKind
import ui.dsl.analyzeDslContext
import ui.dsl.model.DslCursorContext
import ui.dsl.model.DslSection
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

    // --- add clause ---

    private fun thenCompletions(context: DslCursorContext): List<CompletionItem> =
        buildContextualCompletions(context = context, schema = null, actionSchema = null)

    @Test
    fun `the then block offers the add keyword`() {
        val labels = thenCompletions(context = DslCursorContext(section = DslSection.THEN)).map { it.label }

        assertTrue(actual = labels.contains(element = "add"), message = "got: $labels")
    }

    /**
     * `add` is a clause keyword, not an action name. Without that distinction the editor would offer
     * the argument completions of an action called `add`.
     */
    @Test
    fun `add is not treated as an action name`() {
        val text = """
            rule "r" {
              when
                amount > 0
              then
                add 
        """.trimIndent()

        val context = analyzeDslContext(text = text, cursorPos = text.length)

        assertEquals(expected = null, actual = context.afterAction)
    }

    @Test
    fun `the target of an add clause is offered as a bare variable name`() {
        val text = """
            rule "r" {
              when
                amount > 0
              then
                add "billing" to 
        """.trimIndent()

        val context = analyzeDslContext(text = text, cursorPos = text.length)
        assertTrue(actual = context.expectsListName, message = "expected a list-name context")

        val labels = buildContextualCompletions(
            context = context,
            schema = null,
            actionSchema = null,
            variableNames = listOf("topics"),
        ).map { it.label }

        assertEquals(expected = listOf("topics"), actual = labels)
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

    /**
     * The completion catalog is hand-written, so nothing but this test stops a function added to the
     * engine from being invisible in the editor.
     */
    @Test
    fun `every engine function is offered as a completion`() {
        val labels = whenCompletions().map { item -> item.label }

        DslFunctions.allNames().forEach { function ->
            assertTrue(
                actual = labels.any { label -> label.startsWith(prefix = "$function(") },
                message = "No completion for '$function', got: $labels",
            )
        }
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

    /**
     * `in` had the same shape of bug `between` used to: one hardcoded snippet for every type.
     *
     * It inserted `["a", "b"]` regardless, so completing `in` on a numeric field produced
     * `statusCode in ["a", "b"]` — a string list the validator rejects. Now that `in` reaches every
     * scalar, the snippet has to be typed like the field.
     */
    @Test
    fun `in placeholder on a numeric field is a numeric list`() {
        val integer = FieldDefinition(id = FieldId(value = "statusCode"), type = FieldType.INTEGER)
        assertEquals(expected = "[0, 100]", actual = valuePlaceholderForOperator(op = "in", def = integer))

        val decimal = FieldDefinition(id = FieldId(value = "amount"), type = FieldType.DECIMAL)
        assertEquals(expected = "[0.0, 1.0]", actual = valuePlaceholderForOperator(op = "in", def = decimal))
    }

    @Test
    fun `in placeholder on a date field is a list of quoted dates in the declared format`() {
        val def = FieldDefinition(id = FieldId(value = "dueDate"), type = FieldType.DATE, format = "dd.MM.yyyy")
        assertEquals(
            expected = "[\"31.01.2024\", \"31.01.2024\"]",
            actual = valuePlaceholderForOperator(op = "in", def = def),
        )
    }

    @Test
    fun `in placeholder on a text field stays a text list`() {
        val def = FieldDefinition(id = FieldId(value = "purpose"), type = FieldType.TEXT)
        assertEquals(
            expected = "[\"a\", \"b\"]",
            actual = valuePlaceholderForOperator(op = "in", def = def),
        )
    }

    @Test
    fun `in is offered on a numeric field`() {
        assertTrue(
            actual = OperatorNames.IN in defaultOperatorsForType(fieldType = FieldType.INTEGER),
            message = "the engine allows membership on a number, so the editor must offer it",
        )
        assertTrue(
            actual = OperatorNames.IN in defaultOperatorsForType(fieldType = FieldType.DATE),
        )
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

    // --- else branch ---

    @Test
    fun `a then block offers the else keyword`() {
        assertTrue(
            actual = branchCompletions(section = DslSection.THEN).map { it.label }.contains(element = "else"),
            message = "else must be offered after a then block",
        )
    }

    /** There is no nested `else`, so offering the keyword inside one would only produce a parse error. */
    @Test
    fun `an else block does not offer the else keyword again`() {
        assertTrue(
            actual = branchCompletions(section = DslSection.ELSE).map { it.label }.none { it == "else" },
            message = "else must not be offered inside an else block",
        )
    }

    @Test
    fun `an else block offers the same action clauses as a then block`() {
        val then = branchCompletions(section = DslSection.THEN).map { it.label }.filter { it != "else" }

        assertEquals(expected = then, actual = branchCompletions(section = DslSection.ELSE).map { it.label })
    }

    private fun branchCompletions(section: DslSection): List<CompletionItem> {
        return buildContextualCompletions(
            context = DslCursorContext(section = section),
            schema = null,
            actionSchema = null,
        )
    }

    // --- section detection ---

    @Test
    fun `the cursor after else is in the ELSE section`() {
        val text = "rule \"r\" {\n  when\n    amount >= 1\n  then\n    label \"x\"\n  else\n    "

        assertEquals(
            expected = DslSection.ELSE,
            actual = analyzeDslContext(text = text, cursorPos = text.length).section,
        )
    }

    @Test
    fun `the cursor after not_exists is in the NOT_EXISTS section`() {
        val text = "rule \"r\" {\n  when\n    amount >= 1\n  then\n    label \"x\"\n  not_exists\n    "

        assertEquals(
            expected = DslSection.NOT_EXISTS,
            actual = analyzeDslContext(text = text, cursorPos = text.length).section,
        )
    }

    // --- not_exists branch ---

    @Test
    fun `a then block offers the not_exists keyword`() {
        assertTrue(
            actual = branchCompletions(section = DslSection.THEN).map { it.label }.contains(element = "not_exists"),
            message = "not_exists must be offered after a then block",
        )
    }

    /** `not_exists` is written after `else`, so an else block is still allowed to open it. */
    @Test
    fun `an else block offers not_exists but not else`() {
        val labels = branchCompletions(section = DslSection.ELSE).map { it.label }

        assertTrue(actual = labels.contains(element = "not_exists"), message = "got: $labels")
        assertTrue(actual = labels.none { it == "else" }, message = "got: $labels")
    }

    /** Nothing may follow it, so offering either branch keyword there would only produce a parse error. */
    @Test
    fun `a not_exists block offers no branch keyword at all`() {
        val labels = branchCompletions(section = DslSection.NOT_EXISTS).map { it.label }

        assertTrue(actual = labels.none { it == "else" || it == "not_exists" }, message = "got: $labels")
    }

    @Test
    fun `a not_exists block offers the same action clauses as a then block`() {
        val then = branchCompletions(section = DslSection.THEN)
            .map { it.label }
            .filter { it != "else" && it != "not_exists" }
        val notExists = branchCompletions(section = DslSection.NOT_EXISTS).map { it.label }

        assertEquals(expected = then, actual = notExists)
    }

    /** A closing brace ends the rule, so the next token starts fresh rather than staying in a branch. */
    @Test
    fun `the cursor after the rule's closing brace is back at top level`() {
        val text = "rule \"r\" {\n  when\n    amount >= 1\n  then\n    label \"x\"\n  else\n    label \"y\"\n}\n"

        assertEquals(
            expected = DslSection.TOP_LEVEL,
            actual = analyzeDslContext(text = text, cursorPos = text.length).section,
        )
    }
}
