package ui.dock

import ruleengine.core.domain.dto.field.FieldType
import ui.actions.ActionSchemaYamlBridge
import ui.actions.model.ActionEditorState
import ui.actions.model.EditableAction
import ui.manifest.ManifestYamlBridge
import ui.manifest.model.EditableManifestEntry
import ui.manifest.model.ManifestEditorState
import ui.schema.FieldSchemaYamlBridge
import ui.schema.model.EditableField
import ui.schema.model.SchemaEditorState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Driven through the real bridges, never through hand-written YAML.
 *
 * These functions encode the writers' layout. Asserting against a literal string here would let a
 * writer change its indentation and leave the highlight landing on the wrong lines with every test
 * still green — the failure mode worth designing the test against.
 */
class YamlRangesTest {

    // ── the schema ────────────────────────────────────────────────────────────

    private val schema = SchemaEditorState(
        schemaName = "loan-decisioning-v1",
        fields = listOf(
            EditableField(
                path = "product",
                type = FieldType.TEXT,
                normalizers = listOf("trim", "lowercase"),
                operators = listOf("equals", "in"),
            ),
            EditableField(path = "creditScore", type = FieldType.INTEGER, operators = listOf("gt")),
            EditableField(
                path = "existingLoans",
                type = FieldType.COLLECTION,
                fields = listOf(
                    // Deliberately the same leaf name as a member of `applicant` below, at the same
                    // depth: the range must be scoped to its parent, not found by name alone.
                    EditableField(path = "lender", type = FieldType.TEXT, operators = listOf("equals")),
                    EditableField(path = "monthlyPayment", type = FieldType.DECIMAL),
                ),
            ),
            EditableField(
                path = "applicant",
                type = FieldType.OBJECT,
                fields = listOf(
                    EditableField(path = "lender", type = FieldType.TEXT),
                    EditableField(path = "country", type = FieldType.TEXT),
                ),
            ),
        ),
    )

    private val schemaYaml: String = FieldSchemaYamlBridge.toYaml(state = schema)

    private fun sliceOf(yaml: String, range: IntRange): String = yaml.substring(range.first, range.last + 1)

    @Test
    fun `a top level field covers its header and everything under it`() {
        val range = assertNotNull(actual = schemaFieldRange(yaml = schemaYaml, dottedPath = "product"))
        val slice = sliceOf(yaml = schemaYaml, range = range)

        assertTrue(actual = slice.startsWith(prefix = "  product:"), message = slice)
        assertTrue(actual = slice.contains(other = "type: text"), message = slice)
        assertTrue(actual = slice.contains(other = "- lowercase"), message = slice)
        // and stops before the next sibling
        assertTrue(actual = !slice.contains(other = "creditScore"), message = slice)
    }

    @Test
    fun `a structure field covers its nested members too`() {
        val range = assertNotNull(actual = schemaFieldRange(yaml = schemaYaml, dottedPath = "existingLoans"))
        val slice = sliceOf(yaml = schemaYaml, range = range)

        assertTrue(actual = slice.startsWith(prefix = "  existingLoans:"), message = slice)
        assertTrue(actual = slice.contains(other = "monthlyPayment"), message = slice)
        assertTrue(actual = !slice.contains(other = "applicant"), message = slice)
    }

    @Test
    fun `a nested member is found inside its own parent, not by name`() {
        val underLoans = assertNotNull(
            actual = schemaFieldRange(yaml = schemaYaml, dottedPath = "existingLoans.lender"),
        )
        val underApplicant = assertNotNull(
            actual = schemaFieldRange(yaml = schemaYaml, dottedPath = "applicant.lender"),
        )

        assertTrue(
            actual = underLoans.first < underApplicant.first,
            message = "both resolved to the same block, so the walk is not scoped to the parent",
        )
        assertTrue(actual = sliceOf(schemaYaml, underLoans).contains(other = "equals"))
        assertTrue(actual = !sliceOf(schemaYaml, underApplicant).contains(other = "equals"))

        // each sits inside its parent's range
        val loans = assertNotNull(actual = schemaFieldRange(yaml = schemaYaml, dottedPath = "existingLoans"))
        assertTrue(actual = underLoans.first in loans && underLoans.last in loans)
    }

    @Test
    fun `a member the parent does not declare resolves to nothing`() {
        assertNull(actual = schemaFieldRange(yaml = schemaYaml, dottedPath = "existingLoans.country"))
        assertNull(actual = schemaFieldRange(yaml = schemaYaml, dottedPath = "nope"))
        assertNull(actual = schemaFieldRange(yaml = schemaYaml, dottedPath = ""))
    }

    /** The bridge writes a blank line after `schema:`; it must not end the `fields:` block. */
    @Test
    fun `the blank line the bridge writes does not truncate a block`() {
        assertTrue(actual = schemaYaml.contains(other = "\n\n"), message = "the bridge stopped emitting a blank line")
        assertNotNull(actual = schemaFieldRange(yaml = schemaYaml, dottedPath = "product"))
    }

    // ── the actions ───────────────────────────────────────────────────────────

    @Test
    fun `an action covers its purpose and its argTypes`() {
        val yaml = ActionSchemaYamlBridge.toYaml(
            state = ActionEditorState(
                actions = listOf(
                    EditableAction(name = "decision", argTypes = listOf("string"), purpose = "the call"),
                    EditableAction(name = "audit", argTypes = listOf("string", "integer")),
                ),
            ),
        )

        val range = assertNotNull(actual = actionRange(yaml = yaml, name = "audit"))
        val slice = sliceOf(yaml = yaml, range = range)

        assertTrue(actual = slice.startsWith(prefix = "  audit:"), message = slice)
        assertTrue(actual = slice.contains(other = "- string") && slice.contains(other = "- integer"), message = slice)
        assertTrue(actual = !slice.contains(other = "decision"), message = slice)
        assertNull(actual = actionRange(yaml = yaml, name = "missing"))
    }

    // ── the manifest ──────────────────────────────────────────────────────────

    private val manifest = ManifestEditorState(
        name = "loan-decisioning",
        entries = listOf(
            EditableManifestEntry(
                id = "first",
                schemaPath = "schema.yaml",
                actionsPath = "actions.yaml",
                rulePaths = listOf("rules/a.rule", "rules/b.rule"),
            ),
            EditableManifestEntry(
                id = "second",
                schemaPath = "schema.yaml",
                actionsPath = "actions.yaml",
                rulePaths = listOf("rules/c.rule"),
                scope = "existingLoans",
            ),
        ),
    )

    @Test
    fun `an entry covers its own keys and stops at the next sequence item`() {
        val yaml = ManifestYamlBridge.toYaml(state = manifest)

        val first = assertNotNull(actual = manifestEntryRange(yaml = yaml, entryId = "first"))
        val firstSlice = sliceOf(yaml = yaml, range = first)
        assertTrue(actual = firstSlice.trimStart().startsWith(prefix = "- id: first"), message = firstSlice)
        assertTrue(actual = firstSlice.contains(other = "rules/b.rule"), message = firstSlice)
        assertTrue(actual = !firstSlice.contains(other = "second"), message = firstSlice)

        val second = assertNotNull(actual = manifestEntryRange(yaml = yaml, entryId = "second"))
        val secondSlice = sliceOf(yaml = yaml, range = second)
        assertTrue(actual = secondSlice.contains(other = "existingLoans"), message = secondSlice)
        assertTrue(actual = !secondSlice.contains(other = "rules/a.rule"), message = secondSlice)

        assertTrue(actual = first.last < second.first, message = "the two entries overlap")
        assertNull(actual = manifestEntryRange(yaml = yaml, entryId = "third"))
    }

    /** The last entry has no following item to stop at, so it runs to the end of the document. */
    @Test
    fun `the last entry runs to the end of the file`() {
        val yaml = ManifestYamlBridge.toYaml(state = manifest)
        val second = assertNotNull(actual = manifestEntryRange(yaml = yaml, entryId = "second"))

        assertEquals(
            expected = yaml.trimEnd().length,
            actual = sliceOf(yaml = yaml, range = second).let { slice -> second.first + slice.trimEnd().length },
        )
    }

    @Test
    fun `an empty document resolves to nothing everywhere`() {
        assertNull(actual = schemaFieldRange(yaml = "", dottedPath = "a"))
        assertNull(actual = actionRange(yaml = "", name = "a"))
        assertNull(actual = manifestEntryRange(yaml = "", entryId = "a"))
    }
}
