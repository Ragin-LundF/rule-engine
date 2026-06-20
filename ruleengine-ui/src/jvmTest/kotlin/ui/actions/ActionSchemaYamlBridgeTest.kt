package ui.actions

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

    @Test
    fun `invalid yaml is marked read-only`() {
        val state = ActionSchemaYamlBridge.fromYaml(yaml = "not: [[[valid")
        assertTrue(state.isReadOnly)
    }
}
