package ruleengine.builder

import ruleengine.compiler.Compiler
import ruleengine.compiler.Validator
import ruleengine.core.domain.dto.ActionSchema
import ruleengine.core.domain.dto.FieldSchema
import ruleengine.core.errors.RuleEngineBuildException
import ruleengine.core.errors.Severity
import ruleengine.core.errors.ValidationDiagnostic
import ruleengine.core.io.FileInputSupport
import ruleengine.core.normalizer.NormalizerRegistry
import ruleengine.dsl.ast.RuleAst
import ruleengine.dsl.diagnostics.ParseException
import ruleengine.dsl.parser.Parser
import ruleengine.evaluator.RuleEngine
import ruleengine.manifest.ManifestEntry
import ruleengine.manifest.ManifestLoader
import ruleengine.manifest.ManifestPathResolution
import ruleengine.manifest.ManifestPathResolver
import ruleengine.schema.ActionSchemaLoader
import ruleengine.schema.FieldSchemaLoader
import java.nio.file.Files
import java.nio.file.Path

/**
 * Convenience entry point for library users: turns a project manifest into ready-to-use rule
 * engines in a single call.
 *
 * The builder performs the whole load phase for every manifest entry — resolving the referenced
 * files relative to the manifest, loading the field and action schema, parsing the rule files in
 * manifest order, validating and compiling them — and returns one [LoadedRuleEngine] per entry.
 *
 * Every problem (missing or unreadable file, path escaping the manifest directory, unknown entry
 * id, validation error) raises a [RuleEngineBuildException] whose message states exactly what went
 * wrong, so a half-initialised engine can never be used. Non-fatal diagnostics are reported as
 * [LoadedRuleEngine.warnings].
 *
 * For custom sources (strings, readers, classpath resources) or partial pipelines, use the
 * individual loaders (`FieldSchemaLoader`, `ActionSchemaLoader`, `Parser`, `Validator`, `Compiler`)
 * directly.
 */
@Suppress("TooManyFunctions")
object RuleEngineBuilder {
    /**
     * Loads every entry of the manifest at [manifestPath], or only the entry named [entryId].
     *
     * @param manifestPath path to the manifest YAML (or JSON) file
     * @param entryId when set, only this entry is built; the result is a single-element map
     * @param shortCircuitByOutput passed to [RuleEngine]; stops evaluating once every declared
     *   output has been produced
     * @param normalizerRegistry normalizer registry used for compilation
     * @return the built engines keyed by manifest entry id
     * @throws RuleEngineBuildException if the manifest, any referenced file or any rule is invalid
     */
    fun fromManifest(
        manifestPath: Path,
        entryId: String? = null,
        shortCircuitByOutput: Boolean = false,
        normalizerRegistry: NormalizerRegistry = NormalizerRegistry.default,
    ): Map<String, LoadedRuleEngine> {
        val manifest = runCatching {
            ManifestLoader.load(path = manifestPath)
        }.getOrElse { cause ->
            fail(manifestPath = manifestPath, entryId = entryId, details = "manifest is not readable", cause = cause)
        }

        val entries = selectEntries(manifestPath = manifestPath, entries = manifest.entries, entryId = entryId)
        val baseDir = manifestBaseDir(manifestPath = manifestPath)

        return entries.associate { entry ->
            entry.id to buildEntry(
                manifestPath = manifestPath,
                baseDir = baseDir,
                entry = entry,
                shortCircuitByOutput = shortCircuitByOutput,
                normalizerRegistry = normalizerRegistry,
            )
        }
    }

    /**
     * Loads a single manifest entry and returns it directly instead of wrapped in a map.
     *
     * @throws RuleEngineBuildException if the entry does not exist or cannot be built
     */
    fun fromManifestEntry(
        manifestPath: Path,
        entryId: String,
        shortCircuitByOutput: Boolean = false,
        normalizerRegistry: NormalizerRegistry = NormalizerRegistry.default,
    ): LoadedRuleEngine = fromManifest(
        manifestPath = manifestPath,
        entryId = entryId,
        shortCircuitByOutput = shortCircuitByOutput,
        normalizerRegistry = normalizerRegistry,
    ).getValue(entryId)

    private fun selectEntries(
        manifestPath: Path,
        entries: List<ManifestEntry>,
        entryId: String?,
    ): List<ManifestEntry> {
        if (entries.isEmpty()) {
            fail(manifestPath = manifestPath, entryId = entryId, details = "manifest contains no entries")
        }

        val duplicateIds = entries.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        if (duplicateIds.isNotEmpty()) {
            fail(
                manifestPath = manifestPath,
                entryId = null,
                details = "manifest contains duplicate entry ids: ${duplicateIds.sorted().joinToString()}",
            )
        }

        if (entryId == null) {
            return entries
        }

        val entry = entries.firstOrNull { it.id == entryId }
            ?: fail(
                manifestPath = manifestPath,
                entryId = entryId,
                details = "no entry with this id; available ids: ${entries.joinToString { it.id }}",
            )

        return listOf(entry)
    }

    private fun manifestBaseDir(manifestPath: Path): Path =
        manifestPath.toAbsolutePath().normalize().parent
            ?: fail(manifestPath = manifestPath, entryId = null, details = "manifest path has no parent directory")

    private fun buildEntry(
        manifestPath: Path,
        baseDir: Path,
        entry: ManifestEntry,
        shortCircuitByOutput: Boolean,
        normalizerRegistry: NormalizerRegistry,
    ): LoadedRuleEngine {
        val schema = loadSchema(manifestPath = manifestPath, baseDir = baseDir, entry = entry)
        val actions = loadActions(manifestPath = manifestPath, baseDir = baseDir, entry = entry)
        val asts = loadRuleAsts(manifestPath = manifestPath, baseDir = baseDir, entry = entry)

        val validationResult = Validator.validate(asts = asts, schema = schema, actions = actions)
        if (!validationResult.isValid) {
            fail(
                manifestPath = manifestPath,
                entryId = entry.id,
                details = "rule validation failed",
                diagnostics = validationResult.diagnostics,
            )
        }

        val compiledRules = runCatching {
            Compiler.compileRules(asts = asts, schema = schema, normalizerRegistry = normalizerRegistry)
        }.getOrElse { cause ->
            fail(manifestPath = manifestPath, entryId = entry.id, details = "rule compilation failed", cause = cause)
        }

        return LoadedRuleEngine(
            entryId = entry.id,
            engine = RuleEngine(compiledRules = compiledRules, shortCircuitByOutput = shortCircuitByOutput),
            schema = schema,
            actions = actions,
            warnings = validationResult.diagnostics.filter { it.severity != Severity.ERROR },
        )
    }

    private fun loadSchema(manifestPath: Path, baseDir: Path, entry: ManifestEntry): FieldSchema {
        val relativePath = entry.schema
            ?: fail(manifestPath = manifestPath, entryId = entry.id, details = "entry declares no 'schema'")
        val path = resolveExisting(
            manifestPath = manifestPath,
            baseDir = baseDir,
            entry = entry,
            relativePath = relativePath,
            label = "schema",
        )

        return runCatching { FieldSchemaLoader.load(path = path) }.getOrElse { cause ->
            fail(
                manifestPath = manifestPath,
                entryId = entry.id,
                details = "field schema '$relativePath' could not be loaded",
                cause = cause,
            )
        }
    }

    private fun loadActions(manifestPath: Path, baseDir: Path, entry: ManifestEntry): ActionSchema? {
        val relativePath = entry.actions ?: return null
        val path = resolveExisting(
            manifestPath = manifestPath,
            baseDir = baseDir,
            entry = entry,
            relativePath = relativePath,
            label = "actions",
        )

        return runCatching { ActionSchemaLoader.load(path = path) }.getOrElse { cause ->
            fail(
                manifestPath = manifestPath,
                entryId = entry.id,
                details = "action schema '$relativePath' could not be loaded",
                cause = cause,
            )
        }
    }

    /**
     * Parses all rule files of [entry] in manifest order, which is authoritative for execution
     * order (manifest file order first, then in-file declaration order).
     */
    private fun loadRuleAsts(manifestPath: Path, baseDir: Path, entry: ManifestEntry): List<RuleAst> {
        if (entry.rules.isEmpty()) {
            fail(manifestPath = manifestPath, entryId = entry.id, details = "entry declares no rule files")
        }

        return entry.rules.flatMap { relativePath ->
            val path = resolveExisting(
                manifestPath = manifestPath,
                baseDir = baseDir,
                entry = entry,
                relativePath = relativePath,
                label = "rules",
            )
            parseRuleFile(manifestPath = manifestPath, entry = entry, relativePath = relativePath, path = path)
        }
    }

    private fun parseRuleFile(
        manifestPath: Path,
        entry: ManifestEntry,
        relativePath: String,
        path: Path,
    ): List<RuleAst> = runCatching {
        Parser(input = FileInputSupport.readBoundedText(path = path, kind = "rule file")).parseRules()
    }.getOrElse { cause ->
        val reason = (cause as? ParseException)?.messageText ?: cause.message
        fail(
            manifestPath = manifestPath,
            entryId = entry.id,
            details = "rule file '$relativePath' could not be parsed: $reason",
            cause = cause,
        )
    }

    private fun resolveExisting(
        manifestPath: Path,
        baseDir: Path,
        entry: ManifestEntry,
        relativePath: String,
        label: String,
    ): Path {
        val resolution = ManifestPathResolver.resolveWithinBase(
            baseDir = baseDir,
            relativePath = relativePath,
            label = label,
        )
        val path = when (resolution) {
            is ManifestPathResolution.Accepted -> resolution.path
            is ManifestPathResolution.Rejected ->
                fail(manifestPath = manifestPath, entryId = entry.id, details = resolution.message)
        }

        if (!Files.isRegularFile(path)) {
            fail(
                manifestPath = manifestPath,
                entryId = entry.id,
                details = "$label file '$relativePath' not found (resolved to $path)",
            )
        }

        return path
    }

    private fun fail(
        manifestPath: Path,
        entryId: String?,
        details: String,
        diagnostics: List<ValidationDiagnostic> = emptyList(),
        cause: Throwable? = null,
    ): Nothing = throw RuleEngineBuildException(
        manifestPath = manifestPath,
        entryId = entryId,
        details = details,
        diagnostics = diagnostics,
        cause = cause,
    )
}
