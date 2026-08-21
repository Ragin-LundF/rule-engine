package ui.autocompletion

import ruleengine.core.domain.dto.action.ActionArgType
import ruleengine.core.domain.dto.action.ActionDefinition
import ruleengine.core.domain.dto.action.ActionSchema
import ruleengine.dsl.ast.AssignmentKindAst
import ui.dsl.model.DslCursorContext
import ui.dsl.model.DslSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the code editor offers as the argument of an action that declares a variable type.
 *
 * The point of declaring `variable_list` rather than `string` is that the editor can then offer the
 * accumulators and nothing else. An action that declares a literal type keeps offering every variable,
 * because the engine still accepts any of them there.
 */
class VariableArgumentCompletionTest {

    private val actionSchema = ActionSchema(
        actions = mapOf(
            "reason" to ActionDefinition(name = "reason", argTypes = listOf(ActionArgType.VARIABLE_STRING)),
            "topics" to ActionDefinition(name = "topics", argTypes = listOf(ActionArgType.VARIABLE_LIST)),
            "label" to ActionDefinition(name = "label", argTypes = listOf(ActionArgType.STRING)),
        )
    )

    private val kinds = mapOf(
        "why" to AssignmentKindAst.SET,
        "matters" to AssignmentKindAst.ADD,
    )

    @Test
    fun `a variable_string argument offers only the set variables`() {
        assertEquals(expected = listOf("\$why"), actual = variableLabels(afterAction = "reason"))
    }

    @Test
    fun `a variable_list argument offers only the add variables`() {
        assertEquals(expected = listOf("\$matters"), actual = variableLabels(afterAction = "topics"))
    }

    @Test
    fun `a string argument still offers every variable`() {
        assertEquals(
            expected = listOf("\$why", "\$matters"),
            actual = variableLabels(afterAction = "label"),
        )
    }

    /** Without kinds there is nothing to narrow by, so narrowing would hide every option. */
    @Test
    fun `an unknown set of kinds offers every variable rather than none`() {
        val labels = completions(afterAction = "topics", variableKinds = emptyMap())
            .map { item -> item.label }
            .filter { label -> label.startsWith(prefix = "$") }

        assertEquals(expected = listOf("\$why", "\$matters"), actual = labels)
    }

    /** The variables are the completions there, so a bare `$` beside them would be noise. */
    @Test
    fun `a declared variable argument contributes no literal placeholder`() {
        val placeholders = buildActionArgCompletions(actionName = "reason", actionSchema = actionSchema)

        assertEquals(expected = emptyList(), actual = placeholders)
    }

    @Test
    fun `a literal argument still contributes its placeholder`() {
        val placeholders = buildActionArgCompletions(actionName = "label", actionSchema = actionSchema)

        assertEquals(expected = listOf("\"value\""), actual = placeholders.map { item -> item.insertText })
    }

    @Test
    fun `the action name completion inserts a dollar sign for a declared variable argument`() {
        val item = buildActionNameCompletions(actionSchema = actionSchema).single { it.label == "topics" }

        assertEquals(expected = "topics $", actual = item.insertText)
        assertTrue(actual = item.hint == "variable_list", message = "got: ${item.hint}")
    }

    private fun variableLabels(afterAction: String): List<String> {
        return completions(afterAction = afterAction, variableKinds = kinds)
            .map { item -> item.label }
            .filter { label -> label.startsWith(prefix = "$") }
    }

    private fun completions(
        afterAction: String,
        variableKinds: Map<String, AssignmentKindAst>,
    ) = buildContextualCompletions(
        context = DslCursorContext(section = DslSection.THEN, afterAction = afterAction),
        schema = null,
        actionSchema = actionSchema,
        variableNames = listOf("why", "matters"),
        variableKinds = variableKinds,
    )
}
