package ruleengine.export

import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.errors.RuleEngineBuildException
import ruleengine.core.io.FileInputSupport
import ruleengine.dsl.ast.ActionAst
import ruleengine.dsl.ast.BetweenLiteral
import ruleengine.dsl.ast.BooleanLiteral
import ruleengine.dsl.ast.ExtractionRefLiteral
import ruleengine.dsl.ast.ListLiteral
import ruleengine.dsl.ast.LiteralAst
import ruleengine.dsl.ast.NumberLiteral
import ruleengine.dsl.ast.RuleAst
import ruleengine.dsl.ast.StringLiteral
import ruleengine.dsl.ast.ValueExpressionRenderer
import ruleengine.dsl.parser.Parser
import ruleengine.export.dto.CatalogOutcome
import ruleengine.export.dto.CatalogRule
import ruleengine.export.dto.CatalogRuleFile
import ruleengine.export.dto.ParsedRuleFile
import ruleengine.export.dto.RuleCatalog
import ruleengine.manifest.ManifestEntry
import ruleengine.manifest.ManifestLoader
import ruleengine.manifest.ManifestPathResolution
import ruleengine.manifest.ManifestPathResolver
import ruleengine.schema.FieldSchemaLoader
import java.nio.file.Path

/**
 * Builds the [RuleCatalog] every exporter renders from.
 *
 * Two entry points, because there are two callers with very different starting points: a headless
 * caller has a manifest on disk and nothing loaded, while the workbench already holds the entry's
 * rule files parsed and re-reading them would both duplicate work and ignore unsaved edits.
 */
object RuleCatalogBuilder {

    /**
     * Reads a manifest and builds one catalog per entry, or only [entryId] when given.
     *
     * Parses each rule file separately rather than going through
     * [ruleengine.builder.RuleEngineBuilder], which concatenates an entry's files into one list and
     * so loses the file grouping the document is organised by.
     */
    fun fromManifest(manifestPath: Path, entryId: String? = null): List<RuleCatalog> {
        val manifest = ManifestLoader.load(path = manifestPath)
        val baseDir = manifestPath.toAbsolutePath().normalize().parent
            ?: throw RuleEngineBuildException(
                manifestPath = manifestPath,
                entryId = entryId,
                details = "Manifest has no parent directory to resolve its entries against",
            )

        val entries = if (entryId == null) {
            manifest.entries
        } else {
            manifest.entries.filter { entry -> entry.id == entryId }
        }

        if (entries.isEmpty()) {
            val known = manifest.entries.joinToString(separator = ", ") { entry -> entry.id }
            throw RuleEngineBuildException(
                manifestPath = manifestPath,
                entryId = entryId,
                details = "No such entry. Known entries: $known",
            )
        }

        return entries.map { entry ->
            buildEntry(
                projectName = manifest.name,
                entry = entry,
                baseDir = baseDir,
                manifestPath = manifestPath,
            )
        }
    }

    /**
     * Builds a catalog from rules that are already parsed.
     *
     * [files] must be in manifest order, and the rules within each file in declaration order —
     * together they are the order the engine evaluates in, and a document that renumbers them would
     * misrepresent the rule set.
     */
    fun build(
        projectName: String?,
        entryId: String?,
        files: List<ParsedRuleFile>,
        schema: FieldSchema? = null,
        schemaPath: String? = null,
    ): RuleCatalog {
        return RuleCatalog(
            projectName = projectName,
            entryId = entryId,
            schemaPath = schemaPath,
            files = files.map { file ->
                CatalogRuleFile(
                    relativePath = file.relativePath,
                    rules = file.rules.map { rule -> catalogRule(rule = rule, schema = schema) },
                )
            },
        )
    }

    // ── entry loading ─────────────────────────────────────────────────────────

    private fun buildEntry(
        projectName: String?,
        entry: ManifestEntry,
        baseDir: Path,
        manifestPath: Path,
    ): RuleCatalog {
        val schema = entry.schema?.let { relative ->
            FieldSchemaLoader.load(
                path = resolve(
                    baseDir = baseDir,
                    relativePath = relative,
                    label = "schema",
                    manifestPath = manifestPath,
                    entryId = entry.id,
                )
            )
        }

        val files = entry.rules.map { relative ->
            val path = resolve(
                baseDir = baseDir,
                relativePath = relative,
                label = "rules",
                manifestPath = manifestPath,
                entryId = entry.id,
            )
            val text = FileInputSupport.readBoundedText(path = path, kind = "rule file")
            ParsedRuleFile(relativePath = relative, rules = Parser(input = text).parseRules())
        }

        return build(
            projectName = projectName,
            entryId = entry.id,
            files = files,
            schema = schema,
            schemaPath = entry.schema,
        )
    }

    private fun resolve(
        baseDir: Path,
        relativePath: String,
        label: String,
        manifestPath: Path,
        entryId: String,
    ): Path {
        return when (val resolution = ManifestPathResolver.resolveWithinBase(
            baseDir = baseDir,
            relativePath = relativePath,
            label = label,
        )) {
            is ManifestPathResolution.Accepted -> resolution.path
            is ManifestPathResolution.Rejected -> throw RuleEngineBuildException(
                manifestPath = manifestPath,
                entryId = entryId,
                details = resolution.message,
            )
        }
    }

    // ── per-rule projection ───────────────────────────────────────────────────

    private fun catalogRule(rule: RuleAst, schema: FieldSchema?): CatalogRule {
        return CatalogRule(
            id = rule.id,
            // Blank is treated as absent so a leftover `description ""` does not render as an empty
            // paragraph, matching how the validator decides whether to warn.
            description = rule.description?.takeIf { text -> text.isNotBlank() },
            condition = PlainLanguageRenderer.render(expr = rule.condition, schema = schema),
            technicalCondition = ValueExpressionRenderer.renderExpression(expr = rule.condition),
            outcomes = rule.actions.map { action -> outcome(action = action) },
        )
    }

    private fun outcome(action: ActionAst): CatalogOutcome {
        val arguments = action.arguments.map { argument -> argumentText(literal = argument) }

        return CatalogOutcome(
            action = action.name,
            argument = arguments.firstOrNull(),
            arguments = arguments,
        )
    }

    /**
     * An action argument as it should read in a document: unquoted.
     *
     * Unlike a condition literal, an action argument is the outcome's name rather than a value being
     * compared, and `assessment "service:premium"` reads better as `service:premium`.
     */
    private fun argumentText(literal: LiteralAst): String {
        return when (literal) {
            is StringLiteral -> literal.value
            is NumberLiteral -> literal.value
            is BooleanLiteral -> literal.value.toString()
            is ListLiteral -> literal.items.joinToString(separator = ", ") { item ->
                argumentText(literal = item)
            }

            is BetweenLiteral -> "${literal.low} - ${literal.high}"
            is ExtractionRefLiteral -> "\$${literal.groupIndex}"
        }
    }
}
