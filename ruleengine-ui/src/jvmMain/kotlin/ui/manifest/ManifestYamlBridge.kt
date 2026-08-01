package ui.manifest

import ruleengine.manifest.ManifestLoader
import ruleengine.manifest.ProjectManifest

/**
 * Converts between editable manifest state and the core [ProjectManifest] YAML format.
 */
object ManifestYamlBridge {

    fun fromYaml(yaml: String): ManifestEditorState {
        if (yaml.isBlank()) {
            return ManifestEditorState(
                name = "",
                entries = emptyList(),
            )
        }
        val project = runCatching { ManifestLoader.loadFromString(content = yaml) }.getOrNull()
            ?: return ManifestEditorState(
                name = "",
                entries = emptyList(),
                isReadOnly = true,
            )
        return ManifestEditorState(
            name = project.name ?: "",
            entries = project.entries.map { entry ->
                EditableManifestEntry(
                    id = entry.id,
                    schemaPath = entry.schema ?: "",
                    actionsPath = entry.actions ?: "",
                    rulePaths = entry.rules,
                )
            },
        )
    }

    fun toYaml(state: ManifestEditorState): String {
        val nonEmptyEntries = state.entries.filter {
            it.schemaPath.isNotBlank() || it.actionsPath.isNotBlank() || it.rulePaths.isNotEmpty()
        }
        val manifest = ProjectManifest(
            name = state.name.takeIf { it.isNotBlank() },
            entries = nonEmptyEntries.map {
                ruleengine.manifest.ManifestEntry(
                    id = it.id.ifBlank { "default" },
                    schema = it.schemaPath.takeIf { path -> path.isNotBlank() },
                    actions = it.actionsPath.takeIf { path -> path.isNotBlank() },
                    rules = it.rulePaths.filter { path -> path.isNotBlank() },
                )
            },
        )
        return buildString {
            manifest.name?.takeIf { it.isNotBlank() }?.let { name ->
                appendLine("name: ${scalar(value = name)}")
            }
            appendLine("entries:")
            manifest.entries.forEach { entry ->
                appendLine("  - id: ${scalar(value = entry.id)}")
                entry.schema?.let { schema -> appendLine("    schema: ${scalar(value = schema)}") }
                entry.actions?.let { actions -> appendLine("    actions: ${scalar(value = actions)}") }
                if (entry.rules.isNotEmpty()) {
                    appendLine("    rules:")
                    entry.rules.forEach { rule -> appendLine("      - ${scalar(value = rule)}") }
                }
            }
        }
    }

    /**
     * Emits [value] as a YAML scalar, quoting it when a plain one would not survive a round trip.
     *
     * Paths reach here from a file dialog, so they can hold anything a filesystem allows: a comment
     * marker, a colon, a leading indicator character. Writing those unquoted produces a manifest the
     * loader cannot read back — a save that looks like it worked and breaks on the next open.
     */
    private fun scalar(value: String): String {
        val needsQuotes = value.isEmpty() ||
                value.first().isWhitespace() ||
                value.last().isWhitespace() ||
                value.first() in INDICATOR_CHARS ||
                value.contains(other = ": ") ||
                value.contains(other = " #") ||
                value.endsWith(suffix = ":")

        if (!needsQuotes) return value
        return "\"" + value.replace(oldValue = "\\", newValue = "\\\\").replace(oldValue = "\"", newValue = "\\\"") + "\""
    }

    private val INDICATOR_CHARS: Set<Char> = setOf(
        '-', '?', ':', ',', '[', ']', '{', '}', '#', '&', '*', '!', '|', '>', '\'', '"', '%', '@', '`',
    )
}
