package ui.workbench

import kotlinx.coroutines.CoroutineScope
import ui.editor.rules.RuleEditorState
import ui.editor.rules.model.StatusKind
import ui.samples.model.LoadedSample
import ui.samples.model.SampleCategory
import ui.samples.model.SampleDescriptor
import ui.workbench.areas.applySample
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Loading a sample replaces every part of the editor at once.
 *
 * This was an eleven-statement block inlined in a click handler and therefore untestable. The order
 * of those statements is load-bearing — see the KDoc on `applySample` — so these tests pin the
 * resulting state rather than any single write.
 */
class ApplySampleTest {

    private val descriptor = SampleDescriptor(
        id = "demo-v1",
        name = "Demo",
        description = "d",
        category = SampleCategory.entries.first(),
        schemaResPath = "s",
        actionsResPath = "a",
        ruleResPaths = listOf("r"),
    )

    private val loaded = LoadedSample(
        descriptor = descriptor,
        schemaYaml = """
            schema: demo-v1
            fields:
              amount:
                type: decimal
                operators: [gte, lte]
        """.trimIndent(),
        actionsYaml = """
            actions:
              label:
                argTypes: [string]
        """.trimIndent(),
        rulesText = "rule \"big\" {\n  when\n    amount >= 500\n  then\n    label \"big\"\n}",
    )

    private fun state() = RuleEditorState(scope = CoroutineScope(EmptyCoroutineContext))

    @Test
    fun `the sample's three documents all land in the editor`() {
        val state = state()

        state.applySample(descriptor = descriptor, loaded = loaded)

        assertEquals(expected = loaded.schemaYaml, actual = state.schemaText.value)
        assertEquals(expected = loaded.schemaYaml, actual = state.schemaFieldValue.value.text)
        assertEquals(expected = loaded.actionsYaml, actual = state.actionSchemaText.value)
        assertEquals(expected = loaded.actionsYaml, actual = state.actionFieldValue.value.text)
        assertEquals(expected = loaded.rulesText, actual = state.ruleValue.value.text)
    }

    /**
     * Both parses run inside `applySample`, so the rule text never arrives without a schema to
     * validate it against.
     */
    @Test
    fun `both schemas are parsed, not just stored as text`() {
        val state = state()

        state.applySample(descriptor = descriptor, loaded = loaded)

        assertNotNull(actual = state.parsedSchema.value)
        assertNotNull(actual = state.parsedActionSchema.value)
        assertEquals(expected = "demo-v1", actual = state.parsedSchema.value!!.name)
        assertTrue(actual = state.parsedSchema.value!!.fields.isNotEmpty())
    }

    /**
     * The previous sample's diagnostics have to go, or they flash up against rules they were never
     * about until the debounced pass catches up.
     */
    @Test
    fun `the previous sample's diagnostics are cleared`() {
        val state = state()
        state.diagnosticsText.value = "stale"

        state.applySample(descriptor = descriptor, loaded = loaded)

        assertTrue(actual = state.diagnosticsList.value.isEmpty())
        assertEquals(expected = "", actual = state.diagnosticsText.value)
    }

    @Test
    fun `the status line names the sample`() {
        val state = state()

        state.applySample(descriptor = descriptor, loaded = loaded)

        assertEquals(expected = "Loaded sample: Demo", actual = state.status.value)
        assertEquals(expected = StatusKind.SUCCESS, actual = state.statusKind.value)
    }

    /** An unparseable sample still loads its text; only the parse comes back null. */
    @Test
    fun `a broken schema leaves the text in place and the parse empty`() {
        val state = state()

        state.applySample(
            descriptor = descriptor,
            loaded = loaded.copy(schemaYaml = "fields: [ this is not a schema"),
        )

        assertEquals(expected = "fields: [ this is not a schema", actual = state.schemaText.value)
        assertNull(actual = state.parsedSchema.value)
        assertEquals(expected = loaded.rulesText, actual = state.ruleValue.value.text, message = "rules still load")
    }
}
