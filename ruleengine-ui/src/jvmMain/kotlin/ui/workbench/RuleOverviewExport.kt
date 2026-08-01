package ui.workbench

import ruleengine.export.RuleCatalogBuilder
import ruleengine.export.docx.DocxCatalogWriter
import ruleengine.export.dto.ParsedRuleFile
import ruleengine.export.dto.RuleCatalog
import ruleengine.export.markdown.MarkdownCatalogRenderer
import ui.editor.rules.RuleEditorState
import java.time.LocalDate

/**
 * Turns the manifest entry the workbench has open into a rule-overview document.
 *
 * Lives beside the toolbar rather than inside it so the assembly is testable without a composition,
 * and so the two output formats stay one decision apart: everything up to [RuleCatalog] is shared,
 * and only the last step differs.
 */
object RuleOverviewExport {

    /** What the user picked from the export menu. */
    enum class Format(val label: String, val extension: String) {
        MARKDOWN(label = "Markdown (.md)", extension = "md"),
        WORD(label = "Word (.docx)", extension = "docx"),
    }

    /** A document ready to be written, or the reason there is nothing to write. */
    sealed interface Result {
        data class Ready(val fileName: String, val bytes: ByteArray, val ruleCount: Int) : Result {
            // ByteArray uses identity equality, which would make the data class's generated equals
            // lie. Nothing compares these, so the members are simply not generated.
            override fun equals(other: Any?): Boolean = this === other
            override fun hashCode(): Int = System.identityHashCode(this)
        }

        data class Unavailable(val reason: String) : Result
    }

    /**
     * Builds the document for the entry currently selected in [state].
     *
     * Entry-wide on purpose: which single rule file happens to be open is an editing detail, while
     * the document describes the whole rule set a customer will be evaluated against.
     */
    fun export(state: RuleEditorState, format: Format, today: LocalDate? = null): Result {
        val entryId = state.selectedManifestEntry.value
            ?: return Result.Unavailable(reason = "Open a manifest entry before exporting an overview")

        val entry = state.parsedManifest.value?.entries?.find { candidate -> candidate.id == entryId }
            ?: return Result.Unavailable(reason = "Manifest entry '$entryId' is no longer in the manifest")

        val files = state.parsedRuleFilesForCurrentEntry()
        if (files.all { file -> file.rules.isEmpty() }) {
            return Result.Unavailable(reason = "Entry '$entryId' has no rules to export")
        }

        val catalog = RuleCatalogBuilder.build(
            projectName = state.parsedManifest.value?.name,
            entryId = entryId,
            files = files.map { source ->
                ParsedRuleFile(relativePath = source.relativePath, rules = source.rules)
            },
            schema = state.parsedSchema.value,
            schemaPath = entry.schema,
            actionsPath = entry.actions,
        )

        return Result.Ready(
            fileName = "${fileStem(entryId = entryId, projectName = catalog.projectName)}.${format.extension}",
            bytes = render(catalog = catalog, format = format, today = today),
            ruleCount = catalog.rules.size,
        )
    }

    private fun render(catalog: RuleCatalog, format: Format, today: LocalDate?): ByteArray {
        return when (format) {
            Format.MARKDOWN -> MarkdownCatalogRenderer.render(catalog = catalog)
                .toByteArray(charset = Charsets.UTF_8)

            // Only the Word document is dated: it is handed over as a fixed artefact, while the
            // Markdown page is regenerated into a wiki that keeps its own history.
            Format.WORD -> DocxCatalogWriter.write(
                catalog = catalog,
                generatedOn = today?.toString(),
            )
        }
    }

    /** A file name the customer can recognise without opening it. */
    private fun fileStem(entryId: String, projectName: String?): String {
        val name = listOfNotNull(projectName, entryId).joinToString(separator = "-")

        return name.lowercase()
            .replace(regex = Regex(pattern = "[^a-z0-9]+"), replacement = "-")
            .trim('-')
            .ifEmpty { "rule-overview" }
    }
}
