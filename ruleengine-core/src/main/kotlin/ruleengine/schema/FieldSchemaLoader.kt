package ruleengine.schema

import ruleengine.core.domain.FieldDefinition
import ruleengine.core.domain.FieldId
import ruleengine.core.domain.FieldSchema
import ruleengine.core.domain.FieldType
import ruleengine.core.domain.NormalizerId
import ruleengine.core.domain.OperatorId
import ruleengine.core.errors.SchemaLoadException
import ruleengine.jackson.JacksonUtil
import tools.jackson.dataformat.yaml.YAMLFactory
import tools.jackson.core.StreamReadFeature
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.DeserializationFeature
import tools.jackson.module.kotlin.KotlinModule
import tools.jackson.module.blackbird.BlackbirdModule
import ruleengine.schema.dto.RawFieldDefinition
import ruleengine.schema.dto.RawFieldSchema
import java.nio.file.Files
import java.nio.file.Path

object FieldSchemaLoader {
    private val mapper = JacksonUtil.jsonMapper

    @Throws(SchemaLoadException::class)
    fun load(path: Path): FieldSchema {
        if (!Files.exists(path)) throw SchemaLoadException(path = path, details = "file does not exist")
        try {
            val raw: RawFieldSchema = Files.newInputStream(path).use { ins ->
                val bytes = ins.readAllBytes()
                val yf = YAMLFactory.builder().configure(StreamReadFeature.IGNORE_UNDEFINED, true).build()
                yf.createParser(java.io.ByteArrayInputStream(bytes)).use { p -> mapper.readValue(p, RawFieldSchema::class.java) }
            }
            val fields = raw.fields.mapKeys { FieldId(it.key) }.mapValues { (k, v) ->
                mapRawDefinition(fieldName = k, raw = v)
            }
            return FieldSchema(name = raw.schema ?: path.fileName.toString(), fields = fields)
        } catch (ex: SchemaLoadException) {
            throw ex
        } catch (ex: Exception) {
            throw SchemaLoadException(path = path, details = ex.message ?: "unknown error")
        }
    }

    /** Load from a Reader (useful for UI or in-memory content). If nameHint is provided it will be used when the
     * raw schema doesn't include a schema name. */
    @Throws(SchemaLoadException::class)
    fun loadFromReader(reader: java.io.Reader, nameHint: String? = null): FieldSchema {
        try {
            val content = reader.use { it.readText() }
            val raw: RawFieldSchema = run {
                val bytes = content.toByteArray(Charsets.UTF_8)
                val yf = YAMLFactory.builder().configure(StreamReadFeature.IGNORE_UNDEFINED, true).build()
                yf.createParser(java.io.ByteArrayInputStream(bytes)).use { p -> mapper.readValue(p, RawFieldSchema::class.java) }
            }
            val fields = raw.fields.mapKeys { FieldId(it.key) }.mapValues { (k, v) ->
                mapRawDefinition(fieldName = k, raw = v)
            }
            return FieldSchema(name = raw.schema ?: nameHint ?: "schema", fields = fields)
        } catch (ex: SchemaLoadException) {
            throw ex
        } catch (ex: Exception) {
            throw SchemaLoadException(path = Path.of(nameHint ?: "schema"), details = ex.message ?: "unknown error")
        }
    }

    fun loadFromString(content: String, nameHint: String? = null): FieldSchema = loadFromReader(java.io.StringReader(content), nameHint)

    private fun mapRawDefinition(fieldName: FieldId, raw: RawFieldDefinition): FieldDefinition {
        val type = raw.type?.let { parseFieldType(it) }
            ?: throw SchemaLoadException(path = Path.of(fieldName.value), details = "missing type for field ${fieldName.value}")

        val normalizers = raw.normalizers?.map { NormalizerId(it) } ?: emptyList()
        val operators = raw.operators?.map { OperatorId(it) }?.toSet() ?: emptySet()

        return FieldDefinition(id = fieldName, type = type, normalizers = normalizers, operators = operators)
    }

    private fun parseFieldType(s: String): FieldType {
        return when (s.lowercase()) {
            "text", "string", "string" -> FieldType.TEXT
            "integer", "int", "long" -> FieldType.INTEGER
            "decimal", "bigdecimal", "number" -> FieldType.DECIMAL
            "boolean", "bool" -> FieldType.BOOLEAN
            "stringset", "string_set", "set" -> FieldType.STRING_SET
            "date" -> FieldType.DATE
            else -> throw IllegalArgumentException("Unknown field type: $s")
        }
    }
}

