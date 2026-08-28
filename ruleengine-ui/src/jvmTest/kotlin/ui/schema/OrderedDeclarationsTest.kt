package ui.schema

import ruleengine.core.domain.dto.field.FieldType
import ruleengine.schema.ActionSchemaLoader
import ruleengine.schema.FieldSchemaLoader
import ui.actions.ActionSchemaYamlBridge
import ui.actions.model.ActionEditorState
import ui.actions.model.EditableAction
import ui.schema.model.EditableField
import ui.schema.model.SchemaEditorState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The two lists whose order is part of their meaning, and which the visual editor used to render as
 * unordered sets of tick-boxes.
 *
 * These are the correctness half of the visual-editor rework: not "the chips looked wrong" but "the
 * control could not express what the file means, and two legal declarations were unreachable". Every
 * assertion below goes through the real bridge and the real loader, so the round trip is the subject
 * rather than the editor model on its own.
 */
class OrderedDeclarationsTest {

    // ── an action's argTypes: a positional parameter list ─────────────────────

    private fun actionsYaml(vararg argTypes: String): String = ActionSchemaYamlBridge.toYaml(
        state = ActionEditorState(actions = listOf(EditableAction(name = "audit", argTypes = argTypes.toList()))),
    )

    private fun loadedArgTypes(yaml: String): List<String> =
        ActionSchemaLoader.loadFromString(content = yaml)
            .actions
            .getValue(key = "audit")
            .argTypes
            .map { type -> type.name.lowercase() }

    @Test
    fun `argument order survives the round trip`() {
        val yaml = actionsYaml("string", "integer")

        assertEquals(expected = listOf("string", "integer"), actual = loadedArgTypes(yaml = yaml))
    }

    /**
     * The case a chip row cannot express at all: the same declaration with the arguments the other way
     * round is a *different* declaration, and `Validator` checks the type at each index.
     */
    @Test
    fun `reversing the arguments is a different declaration`() {
        val forwards = actionsYaml("string", "integer")
        val backwards = actionsYaml("integer", "string")

        assertNotEquals(illegal = forwards, actual = backwards)
        assertEquals(expected = listOf("string", "integer"), actual = loadedArgTypes(yaml = forwards))
        assertEquals(expected = listOf("integer", "string"), actual = loadedArgTypes(yaml = backwards))
    }

    /** Unreachable before: a chip is either on or off, so it could not be ticked twice. */
    @Test
    fun `two arguments of the same type are expressible`() {
        val yaml = actionsYaml("string", "string")

        assertEquals(expected = listOf("string", "string"), actual = loadedArgTypes(yaml = yaml))
        assertEquals(
            expected = 2,
            actual = ActionSchemaLoader.loadFromString(content = yaml)
                .actions.getValue(key = "audit").argTypes.size,
            message = "arity is what Validator checks first, and a set of one loses it",
        )
    }

    @Test
    fun `an action with no arguments stays an action with no arguments`() {
        val yaml = actionsYaml()

        assertTrue(actual = loadedArgTypes(yaml = yaml).isEmpty())
    }

    // ── a field's normalizers: an ordered chain ───────────────────────────────

    private fun schemaYaml(vararg normalizers: String): String = FieldSchemaYamlBridge.toYaml(
        state = SchemaEditorState(
            schemaName = "s",
            fields = listOf(
                EditableField(
                    path = "purpose",
                    type = FieldType.TEXT,
                    normalizers = normalizers.toList(),
                    operators = listOf("equals"),
                ),
            ),
        ),
    )

    private fun loadedNormalizers(yaml: String): List<String> =
        FieldSchemaLoader.loadFromString(content = yaml, nameHint = "s")
            .fields
            .values
            .first()
            .normalizers
            .map { id -> id.value }

    @Test
    fun `the normalizer chain keeps its order through the round trip`() {
        val yaml = schemaYaml("trim", "collapse_whitespace", "lowercase")

        assertEquals(
            expected = listOf("trim", "collapse_whitespace", "lowercase"),
            actual = loadedNormalizers(yaml = yaml),
        )
    }

    /**
     * The counter-example `NormalizerRegistry.applyAll` documents: with `collapse_whitespace` between
     * them, `trim` first and `lowercase` first are not the same chain — so the two orders must be two
     * different files.
     */
    @Test
    fun `reordering the chain changes the file`() {
        val first = schemaYaml("trim", "collapse_whitespace", "lowercase")
        val second = schemaYaml("lowercase", "collapse_whitespace", "trim")

        assertNotEquals(illegal = first, actual = second)
        assertEquals(
            expected = listOf("lowercase", "collapse_whitespace", "trim"),
            actual = loadedNormalizers(yaml = second),
        )
    }

    /** Every id the editor offers is one the registry accepts — otherwise the schema will not load. */
    @Test
    fun `every offered normalizer loads`() {
        val yaml = schemaYaml(*KnownNormalizers.toTypedArray())

        assertEquals(expected = KnownNormalizers, actual = loadedNormalizers(yaml = yaml))
    }
}
