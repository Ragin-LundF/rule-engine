package ui.schema

import ruleengine.core.domain.dto.field.FieldType
import ui.schema.model.EditableField
import ui.schema.model.SchemaEditorState
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
        assertEquals(FieldType.TEXT, purpose.type)
        assertEquals(listOf("trim", "lowercase"), purpose.normalizers)
        assertEquals(listOf("equals", "contains"), purpose.operators)

        val amount = state.fields.first { it.path == "amount" }
        assertEquals(FieldType.DECIMAL, amount.type)
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
                    type = FieldType.TEXT,
                    normalizers = listOf("trim", "lowercase"),
                    operators = listOf("equals", "contains"),
                ),
                EditableField(
                    path = "amount",
                    type = FieldType.DECIMAL,
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
        assertEquals(FieldType.TEXT, purpose.type)
        assertEquals(listOf("trim", "lowercase"), purpose.normalizers)
        assertEquals(listOf("equals", "contains"), purpose.operators)
    }

    @Test
    fun `toYaml skips fields with blank path`() {
        val state = SchemaEditorState(
            schemaName = "s",
            fields = listOf(
                EditableField(path = "", type = FieldType.TEXT),
                EditableField(path = "amount", type = FieldType.DECIMAL),
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
                EditableField(path = "purpose", alias = "desc", type = FieldType.TEXT),
            ),
        )
        val yaml = FieldSchemaYamlBridge.toYaml(state)
        assertTrue(yaml.contains("alias: desc"))

        val reloaded = FieldSchemaYamlBridge.fromYaml(yaml)
        assertEquals("desc", reloaded.fields.first().alias)
    }

    // ── nested structures ─────────────────────────────────────────────────────

    @Test
    fun `nested collections and objects survive a yaml round-trip at three levels`() {
        val state = SchemaEditorState(
            schemaName = "nested",
            fields = listOf(
                EditableField(
                    path = "orders",
                    type = FieldType.COLLECTION,
                    fields = listOf(
                        EditableField(
                            path = "status",
                            type = FieldType.TEXT,
                            operators = listOf("equals"),
                        ),
                        EditableField(
                            path = "customer",
                            type = FieldType.OBJECT,
                            fields = listOf(EditableField(path = "country", type = FieldType.TEXT)),
                        ),
                        EditableField(
                            path = "items",
                            type = FieldType.COLLECTION,
                            fields = listOf(EditableField(path = "price", type = FieldType.DECIMAL)),
                        ),
                    ),
                ),
            ),
        )

        val yaml = FieldSchemaYamlBridge.toYaml(state)
        val reloaded = FieldSchemaYamlBridge.fromYaml(yaml)

        val orders = reloaded.fields.single()
        assertEquals(FieldType.COLLECTION, orders.type)
        assertEquals(setOf("status", "customer", "items"), orders.fields.map { it.path }.toSet())

        val items = orders.fields.first { it.path == "items" }
        assertEquals(FieldType.COLLECTION, items.type)
        assertEquals(FieldType.DECIMAL, items.fields.single().type)
        assertEquals("price", items.fields.single().path)

        val customer = orders.fields.first { it.path == "customer" }
        assertEquals(FieldType.OBJECT, customer.type)
        assertEquals("country", customer.fields.single().path)
    }

    @Test
    fun `nested fields are omitted for scalar types`() {
        val state = SchemaEditorState(
            fields = listOf(
                EditableField(
                    path = "amount",
                    type = FieldType.DECIMAL,
                    // A leftover nested list from a type change must not be written out, because the
                    // loader rejects nested fields on a scalar type.
                    fields = listOf(EditableField(path = "stale", type = FieldType.TEXT)),
                ),
            ),
        )

        val yaml = FieldSchemaYamlBridge.toYaml(state)

        assertFalse(yaml.contains("stale"))
        assertTrue(FieldSchemaYamlBridge.fromYaml(yaml).fields.single().fields.isEmpty())
    }

    // ── date formats ──────────────────────────────────────────────────────────

    @Test
    fun `date_time fields parse from yaml`() {
        val yaml = """
            schema: temporal
            fields:
              bookedAt:
                type: date_time
        """.trimIndent()

        assertEquals(FieldType.DATE_TIME, FieldSchemaYamlBridge.fromYaml(yaml).fields.single().type)
    }

    @Test
    fun `a declared format round-trips through toYaml and fromYaml`() {
        val state = SchemaEditorState(
            schemaName = "formats",
            fields = listOf(
                EditableField(path = "dueDate", type = FieldType.DATE, format = "dd.MM.yyyy"),
                EditableField(path = "eventAt", type = FieldType.DATE_TIME, format = "dd.MM.yyyy HH:mm"),
                EditableField(path = "createdAt", type = FieldType.DATE),
            ),
        )

        val reloaded = FieldSchemaYamlBridge.fromYaml(FieldSchemaYamlBridge.toYaml(state))

        assertEquals("dd.MM.yyyy", reloaded.fields.first { it.path == "dueDate" }.format)
        assertEquals("dd.MM.yyyy HH:mm", reloaded.fields.first { it.path == "eventAt" }.format)
        assertEquals("", reloaded.fields.first { it.path == "createdAt" }.format)
    }

    @Test
    fun `format is omitted for non-temporal types`() {
        val state = SchemaEditorState(
            fields = listOf(
                // A leftover format from a type change must not be written out, because the loader
                // rejects `format` on anything but a date type.
                EditableField(path = "purpose", type = FieldType.TEXT, format = "dd.MM.yyyy"),
            ),
        )

        val yaml = FieldSchemaYamlBridge.toYaml(state)

        assertFalse(yaml.contains("format"))
        assertFalse(FieldSchemaYamlBridge.fromYaml(yaml).isReadOnly, "emitted yaml must still load")
    }
}
