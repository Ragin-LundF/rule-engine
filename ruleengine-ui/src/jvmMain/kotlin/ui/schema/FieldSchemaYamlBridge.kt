package ui.schema

import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.domain.dto.field.isStructure
import ruleengine.core.domain.dto.field.isTemporal
import ruleengine.schema.FieldSchemaLoader
import ui.schema.model.EditableField
import ui.schema.model.SchemaEditorState

/**
 * Converts between [SchemaEditorState] and YAML text understood by [FieldSchemaLoader].
 *
 * YAML remains the source of truth. The bridge only handles the common subset:
 * - schema name
 * - fields with type, alias, date format, normalizers, operators
 *
 * If the YAML contains unsupported constructs (e.g. top-level normalizer groups),
 * [fromYaml] returns a read-only state so the user can still see the data.
 */
object FieldSchemaYamlBridge {

    /**
     * Parses [yaml] into a [SchemaEditorState].
     * Returns [SchemaEditorState.Empty] with [SchemaEditorState.isReadOnly] = false on blank input.
     * Returns a read-only state when the YAML has unsupported constructs.
     */
    fun fromYaml(yaml: String): SchemaEditorState {
        if (yaml.isBlank()) return SchemaEditorState.Empty

        val schema: FieldSchema = runCatching {
            FieldSchemaLoader.loadFromString(content = yaml, nameHint = "schema")
        }.getOrElse {
            return SchemaEditorState.Empty.copy(isReadOnly = true)
        }

        val hasCustomNormalizerGroups = yaml.contains(Regex("^normalizers:", RegexOption.MULTILINE))

        val fields = schema.fields.values.map { it.toEditableField() }

        return SchemaEditorState(
            schemaName = schema.name,
            fields = fields,
            isReadOnly = hasCustomNormalizerGroups,
        )
    }

    /**
     * Serialises [state] to YAML text that [FieldSchemaLoader.loadFromString] can reload.
     * Returns an empty string when [state] has no fields.
     */
    fun toYaml(state: SchemaEditorState): String {
        if (state.fields.isEmpty()) return ""

        val sb = StringBuilder()
        if (state.schemaName.isNotBlank()) {
            sb.appendLine("schema: ${state.schemaName}")
            sb.appendLine()
        }
        sb.appendLine("fields:")
        appendFields(sb = sb, fields = state.fields, indent = "  ")
        return sb.toString()
    }

    /** Writes a list of fields, recursing into nested members with deeper indentation. */
    private fun appendFields(sb: StringBuilder, fields: List<EditableField>, indent: String) {
        for (field in fields) {
            if (field.path.isBlank()) continue
            val body = "$indent  "
            sb.appendLine("$indent${field.path}:")
            sb.appendLine("${body}type: ${field.type.yamlValue}")
            if (field.alias.isNotBlank()) {
                sb.appendLine("${body}alias: ${field.alias}")
            }
            // Quoted: a pattern can contain ':' (HH:mm) and quoted literals, which bare YAML would misread.
            if (field.type.isTemporal && field.format.isNotBlank()) {
                sb.appendLine("${body}format: \"${field.format}\"")
            }
            if (field.normalizers.isNotEmpty()) {
                sb.appendLine("${body}normalizers:")
                field.normalizers.forEach { sb.appendLine("$body  - $it") }
            }
            if (field.operators.isNotEmpty()) {
                sb.appendLine("${body}operators:")
                field.operators.forEach { sb.appendLine("$body  - $it") }
            }
            if (field.type.isStructure && field.fields.isNotEmpty()) {
                sb.appendLine("${body}fields:")
                appendFields(sb = sb, fields = field.fields, indent = "$body  ")
            }
        }
    }
}

/** Converts an engine definition to the editable row model, recursing into nested members. */
private fun FieldDefinition.toEditableField(): EditableField = EditableField(
    path = id.value,
    alias = alias ?: "",
    // The editor and the engine now share one FieldType, so this is carried straight across. It used
    // to be a lookup by lowercased name that fell back to TEXT, which silently downgraded any field
    // whose spelling drifted.
    type = type,
    format = format ?: "",
    normalizers = normalizers.map { it.value },
    operators = operators.map { it.value },
    fields = fields.values.map { it.toEditableField() },
)
