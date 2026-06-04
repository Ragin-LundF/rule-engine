package ruleengine.schema

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import ruleengine.core.domain.FieldDefinition
import ruleengine.core.domain.FieldId
import ruleengine.core.domain.FieldSchema
import ruleengine.core.domain.FieldType
import ruleengine.core.domain.NormalizerId
import ruleengine.core.domain.OperatorId
import ruleengine.core.errors.SchemaLoadException
import ruleengine.schema.dto.RawFieldDefinition
import ruleengine.schema.dto.RawFieldSchema
import java.nio.file.Files
import java.nio.file.Path

object FieldSchemaLoader {
    private val mapper = ObjectMapper(YAMLFactory())
        .registerKotlinModule()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    @Throws(SchemaLoadException::class)
    fun load(path: Path): FieldSchema {
        if (!Files.exists(path)) throw SchemaLoadException(path = path, details = "file does not exist")
        try {
            val raw: RawFieldSchema = Files.newBufferedReader(path).use { r -> mapper.readValue(r) }
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


