package ui.schema

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FieldSchemaYamlBridgeTest {

    // ── fromYaml ──────────────────────────────────────────────────────────────

    @Test
    fun `blank yaml returns empty non-readonly state`() {
        val state = FieldSchemaYamlBridge.fromYaml("")
        assertEquals(SchemaEditorState.Empty, state)
        assertFalse(state.isReadOnly)
    }

    @Test
    fun `simple yaml parses fields correctly`() {
        val yaml = """
            schema: test-schema
            fields:
              purpose:
                type: text
                normalizers:
                  - trim
                  - lowercase
                operators:
                  - equals
                  - contains
              amount:
                type: decimal
                operators:
                  - gte
                  - lte
        """.trimIndent()

        val state = FieldSchemaYamlBridge.fromYaml(yaml)

        assertFalse(state.isReadOnly)
        assertEquals("test-schema", state.schemaName)
        assertEquals(2, state.fields.size)

        val purpose = state.fields.first { it.path == "purpose" }
        assertEquals(SchemaFieldType.TEXT, purpose.type)
        assertEquals(listOf("trim", "lowercase"), purpose.normalizers)
        assertEquals(listOf("equals", "contains"), purpose.operators)

        val amount = state.fields.first { it.path == "amount" }
        assertEquals(SchemaFieldType.DECIMAL, amount.type)
        assertTrue(amount.normalizers.isEmpty())
    }

    @Test
    fun `yaml with top-level normalizers block is read-only`() {
        val yaml = """
            schema: custom
            normalizers:
              my_norm:
                - trim
            fields:
              purpose:
                type: text
        """.trimIndent()

        val state = FieldSchemaYamlBridge.fromYaml(yaml)
        assertTrue(state.isReadOnly)
    }

    @Test
    fun `invalid yaml returns read-only empty state`() {
        val state = FieldSchemaYamlBridge.fromYaml("not: valid: yaml: [[[")
        assertTrue(state.isReadOnly)
    }

    // ── toYaml ────────────────────────────────────────────────────────────────

    @Test
    fun `empty state produces empty yaml`() {
        val yaml = FieldSchemaYamlBridge.toYaml(SchemaEditorState.Empty)
        assertEquals("", yaml)
    }

    @Test
    fun `toYaml round-trips through FieldSchemaLoader`() {
        val state = SchemaEditorState(
            schemaName = "round-trip",
            fields = listOf(
                EditableField(
                    path = "purpose",
                    type = SchemaFieldType.TEXT,
                    normalizers = listOf("trim", "lowercase"),
                    operators = listOf("equals", "contains"),
                ),
                EditableField(
                    path = "amount",
                    type = SchemaFieldType.DECIMAL,
                    operators = listOf("gte", "lte"),
                ),
            ),
        )

        val yaml = FieldSchemaYamlBridge.toYaml(state)
        assertTrue(yaml.isNotBlank())

        val reloaded = FieldSchemaYamlBridge.fromYaml(yaml)
        assertFalse(reloaded.isReadOnly)
        assertEquals("round-trip", reloaded.schemaName)
        assertEquals(2, reloaded.fields.size)

        val purpose = reloaded.fields.first { it.path == "purpose" }
        assertEquals(SchemaFieldType.TEXT, purpose.type)
        assertEquals(listOf("trim", "lowercase"), purpose.normalizers)
        assertEquals(listOf("equals", "contains"), purpose.operators)
    }

    @Test
    fun `toYaml skips fields with blank path`() {
        val state = SchemaEditorState(
            schemaName = "s",
            fields = listOf(
                EditableField(path = "", type = SchemaFieldType.TEXT),
                EditableField(path = "amount", type = SchemaFieldType.DECIMAL),
            ),
        )
        val yaml = FieldSchemaYamlBridge.toYaml(state)
        assertFalse(yaml.contains("type: text"))
        assertTrue(yaml.contains("amount:"))
    }

    @Test
    fun `alias is included in generated yaml when non-blank`() {
        val state = SchemaEditorState(
            fields = listOf(
                EditableField(path = "purpose", alias = "desc", type = SchemaFieldType.TEXT),
            ),
        )
        val yaml = FieldSchemaYamlBridge.toYaml(state)
        assertTrue(yaml.contains("alias: desc"))

        val reloaded = FieldSchemaYamlBridge.fromYaml(yaml)
        assertEquals("desc", reloaded.fields.first().alias)
    }
}
