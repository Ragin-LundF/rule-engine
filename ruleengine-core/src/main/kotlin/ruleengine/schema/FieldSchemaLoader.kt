package ruleengine.schema

import ruleengine.core.domain.FieldDefinition
import ruleengine.core.domain.FieldId
import ruleengine.core.domain.FieldSchema
import ruleengine.core.domain.FieldType
import ruleengine.core.domain.NormalizerId
import ruleengine.core.domain.OperatorId
import ruleengine.core.errors.SchemaLoadException
import ruleengine.core.io.FileInputSupport
import ruleengine.core.normalizer.NormalizerRegistry
import ruleengine.jackson.JacksonUtil
import ruleengine.schema.dto.RawFieldDefinition
import ruleengine.schema.dto.RawFieldSchema
import tools.jackson.core.ObjectReadContext
import tools.jackson.core.StreamReadFeature
import tools.jackson.dataformat.yaml.YAMLFactory
import java.io.Reader
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Path

object FieldSchemaLoader {
    private val mapper = JacksonUtil.jsonMapper

    @Throws(SchemaLoadException::class)
    fun load(path: Path): FieldSchema {
        if (!Files.exists(path)) {
            throw SchemaLoadException(path = path, details = "file does not exist")
        }

        return runCatching {
            val content = FileInputSupport.readBoundedText(path = path, kind = "schema")
            parseSchema(content = content)
        }.map { raw ->
            val fields = raw.fields.mapKeys { FieldId(value = it.key) }.mapValues { (k, v) ->
                mapRawDefinition(fieldName = k, raw = v)
            }
            FieldSchema(name = raw.schema ?: path.fileName.toString(), fields = fields)
        }.getOrElse { ex ->
            throw SchemaLoadException(path = path, details = ex.message ?: "unknown error")
        }
    }

    /** Load from a Reader (useful for UI or in-memory content). If nameHint is provided it will be used when the
     * raw schema doesn't include a schema name. */
    @Throws(SchemaLoadException::class)
    fun loadFromReader(reader: Reader, nameHint: String? = null): FieldSchema {
        val content = reader.use { it.readText() }
        return runCatching {
            val raw = parseSchema(content = content)
            val fields = raw.fields.mapKeys { FieldId(value = it.key) }.mapValues { (k, v) ->
                mapRawDefinition(fieldName = k, raw = v)
            }
            FieldSchema(name = raw.schema ?: nameHint ?: "schema", fields = fields)
        }.getOrElse { ex ->
            throw SchemaLoadException(path = Path.of(nameHint ?: "schema"), details = ex.message ?: "unknown error")
        }
    }

    fun loadFromString(content: String, nameHint: String? = null): FieldSchema {
        return loadFromReader(reader = StringReader(content), nameHint = nameHint)
    }

    private fun parseSchema(content: String): RawFieldSchema {
        val yf = YAMLFactory.builder().configure(StreamReadFeature.IGNORE_UNDEFINED, true).build()
        val bytes = content.toByteArray(Charsets.UTF_8)
        return yf.createParser(ObjectReadContext.empty(), bytes).use { p ->
            mapper.readValue(p, RawFieldSchema::class.java)
        }
    }

    private fun mapRawDefinition(fieldName: FieldId, raw: RawFieldDefinition): FieldDefinition {
        val type = raw.type?.let { parseFieldType(s = it) }
            ?: throw SchemaLoadException(
                path = Path.of(fieldName.value),
                details = "missing type for field ${fieldName.value}"
            )

        val normalizers = raw.normalizers?.map { NormalizerId(value = it) } ?: emptyList()
        validateNormalizers(fieldName = fieldName, normalizers = normalizers)
        val operators = raw.operators?.map { OperatorId(value = it) }?.toSet() ?: emptySet()

        return FieldDefinition(
            id = fieldName,
            type = type,
            alias = raw.alias,
            normalizers = normalizers,
            operators = operators
        )
    }

    private fun validateNormalizers(fieldName: FieldId, normalizers: List<NormalizerId>) {
        for (normalizerId in normalizers) {
            runCatching {
                NormalizerRegistry.default.get(id = normalizerId)
            }.getOrElse {
                throw SchemaLoadException(
                    path = Path.of(fieldName.value),
                    details = "Unknown normalizer '${normalizerId.value}' for field '${fieldName.value}'"
                )
            }
        }
    }

    private fun parseFieldType(s: String): FieldType {
        return when (s.lowercase()) {
            "text", "string" -> FieldType.TEXT
            "integer", "int", "long" -> FieldType.INTEGER
            "decimal", "bigdecimal", "number" -> FieldType.DECIMAL
            "boolean", "bool" -> FieldType.BOOLEAN
            "stringset", "string_set", "set" -> FieldType.STRING_SET
            "date" -> FieldType.DATE
            else -> throw IllegalArgumentException("Unknown field type: $s")
        }
    }
}

