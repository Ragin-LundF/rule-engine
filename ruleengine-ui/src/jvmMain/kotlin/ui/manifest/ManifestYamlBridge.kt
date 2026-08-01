package ui.manifest

import ruleengine.manifest.ManifestLoader
import ruleengine.manifest.ProjectManifest
import ui.manifest.model.EditableManifestEntry
import ui.manifest.model.ManifestEditorState
import ui.util.YamlScalars

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
        // An entry is kept as soon as it has an id. Dropping the ones with no files yet would make a
        // freshly added entry disappear on save, before the user has had the chance to give it any.
        val nonEmptyEntries = state.entries.filter { it.id.isNotBlank() }
        val manifest = ProjectManifest(
            name = state.name.takeIf { it.isNotBlank() },
            entries = nonEmptyEntries.map {
                ruleengine.manifest.ManifestEntry(
                    id = it.id,
                    schema = it.schemaPath.takeIf { path -> path.isNotBlank() },
                    actions = it.actionsPath.takeIf { path -> path.isNotBlank() },
                    rules = it.rulePaths.filter { path -> path.isNotBlank() },
                )
            },
        )
        return buildString {
            manifest.name?.takeIf { it.isNotBlank() }?.let { name ->
                appendLine("name: ${YamlScalars.quoteIfNeeded(value = name)}")
            }
            // `entries:` with nothing under it reads back as null rather than an empty list, so a
            // manifest with no entries yet is written without the key at all.
            if (manifest.entries.isEmpty()) return@buildString
            appendLine("entries:")
            manifest.entries.forEach { entry ->
                appendLine("  - id: ${YamlScalars.quoteIfNeeded(value = entry.id)}")
                entry.schema?.let { schema -> appendLine("    schema: ${YamlScalars.quoteIfNeeded(value = schema)}") }
                entry.actions?.let { actions ->
                    appendLine("    actions: ${YamlScalars.quoteIfNeeded(value = actions)}")
                }
                if (entry.rules.isNotEmpty()) {
                    appendLine("    rules:")
                    entry.rules.forEach { rule -> appendLine("      - ${YamlScalars.quoteIfNeeded(value = rule)}") }
                }
            }
        }
    }
}
