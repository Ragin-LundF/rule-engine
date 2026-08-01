package ui.workbench

import ruleengine.core.errors.ValidationDiagnostic
import ui.diagrams.model.RuleSource
import ui.workbench.model.CatalogRule
import ui.workbench.model.RuleTreeFile

/**
 * The Builder's rule tree: one node per rule file, each listing its rules and their status.
 *
 * [parsedFiles] is passed in rather than read here because obtaining it touches the disk and runs
 * the parser; the caller does that inside its own `remember` so the cost stays keyed the way it
 * always was.
 *
 * When there are no manifest files — no project loaded — the tree falls back to one synthetic
 * `current` node built from [fallbackRuleIds], so the tree still shows the rules already in the
 * editor rather than going blank.
 */
internal fun ruleTreeFilesFrom(
    parsedFiles: List<RuleSource>,
    fallbackRuleIds: Set<String>,
    currentFile: String?,
    diagnostics: List<ValidationDiagnostic>,
): List<RuleTreeFile> {
    if (parsedFiles.isEmpty()) {
        return listOf(
            RuleTreeFile(
                relativePath = "current",
                rules = fallbackRuleIds.filter { it.isNotBlank() }.map { CatalogRule(id = it) },
            ),
        )
    }

    return parsedFiles.map { source ->
        RuleTreeFile(
            relativePath = source.relativePath,
            rules = source.rules.map { ast ->
                CatalogRule(
                    id = ast.id,
                    status = ruleTreeStatusFor(
                        ruleId = ast.id,
                        description = ast.description,
                        relativePath = source.relativePath,
                        currentFile = currentFile,
                        diagnostics = diagnostics,
                    ),
                )
            },
        )
    }
}
