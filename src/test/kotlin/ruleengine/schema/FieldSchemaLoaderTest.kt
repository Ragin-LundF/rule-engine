package ruleengine.schema

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import ruleengine.core.errors.SchemaLoadException
import ruleengine.core.domain.FieldType
import java.nio.file.Path

class FieldSchemaLoaderTest {
    @Test
    fun `loads sample schema`() {
        val path = Path.of("src/test/resources/sample-schema.yaml")
        val schema = FieldSchemaLoader.load(path = path)

        assertEquals(expected = "transaction-v1", actual = schema.name)
        val purpose = schema.fields[ruleengine.core.domain.FieldId("purpose")]!!
        assertEquals(expected = FieldType.TEXT, actual = purpose.type)

        val amount = schema.fields[ruleengine.core.domain.FieldId("amount")]!!
        assertEquals(expected = FieldType.DECIMAL, actual = amount.type)
    }

    @Test
    fun `missing file throws SchemaLoadException`() {
        val path = Path.of("src/test/resources/nonexistent.yaml")
        assertFailsWith<SchemaLoadException> { FieldSchemaLoader.load(path = path) }
    }
}

