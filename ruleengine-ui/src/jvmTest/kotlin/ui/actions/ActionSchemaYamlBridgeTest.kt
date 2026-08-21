package ui.actions

import ui.actions.model.ActionEditorState
import ui.actions.model.EditableAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActionSchemaYamlBridgeTest {

    @Test
    fun `round-trip a single string action`() {
        val state = ActionEditorState(
            actions = listOf(
                EditableAction(
                    name = "label",
                    argTypes = listOf("string"),
                    purpose = "Assign a label",
                ),
            ),
        )
        val yaml = ActionSchemaYamlBridge.toYaml(state = state)
        val reloaded = ActionSchemaYamlBridge.fromYaml(yaml = yaml)
        assertFalse(reloaded.isReadOnly)
        assertEquals(1, reloaded.actions.size)
        assertEquals("label", reloaded.actions[0].name)
        assertEquals(listOf("string"), reloaded.actions[0].argTypes)
    }

    @Test
    fun `round-trip integer and decimal actions`() {
        val state = ActionEditorState(
            actions = listOf(
                EditableAction(name = "score", argTypes = listOf("integer")),
                EditableAction(name = "rate", argTypes = listOf("decimal")),
            ),
        )
        val yaml = ActionSchemaYamlBridge.toYaml(state = state)
        val reloaded = ActionSchemaYamlBridge.fromYaml(yaml = yaml)
        assertEquals(2, reloaded.actions.size)
        assertEquals("score", reloaded.actions[0].name)
        assertEquals(listOf("integer"), reloaded.actions[0].argTypes)
        assertEquals("rate", reloaded.actions[1].name)
        assertEquals(listOf("decimal"), reloaded.actions[1].argTypes)
    }

    /**
     * The chips the schema editor offers come from `ActionArgType.entries`, so a new engine arg type
     * appears there without a UI change — this is what says it also survives the YAML round trip.
     */
    @Test
    fun `round-trip the variable argument types`() {
        val state = ActionEditorState(
            actions = listOf(
                EditableAction(name = "reason", argTypes = listOf("variable_string")),
                EditableAction(name = "topics", argTypes = listOf("variable_list")),
            ),
        )
        val yaml = ActionSchemaYamlBridge.toYaml(state = state)
        val reloaded = ActionSchemaYamlBridge.fromYaml(yaml = yaml)

        assertFalse(reloaded.isReadOnly, "unexpected: $yaml")
        assertEquals(listOf("variable_string"), reloaded.actions[0].argTypes)
        assertEquals(listOf("variable_list"), reloaded.actions[1].argTypes)
    }

    @Test
    fun `the editor offers every engine argument type as a chip`() {
        assertEquals(
            expected = listOf("string", "integer", "decimal", "variable_string", "variable_list"),
            actual = KnownActionArgTypes,
        )
    }

    @Test
    fun `invalid yaml is marked read-only`() {
        val state = ActionSchemaYamlBridge.fromYaml(yaml = "not: [[[valid")
        assertTrue(state.isReadOnly)
    }
}
