package ui.schema

import ruleengine.core.domain.FieldSchema
import ruleengine.schema.FieldSchemaLoader

/**
 * Converts between [SchemaEditorState] and YAML text understood by [FieldSchemaLoader].
 *
 * YAML remains the source of truth. The bridge only handles the common subset:
 * - schema name
 * - fields with type, alias, normalizers, operators
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

        val fields = schema.fields.values.map { def ->
            EditableField(
                path = def.id.value,
                alias = def.alias ?: "",
                type = SchemaFieldType.entries.firstOrNull { it.yamlValue == def.type.name.lowercase() }
                    ?: SchemaFieldType.TEXT,
                normalizers = def.normalizers.map { it.value },
                operators = def.operators.map { it.value },
            )
        }

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
        for (field in state.fields) {
            if (field.path.isBlank()) continue
            sb.appendLine("  ${field.path}:")
            sb.appendLine("    type: ${field.type.yamlValue}")
            if (field.alias.isNotBlank()) {
                sb.appendLine("    alias: ${field.alias}")
            }
            if (field.normalizers.isNotEmpty()) {
                sb.appendLine("    normalizers:")
                field.normalizers.forEach { sb.appendLine("      - $it") }
            }
            if (field.operators.isNotEmpty()) {
                sb.appendLine("    operators:")
                field.operators.forEach { sb.appendLine("      - $it") }
            }
        }
        return sb.toString()
    }
}
