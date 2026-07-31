package ruleengine.schema

import ruleengine.compiler.operators.OperatorUtils
import ruleengine.core.domain.TemporalFormat
import ruleengine.core.domain.dto.FieldDefinition
import ruleengine.core.domain.dto.FieldId
import ruleengine.core.domain.dto.FieldSchema
import ruleengine.core.domain.dto.FieldType
import ruleengine.core.domain.dto.NormalizerId
import ruleengine.core.domain.dto.OperatorId
import ruleengine.core.domain.dto.isStructure
import ruleengine.core.domain.dto.isTemporal
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
        validateOperators(fieldName = fieldName, operators = operators)
        validateFormat(fieldName = fieldName, type = type, format = raw.format)

        // Recurse into nested members; a nested structure carries its own `fields`, so depth is unbounded.
        val nested = raw.fields?.let { rawNested ->
            if (!type.isStructure) {
                throw SchemaLoadException(
                    path = Path.of(fieldName.value),
                    details = "field '${fieldName.value}' declares nested 'fields' but its type is " +
                        "'${type.name.lowercase()}'; only 'collection' and 'object' can have nested fields"
                )
            }
            rawNested.entries.associate { (name, def) ->
                FieldId(value = name) to mapRawDefinition(fieldName = FieldId(value = name), raw = def)
            }
        } ?: emptyMap()

        return FieldDefinition(
            id = fieldName,
            type = type,
            alias = raw.alias,
            format = raw.format,
            normalizers = normalizers,
            operators = operators,
            fields = nested
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

    /**
     * Rejects an operator name the engine cannot compile.
     *
     * A declared `operators:` list is the field's whitelist, so a name the engine does not know silently
     * disables every condition on that field — the diagnostic then blames the rule
     * (`Operator 'startsWith' is not allowed for field 'x'. Allowed: [starts_with]`) rather than the typo
     * in the schema. Checking the type is deliberately left to `Validator`, which owns the per-type rule.
     */
    private fun validateOperators(fieldName: FieldId, operators: Set<OperatorId>) {
        val unknown = operators.filterNot { OperatorUtils.isKnownOperator(op = it.value) }
        if (unknown.isNotEmpty()) {
            throw SchemaLoadException(
                path = Path.of(fieldName.value),
                details = "Unknown operator '${unknown.first().value}' for field '${fieldName.value}'"
            )
        }
    }

    /**
     * Rejects a `format` that cannot work, because Jackson silently drops keys it does not know: without
     * this check a `format` on the wrong field type would look accepted and quietly do nothing.
     */
    private fun validateFormat(fieldName: FieldId, type: FieldType, format: String?) {
        if (format == null) {
            return
        }
        val problem = when {
            format.isBlank() -> "field '${fieldName.value}' declares an empty 'format'"

            !type.isTemporal -> "field '${fieldName.value}' declares 'format' but its type is " +
                "'${type.name.lowercase()}'; 'format' is only valid on 'date' and 'date_time' fields"

            else -> TemporalFormat.unusableReason(type = type, pattern = format)?.let { reason ->
                "field '${fieldName.value}' declares an invalid 'format' pattern '$format': $reason"
            }
        }
        if (problem != null) {
            throw SchemaLoadException(path = Path.of(fieldName.value), details = problem)
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
            "date_time", "datetime", "timestamp" -> FieldType.DATE_TIME
            "collection", "list", "array" -> FieldType.COLLECTION
            "object", "map" -> FieldType.OBJECT
            else -> throw IllegalArgumentException("Unknown field type: $s")
        }
    }
}

