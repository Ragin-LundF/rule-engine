package ruleengine.schema

import ruleengine.core.domain.dto.FieldId
import ruleengine.core.domain.dto.FieldType
import ruleengine.core.errors.SchemaLoadException
import ruleengine.core.io.FileInputSupport
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FieldSchemaLoaderTest {
    @Test
    fun `loads sample schema`() {
        val path = Path.of("src/test/resources/sample-schema.yaml")
        val schema = FieldSchemaLoader.load(path = path)

        assertEquals(expected = "transaction-v1", actual = schema.name)
        val purpose = schema.fields[ruleengine.core.domain.dto.FieldId("purpose")]!!
        assertEquals(expected = FieldType.TEXT, actual = purpose.type)

        val amount = schema.fields[ruleengine.core.domain.dto.FieldId("amount")]!!
        assertEquals(expected = FieldType.DECIMAL, actual = amount.type)
    }

    @Test
    fun `missing file throws SchemaLoadException`() {
        val path = Path.of("src/test/resources/nonexistent.yaml")
        assertFailsWith<SchemaLoadException> { FieldSchemaLoader.load(path = path) }
    }

    @Test
    fun `invalid normalizer throws SchemaLoadException`() {
        val path = Files.createTempFile("invalid-normalizer", ".yaml")
        Files.writeString(
            path,
            """
                schema: invalid-normalizer
                fields:
                  purpose:
                    type: text
                    normalizers:
                      - does_not_exist
            """.trimIndent()
        )

        val exception = assertFailsWith<SchemaLoadException> {
            FieldSchemaLoader.load(path = path)
        }

        assertTrue(actual = exception.details.contains(other = "Unknown normalizer"))
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

    // --- nested structure fields ---

    @Test
    fun `loads collections and objects nested three levels deep`() {
        val schema = FieldSchemaLoader.loadFromString(
            content = """
                schema: nested-v1
                fields:
                  orders:
                    type: collection
                    fields:
                      status:
                        type: text
                      customer:
                        type: object
                        fields:
                          country:
                            type: text
                      items:
                        type: collection
                        fields:
                          price:
                            type: decimal
                          sku:
                            type: text
            """.trimIndent()
        )

        val orders = schema.fields[FieldId(value = "orders")]!!
        assertEquals(expected = FieldType.COLLECTION, actual = orders.type)

        val items = orders.fields[FieldId(value = "items")]!!
        assertEquals(expected = FieldType.COLLECTION, actual = items.type)
        assertEquals(
            expected = FieldType.DECIMAL,
            actual = items.fields[FieldId(value = "price")]!!.type
        )
        assertEquals(
            expected = FieldType.TEXT,
            actual = items.fields[FieldId(value = "sku")]!!.type
        )

        val customer = orders.fields[FieldId(value = "customer")]!!
        assertEquals(expected = FieldType.OBJECT, actual = customer.type)
        assertEquals(
            expected = FieldType.TEXT,
            actual = customer.fields[FieldId(value = "country")]!!.type
        )
    }

    @Test
    fun `list and array are aliases for collection, map for object`() {
        val schema = FieldSchemaLoader.loadFromString(
            content = """
                schema: aliases-v1
                fields:
                  a:
                    type: list
                  b:
                    type: array
                  c:
                    type: map
            """.trimIndent()
        )

        assertEquals(expected = FieldType.COLLECTION, actual = schema.fields[FieldId(value = "a")]!!.type)
        assertEquals(expected = FieldType.COLLECTION, actual = schema.fields[FieldId(value = "b")]!!.type)
        assertEquals(expected = FieldType.OBJECT, actual = schema.fields[FieldId(value = "c")]!!.type)
    }

    @Test
    fun `nested fields on a scalar type throws SchemaLoadException`() {
        val exception = assertFailsWith<SchemaLoadException> {
            FieldSchemaLoader.loadFromString(
                content = """
                    schema: bad-nesting
                    fields:
                      amount:
                        type: decimal
                        fields:
                          nope:
                            type: text
                """.trimIndent()
            )
        }

        assertTrue(actual = exception.details.contains(other = "nested 'fields'"))
    }

    @Test
    fun `schema without nested declarations still loads with empty fields`() {
        val schema = FieldSchemaLoader.loadFromString(
            content = """
                schema: legacy-v1
                fields:
                  transactions:
                    type: string_set
            """.trimIndent()
        )

        val transactions = schema.fields[FieldId(value = "transactions")]!!
        assertEquals(expected = FieldType.STRING_SET, actual = transactions.type)
        assertTrue(actual = transactions.fields.isEmpty())
    }

    @Test
    fun `an unknown operator name throws SchemaLoadException`() {
        val exception = assertFailsWith<SchemaLoadException> {
            FieldSchemaLoader.loadFromString(
                content = """
                    schema: bad-operator
                    fields:
                      amount:
                        type: integer
                        operators:
                          - greaterThan
                """.trimIndent()
            )
        }

        assertTrue(
            actual = exception.details.contains(other = "Unknown operator 'greaterThan'"),
            message = exception.details,
        )
    }

    @Test
    fun `canonical, symbolic and legacy operator spellings all load`() {
        val schema = FieldSchemaLoader.loadFromString(
            content = """
                schema: operator-spellings
                fields:
                  purpose:
                    type: text
                    operators:
                      - equals
                      - startsWith
                      - starts_with
                      - regex
                  amount:
                    type: decimal
                    operators:
                      - ">="
                      - "!="
                      - between
            """.trimIndent()
        )

        assertEquals(expected = 2, actual = schema.fields.size)
    }

    @Test
    fun `datetime and timestamp are aliases for date_time`() {
        val schema = FieldSchemaLoader.loadFromString(
            content = """
                schema: temporal-aliases-v1
                fields:
                  a:
                    type: date_time
                  b:
                    type: datetime
                  c:
                    type: timestamp
            """.trimIndent()
        )

        assertEquals(expected = FieldType.DATE_TIME, actual = schema.fields[FieldId(value = "a")]!!.type)
        assertEquals(expected = FieldType.DATE_TIME, actual = schema.fields[FieldId(value = "b")]!!.type)
        assertEquals(expected = FieldType.DATE_TIME, actual = schema.fields[FieldId(value = "c")]!!.type)
    }

    @Test
    fun `format is loaded for date and date_time fields`() {
        val schema = FieldSchemaLoader.loadFromString(
            content = """
                schema: formats-v1
                fields:
                  dueDate:
                    type: date
                    format: "dd.MM.yyyy"
                  eventAt:
                    type: date_time
                    format: "dd.MM.yyyy HH:mm"
                  createdAt:
                    type: date
            """.trimIndent()
        )

        assertEquals(expected = "dd.MM.yyyy", actual = schema.fields[FieldId(value = "dueDate")]!!.format)
        assertEquals(expected = "dd.MM.yyyy HH:mm", actual = schema.fields[FieldId(value = "eventAt")]!!.format)
        assertNull(actual = schema.fields[FieldId(value = "createdAt")]!!.format)
    }

    @Test
    fun `format on a nested date member is loaded`() {
        val schema = FieldSchemaLoader.loadFromString(
            content = """
                schema: nested-format-v1
                fields:
                  orders:
                    type: collection
                    fields:
                      placedOn:
                        type: date
                        format: "dd.MM.yyyy"
            """.trimIndent()
        )

        val placedOn = schema.fields[FieldId(value = "orders")]!!.fields[FieldId(value = "placedOn")]!!
        assertEquals(expected = "dd.MM.yyyy", actual = placedOn.format)
    }

    @Test
    fun `format on a non-date field throws SchemaLoadException`() {
        val exception = assertFailsWith<SchemaLoadException> {
            FieldSchemaLoader.loadFromString(
                content = """
                    schema: bad-format-target
                    fields:
                      purpose:
                        type: text
                        format: "dd.MM.yyyy"
                """.trimIndent()
            )
        }

        assertTrue(
            actual = exception.details.contains(other = "only valid on 'date' and 'date_time'"),
            message = exception.details,
        )
    }

    @Test
    fun `an empty format throws SchemaLoadException`() {
        val exception = assertFailsWith<SchemaLoadException> {
            FieldSchemaLoader.loadFromString(
                content = """
                    schema: blank-format
                    fields:
                      dueDate:
                        type: date
                        format: "  "
                """.trimIndent()
            )
        }

        assertTrue(actual = exception.details.contains(other = "empty 'format'"), message = exception.details)
    }

    @Test
    fun `a malformed format pattern throws SchemaLoadException`() {
        val exception = assertFailsWith<SchemaLoadException> {
            FieldSchemaLoader.loadFromString(
                content = """
                    schema: malformed-format
                    fields:
                      dueDate:
                        type: date
                        format: "QQQQQQ"
                """.trimIndent()
            )
        }

        assertTrue(actual = exception.details.contains(other = "invalid 'format' pattern"), message = exception.details)
    }

    @Test
    fun `a format that cannot represent the field type throws SchemaLoadException`() {
        val incompleteDate = assertFailsWith<SchemaLoadException> {
            FieldSchemaLoader.loadFromString(
                content = """
                    schema: incomplete-date
                    fields:
                      dueDate:
                        type: date
                        format: "MM-dd"
                """.trimIndent()
            )
        }
        assertTrue(actual = incompleteDate.details.contains(other = "complete date"), message = incompleteDate.details)

        val missingTime = assertFailsWith<SchemaLoadException> {
            FieldSchemaLoader.loadFromString(
                content = """
                    schema: missing-time
                    fields:
                      eventAt:
                        type: date_time
                        format: "yyyy-MM-dd"
                """.trimIndent()
            )
        }
        assertTrue(
            actual = missingTime.details.contains(other = "complete date and time"),
            message = missingTime.details,
        )
    }
}

