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
import ruleengine.dsl.ast.VariableRefLiteral
import ruleengine.dsl.parser.Parser
import ruleengine.export.dto.CatalogOutcome
import ruleengine.export.dto.CatalogRule
import ruleengine.export.dto.CatalogRuleFile
import ruleengine.export.dto.ParsedRuleFile
import ruleengine.export.dto.RuleCatalog
import ruleengine.manifest.ManifestEntry
import ruleengine.manifest.ManifestFile
import ruleengine.manifest.ManifestFileResolver
import ruleengine.manifest.ProjectManifest
import ruleengine.manifest.classpath.ClasspathManifestFileResolver
import ruleengine.manifest.source.ManifestSource
import ruleengine.schema.FieldSchemaLoader
import java.nio.file.Path

/**
 * Builds the [RuleCatalog] every exporter renders from.
 *
 * Three entry points, because there are callers with very different starting points: a headless caller
 * has a manifest on disk or packaged in its own jar ([fromManifest]) and nothing loaded, while the
 * workbench ([build]) already holds the entry's rule files parsed and re-reading them would both
 * duplicate work and ignore unsaved edits.
 */
object RuleCatalogBuilder {

    /**
     * Reads the manifest at [manifestLocation] and builds one catalog per entry, or only [entryId]
     * when given.
     *
     * A location prefixed with `classpath:` is a classpath resource — so a service whose rules ship
     * inside its own jar can still export its rule documentation at runtime — and anything else is a
     * filesystem path. See [ruleengine.manifest.source.ManifestSource] for how a location is read.
     *
     * Parses each rule file separately rather than going through
     * [ruleengine.builder.RuleEngineBuilder], which concatenates an entry's files into one list and
     * so loses the file grouping the document is organised by.
     *
     * @param classLoader loader for a `classpath:` location; ignored for a filesystem one
     */
    fun fromManifest(
        manifestLocation: String,
        entryId: String? = null,
        classLoader: ClassLoader = ClasspathManifestFileResolver.defaultClassLoader(),
    ): List<RuleCatalog> {
        return fromSource(
            source = ManifestSource.of(location = manifestLocation, classLoader = classLoader),
            entryId = entryId,
        )
    }

    /**
     * The typed variant of [fromManifest], for a caller that already holds a [Path]; a manifest
     * packaged in a jar has none and is named by a `classpath:` location instead.
     */
    fun fromManifest(manifestPath: Path, entryId: String? = null): List<RuleCatalog> {
        return fromSource(source = ManifestSource.ofPath(manifestPath = manifestPath), entryId = entryId)
    }

    private fun fromSource(source: ManifestSource, entryId: String?): List<RuleCatalog> {
        return buildCatalogs(
            location = source.location,
            manifest = source.readManifest(),
            entryId = entryId,
            resolver = source.resolver,
        )
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

    /**
     * The load phase the manifest entry points share, once [resolver] has decided where the
     * manifest's files come from. [location] only labels failures.
     */
    private fun buildCatalogs(
        location: Path,
        manifest: ProjectManifest,
        entryId: String?,
        resolver: ManifestFileResolver,
    ): List<RuleCatalog> {
        val entries = if (entryId == null) {
            manifest.entries
        } else {
            manifest.entries.filter { entry -> entry.id == entryId }
        }

        if (entries.isEmpty()) {
            val known = manifest.entries.joinToString(separator = ", ") { entry -> entry.id }
            throw RuleEngineBuildException(
                manifestPath = location,
                entryId = entryId,
                details = "No such entry. Known entries: $known",
            )
        }

        return entries.map { entry ->
            buildEntry(
                projectName = manifest.name,
                entry = entry,
                resolver = resolver,
                manifestPath = location,
            )
        }
    }

    private fun buildEntry(
        projectName: String?,
        entry: ManifestEntry,
        resolver: ManifestFileResolver,
        manifestPath: Path,
    ): RuleCatalog {
        val schema = entry.schema?.let { relative ->
            loadSchema(
                file = resolve(
                    resolver = resolver,
                    relativePath = relative,
                    label = "schema",
                    manifestPath = manifestPath,
                    entryId = entry.id,
                )
            )
        }

        val files = entry.rules.map { relative ->
            val file = resolve(
                resolver = resolver,
                relativePath = relative,
                label = "rules",
                manifestPath = manifestPath,
                entryId = entry.id,
            )
            ParsedRuleFile(relativePath = relative, rules = Parser(input = ruleText(file = file)).parseRules())
        }

        return build(
            projectName = projectName,
            entryId = entry.id,
            files = files,
            schema = schema,
            schemaPath = entry.schema,
        )
    }

    private fun loadSchema(file: ManifestFile.Available): FieldSchema {
        return when (file) {
            is ManifestFile.OnDisk -> FieldSchemaLoader.load(path = file.path)
            is ManifestFile.InMemory ->
                FieldSchemaLoader.loadFromString(content = file.content, nameHint = file.nameHint)
        }
    }

    private fun ruleText(file: ManifestFile.Available): String {
        return when (file) {
            is ManifestFile.OnDisk -> FileInputSupport.readBoundedText(path = file.path, kind = "rule file")
            is ManifestFile.InMemory -> file.content
        }
    }

    private fun resolve(
        resolver: ManifestFileResolver,
        relativePath: String,
        label: String,
        manifestPath: Path,
        entryId: String,
    ): ManifestFile.Available {
        val file = resolver.resolve(relativePath = relativePath, label = label)

        return when (file) {
            is ManifestFile.Available -> file
            is ManifestFile.Unavailable -> throw RuleEngineBuildException(
                manifestPath = manifestPath,
                entryId = entryId,
                details = file.message,
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
            publishes = rule.assignments.map { assignment -> assignment.name },
            elseOutcomes = rule.elseActions.map { action -> outcome(action = action) },
            elsePublishes = rule.elseAssignments.map { assignment -> assignment.name },
            stopsOnThen = rule.stopOnThen,
            stopsOnElse = rule.stopOnElse,
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
            is VariableRefLiteral -> "\$${literal.name}"
        }
    }
}
