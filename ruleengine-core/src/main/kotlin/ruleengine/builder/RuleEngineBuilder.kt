package ruleengine.builder

import ruleengine.builder.RuleEngineBuilder.loadRuleAsts
import ruleengine.compiler.Compiler
import ruleengine.compiler.RuleFileAsts
import ruleengine.compiler.Validator
import ruleengine.core.domain.dto.action.ActionSchema
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.core.errors.RuleEngineBuildException
import ruleengine.core.errors.Severity
import ruleengine.core.errors.ValidationDiagnostic
import ruleengine.core.io.FileInputSupport
import ruleengine.core.normalizer.NormalizerRegistry
import ruleengine.dsl.ast.RuleAst
import ruleengine.dsl.diagnostics.ParseException
import ruleengine.dsl.parser.Parser
import ruleengine.evaluator.RuleEngine
import ruleengine.evaluator.ScopedEvaluation
import ruleengine.manifest.ManifestEntry
import ruleengine.manifest.ManifestFile
import ruleengine.manifest.ManifestFileResolver
import ruleengine.manifest.ProjectManifest
import ruleengine.manifest.classpath.ClasspathManifestFileResolver
import ruleengine.manifest.source.ManifestSource
import ruleengine.schema.ActionSchemaLoader
import ruleengine.schema.FieldSchemaLoader
import java.nio.file.Path

/**
 * Convenience entry point for library users: turns a project manifest into ready-to-use rule
 * engines in a single call.
 *
 * The builder performs the whole load phase for every manifest entry — resolving the referenced
 * files relative to the manifest, loading the field and action schema, parsing the rule files in
 * manifest order, validating and compiling them — and returns one [LoadedRuleEngine] per entry.
 *
 * One entry point, [fromManifest], covers both supported locations: a location prefixed with
 * `classpath:` is read as a classpath resource — which is what a manifest inside a jar or a Spring
 * Boot executable jar needs, because such a resource has no [Path] at all — and anything else as a
 * filesystem path. Rules that live somewhere else entirely (a database, an object store) are served
 * by implementing [ruleengine.manifest.ManifestFileResolver].
 *
 * Every problem (missing or unreadable file, path escaping the manifest directory, unknown entry
 * id, validation error) raises a [RuleEngineBuildException] whose message states exactly what went
 * wrong, so a half-initialised engine can never be used. Non-fatal diagnostics are reported as
 * [LoadedRuleEngine.warnings].
 *
 * For content held in memory or a partial pipeline, use the individual loaders (`FieldSchemaLoader`,
 * `ActionSchemaLoader`, `Parser`, `Validator`, `Compiler`) directly.
 */
object RuleEngineBuilder {
    /**
     * Loads every entry of the manifest at [manifestLocation], or only the entry named [entryId].
     *
     * The location says where to read from: a `classpath:` prefix names a classpath resource,
     * anything else is a filesystem path.
     *
     * ```kotlin
     * RuleEngineBuilder.fromManifest(manifestLocation = "classpath:rules/manifest.yaml")
     * RuleEngineBuilder.fromManifest(manifestLocation = "/etc/app/rules/manifest.yaml")
     * ```
     *
     * Use `classpath:` whenever the rules ship *inside* the application: it reads through
     * [ClassLoader.getResourceAsStream] only, so a plain jar, a Spring Boot executable jar and a jar
     * nested in one all behave the same as an exploded target directory. A filesystem location cannot
     * do this — a resource inside an executable jar has no [Path] at all.
     *
     * @param manifestLocation `classpath:`-prefixed resource name, or a path to the manifest YAML
     *   (or JSON) file
     * @param entryId when set, only this entry is built; the result is a single-element map
     * @param classLoader loader for a `classpath:` location; defaults to the thread context loader
     *   and is ignored for a filesystem location
     * @param normalizerRegistry normalizer registry used for compilation
     * @return the built engines keyed by manifest entry id
     * @throws RuleEngineBuildException if the manifest, any referenced file or any rule is invalid
     */
    fun fromManifest(
        manifestLocation: String,
        entryId: String? = null,
        classLoader: ClassLoader = ClasspathManifestFileResolver.defaultClassLoader(),
        normalizerRegistry: NormalizerRegistry = NormalizerRegistry.default,
    ): Map<String, LoadedRuleEngine> {
        return fromSource(
            source = ManifestSource.of(location = manifestLocation, classLoader = classLoader),
            entryId = entryId,
            normalizerRegistry = normalizerRegistry,
        )
    }

    /**
     * Loads every entry of the manifest at [manifestPath], or only the entry named [entryId].
     *
     * The typed variant of [fromManifest], for a caller that already holds a [Path]; a manifest
     * packaged in a jar has none and is named by a `classpath:` location instead.
     *
     * @throws RuleEngineBuildException if the manifest, any referenced file or any rule is invalid
     */
    fun fromManifest(
        manifestPath: Path,
        entryId: String? = null,
        normalizerRegistry: NormalizerRegistry = NormalizerRegistry.default,
    ): Map<String, LoadedRuleEngine> {
        return fromSource(
            source = ManifestSource.ofPath(manifestPath = manifestPath),
            entryId = entryId,
            normalizerRegistry = normalizerRegistry,
        )
    }

    /**
     * Loads a single manifest entry and returns it directly instead of wrapped in a map.
     *
     * @throws RuleEngineBuildException if the entry does not exist or cannot be built
     */
    fun fromManifestEntry(
        manifestLocation: String,
        entryId: String,
        classLoader: ClassLoader = ClasspathManifestFileResolver.defaultClassLoader(),
        normalizerRegistry: NormalizerRegistry = NormalizerRegistry.default,
    ): LoadedRuleEngine {
        return fromManifest(
            manifestLocation = manifestLocation,
            entryId = entryId,
            classLoader = classLoader,
            normalizerRegistry = normalizerRegistry,
        ).getValue(entryId)
    }

    /**
     * Loads a single manifest entry and returns it directly instead of wrapped in a map.
     *
     * @throws RuleEngineBuildException if the entry does not exist or cannot be built
     */
    fun fromManifestEntry(
        manifestPath: Path,
        entryId: String,
        normalizerRegistry: NormalizerRegistry = NormalizerRegistry.default,
    ): LoadedRuleEngine {
        return fromManifest(
            manifestPath = manifestPath,
            entryId = entryId,
            normalizerRegistry = normalizerRegistry,
        ).getValue(entryId)
    }

    /**
     * Loads and parses one entry's inputs without validating or compiling them.
     *
     * The same load phase every other entry point runs, stopped one step earlier and keeping the rules
     * grouped by file. `ValidatorCli` needs exactly that: the builder reports a validation failure by
     * throwing, which is right for something that returns a ready engine and useless to something whose
     * whole output is the list of diagnostics.
     *
     * @param location a filesystem path or a `classpath:` resource name, as [ManifestSource] reads it
     * @param entryId the entry to load, or null for the manifest's first
     * @throws ruleengine.core.errors.RuleEngineBuildException if the manifest, the entry or any file it
     *   references cannot be read or parsed
     */
    internal fun loadEntryInputs(location: String, entryId: String? = null): EntryInputs {
        val source = ManifestSource.of(location = location)
        val manifest = readManifest(location = source.location, entryId = entryId) { source.readManifest() }
        val entry = selectEntries(
            manifestPath = source.location,
            entries = manifest.entries,
            entryId = entryId,
        ).first()

        val schema = loadSchema(manifestPath = source.location, resolver = source.resolver, entry = entry)
        return EntryInputs(
            entryId = entry.id,
            // A scoped entry's rules name the member's fields, so that is what they must be validated
            // against — the same substitution `buildEntry` makes before validating.
            schema = scopedSchema(manifestPath = source.location, entry = entry, schema = schema),
            actions = loadActions(manifestPath = source.location, resolver = source.resolver, entry = entry),
            files = loadRuleFiles(manifestPath = source.location, resolver = source.resolver, entry = entry),
        )
    }

    /**
     * The load phase every entry point shares, once [source] has decided where the manifest and its
     * files come from.
     */
    private fun fromSource(
        source: ManifestSource,
        entryId: String?,
        normalizerRegistry: NormalizerRegistry,
    ): Map<String, LoadedRuleEngine> {
        val manifest = readManifest(location = source.location, entryId = entryId) {
            source.readManifest()
        }

        return build(
            location = source.location,
            manifest = manifest,
            entryId = entryId,
            resolver = source.resolver,
            normalizerRegistry = normalizerRegistry,
        )
    }

    /**
     * The load phase every entry point shares, once its [resolver] has decided where the manifest's
     * files come from.
     *
     * [location] only labels failures; it is a real path for a filesystem manifest and the resource
     * name for a classpath one.
     */
    private fun build(
        location: Path,
        manifest: ProjectManifest,
        entryId: String?,
        resolver: ManifestFileResolver,
        normalizerRegistry: NormalizerRegistry,
    ): Map<String, LoadedRuleEngine> {
        val entries = selectEntries(manifestPath = location, entries = manifest.entries, entryId = entryId)

        return entries.associate { entry ->
            entry.id to buildEntry(
                manifestPath = location,
                resolver = resolver,
                entry = entry,
                normalizerRegistry = normalizerRegistry,
            )
        }
    }

    /**
     * Reads and parses the manifest, reporting both failures as one "not readable".
     *
     * [read] covers reading *and* parsing on purpose: a manifest that is absent and one that is not
     * valid YAML are the same problem to a caller, and neither should escape as a raw I/O exception.
     */
    private fun readManifest(location: Path, entryId: String?, read: () -> ProjectManifest): ProjectManifest {
        return runCatching(read).getOrElse { cause ->
            fail(manifestPath = location, entryId = entryId, details = "manifest is not readable", cause = cause)
        }
    }

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

    private fun buildEntry(
        manifestPath: Path,
        resolver: ManifestFileResolver,
        entry: ManifestEntry,
        normalizerRegistry: NormalizerRegistry,
    ): LoadedRuleEngine {
        val schema = loadSchema(manifestPath = manifestPath, resolver = resolver, entry = entry)
        val actions = loadActions(manifestPath = manifestPath, resolver = resolver, entry = entry)
        val asts = loadRuleAsts(manifestPath = manifestPath, resolver = resolver, entry = entry)

        // A scoped entry's rules name the member's fields, so everything downstream of here — the
        // validator, the compiler and the evaluator — works against the member's schema instead.
        val ruleSchema = scopedSchema(manifestPath = manifestPath, entry = entry, schema = schema)

        val validationResult = Validator.validate(asts = asts, schema = ruleSchema, actions = actions)
        if (!validationResult.isValid) {
            fail(
                manifestPath = manifestPath,
                entryId = entry.id,
                details = "rule validation failed",
                diagnostics = validationResult.diagnostics,
            )
        }

        val compiledRules = runCatching {
            Compiler.compileRules(asts = asts, schema = ruleSchema, normalizerRegistry = normalizerRegistry)
        }.getOrElse { cause ->
            fail(manifestPath = manifestPath, entryId = entry.id, details = "rule compilation failed", cause = cause)
        }

        return LoadedRuleEngine(
            entryId = entry.id,
            engine = RuleEngine(compiledRules = compiledRules),
            schema = schema,
            actions = actions,
            warnings = validationResult.diagnostics.filter { it.severity != Severity.ERROR },
            scope = entry.scope,
        )
    }

    /**
     * The schema an entry's rules are written against, given its `scope`.
     *
     * Rejected at load time rather than left to fail per record: a scope naming nothing, or naming
     * something that is not a collection, describes a rule set that could never run — and the
     * manifest is exactly where that is worth saying.
     */
    private fun scopedSchema(manifestPath: Path, entry: ManifestEntry, schema: FieldSchema): FieldSchema {
        val scope = entry.scope ?: return schema
        val definition = schema.fields[FieldId(value = scope)]
            ?: fail(
                manifestPath = manifestPath,
                entryId = entry.id,
                details = "scope '$scope' is not a field of the schema",
            )
        if (definition.type != FieldType.COLLECTION) {
            fail(
                manifestPath = manifestPath,
                entryId = entry.id,
                details = "scope '$scope' is ${definition.type.name.lowercase()}, not a collection",
            )
        }
        return ScopedEvaluation.memberSchema(schema = schema, scope = scope) ?: schema
    }

    private fun loadSchema(manifestPath: Path, resolver: ManifestFileResolver, entry: ManifestEntry): FieldSchema {
        val relativePath = entry.schema
            ?: fail(manifestPath = manifestPath, entryId = entry.id, details = "entry declares no 'schema'")
        val file = resolveExisting(
            manifestPath = manifestPath,
            resolver = resolver,
            entry = entry,
            relativePath = relativePath,
            label = "schema",
        )

        return runCatching {
            when (file) {
                is ManifestFile.OnDisk -> FieldSchemaLoader.load(path = file.path)
                is ManifestFile.InMemory ->
                    FieldSchemaLoader.loadFromString(content = file.content, nameHint = file.nameHint)
            }
        }.getOrElse { cause ->
            fail(
                manifestPath = manifestPath,
                entryId = entry.id,
                details = "field schema '$relativePath' could not be loaded",
                cause = cause,
            )
        }
    }

    private fun loadActions(manifestPath: Path, resolver: ManifestFileResolver, entry: ManifestEntry): ActionSchema? {
        val relativePath = entry.actions ?: return null
        val file = resolveExisting(
            manifestPath = manifestPath,
            resolver = resolver,
            entry = entry,
            relativePath = relativePath,
            label = "actions",
        )

        return runCatching {
            when (file) {
                is ManifestFile.OnDisk -> ActionSchemaLoader.load(path = file.path)
                is ManifestFile.InMemory -> ActionSchemaLoader.loadFromString(content = file.content)
            }
        }.getOrElse { cause ->
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
    private fun loadRuleAsts(
        manifestPath: Path,
        resolver: ManifestFileResolver,
        entry: ManifestEntry,
    ): List<RuleAst> {
        if (entry.rules.isEmpty()) {
            fail(manifestPath = manifestPath, entryId = entry.id, details = "entry declares no rule files")
        }

        return entry.rules.flatMap { relativePath ->
            val file = resolveExisting(
                manifestPath = manifestPath,
                resolver = resolver,
                entry = entry,
                relativePath = relativePath,
                label = "rules",
            )
            parseRuleFile(manifestPath = manifestPath, entry = entry, relativePath = relativePath, file = file)
        }
    }

    /**
     * The same files [loadRuleAsts] reads, kept grouped by the path the manifest lists them under.
     *
     * [loadRuleAsts] flattens them because the engine evaluates one ordered list. Anything that reports
     * on an entry needs the grouping, because a line number without its file cannot be pointed at.
     */
    private fun loadRuleFiles(
        manifestPath: Path,
        resolver: ManifestFileResolver,
        entry: ManifestEntry,
    ): List<RuleFileAsts> {
        if (entry.rules.isEmpty()) {
            fail(manifestPath = manifestPath, entryId = entry.id, details = "entry declares no rule files")
        }

        return entry.rules.map { relativePath ->
            val file = resolveExisting(
                manifestPath = manifestPath,
                resolver = resolver,
                entry = entry,
                relativePath = relativePath,
                label = "rules",
            )
            RuleFileAsts(
                path = relativePath,
                asts = parseRuleFile(
                    manifestPath = manifestPath,
                    entry = entry,
                    relativePath = relativePath,
                    file = file,
                ),
            )
        }
    }

    private fun parseRuleFile(
        manifestPath: Path,
        entry: ManifestEntry,
        relativePath: String,
        file: ManifestFile.Available,
    ): List<RuleAst> = runCatching {
        val text = when (file) {
            is ManifestFile.OnDisk -> FileInputSupport.readBoundedText(path = file.path, kind = "rule file")
            is ManifestFile.InMemory -> file.content
        }
        Parser(input = text).parseRules()
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
        resolver: ManifestFileResolver,
        entry: ManifestEntry,
        relativePath: String,
        label: String,
    ): ManifestFile.Available {
        // ManifestFileResolver is public, so a custom implementation may throw anything. The class
        // contract is that every problem surfaces as a RuleEngineBuildException naming the entry.
        val file = runCatching {
            resolver.resolve(relativePath = relativePath, label = label)
        }.getOrElse { cause ->
            fail(
                manifestPath = manifestPath,
                entryId = entry.id,
                details = "$label file '$relativePath' could not be read",
                cause = cause,
            )
        }

        return when (file) {
            is ManifestFile.Available -> file
            is ManifestFile.Unavailable ->
                fail(manifestPath = manifestPath, entryId = entry.id, details = file.message)
        }
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
