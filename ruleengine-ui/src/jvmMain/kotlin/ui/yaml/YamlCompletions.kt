package ui.yaml

import ruleengine.core.domain.OperatorNames
import ruleengine.core.domain.dto.action.ActionArgType
import ruleengine.core.domain.dto.field.FieldType
import ui.autocompletion.model.CompletionItem
import ui.autocompletion.model.CompletionKind
import ui.schema.KnownNormalizers
import ui.yaml.model.YamlCursorContext
import ui.yaml.model.YamlEditorType

// What the YAML editors offer at a given cursor position. Split from the highlighter, which
// colours the same documents but shares none of this logic: one answers "what can go here",
// the other "what is already here".

private val FIELD_TYPE_VALUES = FieldType.entries.map { type -> type.name.lowercase() }

private val FORMAT_VALUES = listOf("dd.MM.yyyy", "yyyy/MM/dd", "dd.MM.yyyy HH:mm", "yyyy-MM-dd HH:mm:ss")

private val NORMALIZER_VALUES = KnownNormalizers

private val OPERATOR_VALUES = OperatorNames.ALL

private val ARG_TYPE_VALUES = ActionArgType.entries.map { argType -> argType.name.lowercase() }

private val FIELD_SCHEMA_TOP_KEYS = listOf("schema", "fields")

private val ACTION_SCHEMA_TOP_KEYS = listOf("actions")

private val FIELD_DEF_KEYS = listOf("type", "alias", "format", "normalizers", "operators", "fields")

private val ACTION_DEF_KEYS = listOf("argTypes")

// ── YAML completion builder ───────────────────────────────────────────────────

/**
 * Returns context-appropriate YAML completion items for [context] and [editorType].
 */
/** Depth of a definition's own properties: `fields:` (0) → field name (2) → property (4). */
private const val DEFINITION_KEY_INDENT = 4

/** Depth of a field's own name, directly under `fields:`. */
private const val FIELD_NAME_INDENT = 2

fun buildYamlCompletions(
    context: YamlCursorContext,
    editorType: YamlEditorType,
): List<CompletionItem> =
    // Values first: on a `type:` line at indent 4, the field types win over the definition keys
    // that the same indent would otherwise offer.
    valueCompletions(context = context)
        ?: keyCompletions(context = context, editorType = editorType)
        ?: emptyList()

/** Completions for the right-hand side of a `key:` line, or a `- ` item. Null when neither. */
private fun valueCompletions(context: YamlCursorContext): List<CompletionItem>? = when {
    context.isValue && context.currentKey == "type" -> literals(values = FIELD_TYPE_VALUES, hint = "field type")
    context.isValue && context.currentKey == "format" -> literals(values = FORMAT_VALUES, hint = "date format")
    context.isListItem && context.parentKey == "normalizers" ->
        literals(values = NORMALIZER_VALUES, hint = "normalizer")

    context.isListItem && context.parentKey == "operators" ->
        OPERATOR_VALUES.map { value ->
            CompletionItem(label = value, insertText = value, kind = CompletionKind.OPERATOR, hint = "operator")
        }

    context.isListItem && context.parentKey == "argTypes" -> literals(values = ARG_TYPE_VALUES, hint = "arg type")
    else -> null
}

/** Completions for a key position, which depend on nesting depth and which document is open. */
private fun keyCompletions(context: YamlCursorContext, editorType: YamlEditorType): List<CompletionItem>? {
    val isFieldSchema = editorType == YamlEditorType.FIELD_SCHEMA
    return when {
        context.currentIndent == DEFINITION_KEY_INDENT && isFieldSchema ->
            keys(names = FIELD_DEF_KEYS, suffix = ":", hint = "field property")

        // `argTypes` is always a list, so the suffix saves the author typing the brackets.
        context.currentIndent == DEFINITION_KEY_INDENT && !isFieldSchema ->
            keys(names = ACTION_DEF_KEYS, suffix = ": []", hint = "action property")

        context.currentIndent == 0 && isFieldSchema ->
            keys(names = FIELD_SCHEMA_TOP_KEYS, suffix = ": ", hint = "schema key")

        context.currentIndent == 0 && !isFieldSchema ->
            keys(names = ACTION_SCHEMA_TOP_KEYS, suffix = ":", hint = "schema key")

        context.currentIndent == FIELD_NAME_INDENT && context.parentKey == "fields" && isFieldSchema ->
            keys(names = FIELD_SCHEMA_TOP_KEYS, suffix = ": ", hint = "field property")

        else -> null
    }
}

private fun literals(values: List<String>, hint: String): List<CompletionItem> =
    values.map { value ->
        CompletionItem(label = value, insertText = value, kind = CompletionKind.LITERAL, hint = hint)
    }

private fun keys(names: List<String>, suffix: String, hint: String): List<CompletionItem> =
    names.map { name ->
        CompletionItem(label = name, insertText = name + suffix, kind = CompletionKind.KEYWORD, hint = hint)
    }
