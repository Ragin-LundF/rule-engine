package ruleengine.schema

import ruleengine.core.errors.SchemaLoadException
import ruleengine.core.io.FileInputSupport
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import ruleengine.core.domain.FieldType

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

    @Test
    fun `oversized schema file throws SchemaLoadException`() {
        val path = Files.createTempFile("oversized-schema", ".yaml")
        Files.writeString(path, oversizedContent())

        val exception = assertFailsWith<SchemaLoadException> {
            FieldSchemaLoader.load(path = path)
        }

        assertTrue(actual = exception.details.contains(other = "exceeds limit"))
    }

    private fun oversizedContent(): String {
        return "a".repeat(FileInputSupport.DEFAULT_MAX_BYTES.toInt() + 1)
    }
}

