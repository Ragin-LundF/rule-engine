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
            if (!manifest.name.isNullOrBlank()) {
                appendLine("name: ${manifest.name}")
            }
            appendLine("entries:")
            manifest.entries.forEach { entry ->
                appendLine("  - id: ${entry.id}")
                entry.schema?.let { schema -> appendLine("    schema: $schema") }
                entry.actions?.let { actions -> appendLine("    actions: $actions") }
                if (entry.rules.isNotEmpty()) {
                    appendLine("    rules:")
                    entry.rules.forEach { rule -> appendLine("      - $rule") }
                }
            }
        }
    }
}
