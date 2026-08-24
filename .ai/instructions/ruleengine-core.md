# ruleengine-core Instructions

Use this file when touching `ruleengine-core` or core packages.

## Package layout

| Package | Purpose |
|---|---|
| `ruleengine.core.domain.dto` | Evaluation results (`RuleMatch`, `EvaluationResult`, `RuleAction`) and the `OperatorId` / `NormalizerId` value classes. Subject-specific models live in the subpackages below. One top-level declaration per file. |
| `ruleengine.core.domain.dto.field` | **Lives in `ruleengine-model`.** `FieldSchema`, `FieldDefinition`, `FieldType`, `FieldTypeCategories`, and the `FieldId` value class. |
| `ruleengine.core.domain.dto.action` | **Lives in `ruleengine-model`.** `ActionSchema`, `ActionDefinition`, `ActionArgType`. |
| `ruleengine.core.domain` | Logic over those models: `FieldPathResolver` / `FieldPathResolution` (the single owner of dotted-path resolution), `TemporalFormat` (the single owner of date pattern parsing), `OperatorNames`. |
| `ruleengine.core.errors` | Exception types such as `CompilationException`, `SchemaLoadException`, `RuleEngineException`, plus `ValidationDiagnostic` and `Severity`. |
| `ruleengine.core.normalizer` | **Lives in `ruleengine-model`.** `Normalizer` functional interface, `NormalizerProfile`, and the `NormalizerRegistry` singleton (`ids` enumerates the built-ins). |
| `ruleengine.dsl.lexer` | `Lexer`, `Token`, `TokenType`; raw tokenisation of the rule DSL. |
| `ruleengine.dsl.parser` | `Parser`; converts token stream into AST nodes. |
| `ruleengine.dsl.ast` | Immutable AST data classes such as `RuleAst`, `ConditionAst`, `AndAst`, `OrAst`, `NotAst`, and literals. Stays a single flat package despite its size: almost every type is a direct subclass of the `ExpressionAst` / `LiteralAst` / `ValueExpressionAst` / `PathSegmentAst` sealed hierarchies, and Kotlin requires those to share one package. Do not try to split it. |
| `ruleengine.dsl.diagnostics` | `ParseException`. |
| `ruleengine.compiler` | The public entry points only: `Compiler`, `Validator`, `ValidationResult`, plus `EntryValidator` and its `RuleFileAsts` input for validating a manifest entry file by file. |
| `ruleengine.compiler.support` | Internal helpers: `OperatorSupport` (which operators a type accepts), `LiteralValidation`, `Suggestions`, `FieldPathMessages`. |
| `ruleengine.compiler.value` | `ValueExpressionCompiler`, `ValueExpressionValidator`; the value-expression half of compilation. |
| `ruleengine.compiler.operators` | Per-type operator compilation helpers. |
| `ruleengine.evaluator` | `RuleEngine`, `CompiledRule`. |
| `ruleengine.evaluator.compiled` | `CompiledExpression` interface, `EvaluationCost`, `EvaluationCache`, `CompiledActionArgument`. Concrete expressions live in subpackages, grouped by the field type they test: `text/`, `numeric/`, `temporal/`, `stringset/`, `bool/`, the structural combinators in `logic/`, and the value-expression machinery in `value/`. Kotlin does not import from a parent package, so a new expression needs an explicit `import ruleengine.evaluator.compiled.CompiledExpression`. |
| `ruleengine.evaluator.compiled.value` | `CompiledValueExpression` and its implementations (arithmetic, field access, function call, literal, comparison). |
| `ruleengine.evaluator.compiled.value.result` | The `ExpressionValue` sealed hierarchy — what a value expression evaluates to. |
| `ruleengine.evaluator.compiled.value.path` | The `CompiledPathSegment` sealed hierarchy: `CompiledFieldSegment`, `CompiledFilterSegment`. |
| `ruleengine.evaluator.context` | `RuleContext`, `PreparedRuleContext`. Its `dto/` holds the whole `PreparedValue` sealed hierarchy and stays flat — sealed subclasses must share a package. |
| `ruleengine.evaluator.trace` | `TraceCollector`, `RecordingTraceCollector`, `NoopTraceCollector`, plus the internal `MutableNode` the collector builds. Immutable `DecisionTree` / `DecisionNode` live in its `dto/`. `exit` takes a `ConditionVerdict` and, on a rule node, the `RuleBranch` it selected; `DecisionNode.result` is kept as `verdict == TRUE` so existing readers and serialised traces still mean what they did. |
| `ruleengine.schema` | `FieldSchemaLoader`, `ActionSchemaLoader`, and DTO classes. |
| `ruleengine.manifest` | `ProjectManifest`, `ManifestLoader`, plus the `ManifestFileResolver` seam that decides *where* a manifest's files come from: `ManifestFile` (`OnDisk` / `InMemory` / `Unavailable`), `FileSystemManifestFileResolver`, `ManifestPathResolver`. At the 8-file cap — new location support goes in a subpackage. |
| `ruleengine.manifest.classpath` | `ClasspathManifestFileResolver`; resolves a manifest's files as classpath resources. Reads through `ClassLoader.getResourceAsStream` only, never a `URL` or a `Path` — that is what makes it work inside a plain jar, a Spring Boot executable jar and a nested jar alike. Nothing here may start scanning the classpath: a manifest enumerates its files. |
| `ruleengine.manifest.source` | `ManifestSource`; turns one location string into the manifest plus the resolver serving its files. The `classpath:` prefix is defined here and nowhere else — every entry point that takes a location routes through it, so the filesystem and classpath cases can never drift apart. |
| `ruleengine.jackson` | `JacksonUtil` singleton exposing the configured `jsonMapper`. YAML is read by feeding a `YAMLFactory` parser to that mapper (see `FieldSchemaLoader`). |
| `ruleengine.cli` | `EvaluateCli`, `ValidatorCli`; command-line entry points. Both take either `--manifest [--entry]` or `--schema` + `--rules`. There is no launcher and no executable jar — they are `main` classes on the library's runtime classpath, which needs `kotlin-reflect` as well as Jackson. The `validateRules` / `evaluateRules` JavaExec tasks in `ruleengine-core/build.gradle` are how to run them here; they set `workingDir = rootDir` so a relative path in `--args` means what it does at the prompt. |
| `ruleengine.builder` | `RuleEngineBuilder`, `LoadedRuleEngine`; assembles a ready-to-evaluate engine from a manifest location (`fromManifest`, `fromManifestEntry`) or behind a custom `ManifestFileResolver`. `loadEntryInputs` / `EntryInputs` stop one step earlier — parsed but unvalidated — for `ValidatorCli`, which reports diagnostics instead of refusing to start. |
| `ruleengine.export` | Rule catalog export: `RuleCatalogBuilder`, `PlainLanguageRenderer`, `CatalogText`, `FieldLabels`, plus `docx/`, `markdown/` and `dto/`. |
| `ruleengine.core.io` | `FileInputSupport`; bounded reads from a `Path` or an `InputStream`, and rule-file discovery. `walkRuleFiles` is filesystem-only by design — there is deliberately no classpath equivalent, because it would require classpath scanning. |
| `ruleengine.core.analysis` | `FieldUsage` and `VariableUsage`; report which field paths and variables a rule reads or writes, by walking the AST. Used by the export catalog, the validator's scope check and the UI's field-flow diagram and completions. |

## Core design patterns

- Use stateless singleton services with `object` for `Compiler`, `Validator`, `NormalizerRegistry`, `ManifestLoader`, `FieldSchemaLoader`, `ActionSchemaLoader`, and CLI entry points.
- Represent domain data as immutable `data class` values.
- Wrap primitive string identifiers in `@JvmInline value class`, for example `FieldId`, `OperatorId`, and `NormalizerId`.
- Never pass raw strings where a typed identifier is expected.
- Use `sealed interface` or `sealed class` for closed type sets, for example `PreparedValue`, `ExpressionAst`, and `LiteralAst`.
- Use `fun interface` for single-method abstractions such as `Normalizer`.
- Throw exceptions such as `CompilationException`, `ParseException`, and `SchemaLoadException` for unrecoverable structural failures.
- Accumulate `ValidationDiagnostic` entries with `Severity.ERROR` or `Severity.WARNING` for semantic issues that must be reported without aborting early.

## Extending field types and operators

When adding a new `FieldType` or operator:

`FieldType`, `ActionArgType`, `ConditionVerdict`, `RuleBranch`, `OperatorNames`, `Severity` and
`AggregateFunctionName` live in
`ruleengine-model`, which both `ruleengine-core` and `ruleengine-ui`'s `commonMain` depend on. There is
one declaration of each, so the UI can no longer drift from the engine, and most of what used to be a
manual sync checklist is now a compile error. What is left:

1. Add the constant to `FieldType` in `ruleengine-model`.
2. Add a case to `PreparedRuleContext.prepare()` to produce the matching `PreparedValue` subtype.
3. Add compile logic in `Compiler`, preferably as a focused private `compileXxxCondition` function.
4. Add validation logic in `Validator.validateCondition`.
5. Add a new `CompiledExpression` implementation in `ruleengine.evaluator.compiled`.
6. Map the YAML spelling and its aliases in `FieldSchemaLoader.parseFieldType`.
7. **The two that still fail silently**: list the type's operators in `Validator.supportedOperatorsFor`
   (`else -> emptySet()`) and `ui.builder.OperatorOptions.forField` (`else -> TEXT`). Neither is
   exhaustive, so cover both with a test. Also check `YamlHighlighter.fieldTypeValueColor`, which
   colours by raw string and already lags `parseFieldType`'s alias table.

Everything else is an exhaustive `when` that the compiler flags for you.

**Known divergence, not a bug to "fix" casually.** Four per-type operator tables disagree today:
`Validator.supportedOperatorsFor`, `ui.builder.OperatorOptions.forField`,
`ui.schema.OperatorsByType` and `ui.autocompletion.defaultOperatorsForType`. A `date` field offers
`>` in the Builder but `gt` in autocomplete; `text` offers `!=` in two of the four. The engine's table
decides what is *legal*; the UI tables decide what is *offered*. Collapsing them changes what each
surface shows, so it needs a product decision rather than a refactor.

If the change affects DSL keywords or operators, also update relevant UI syntax highlighting and autocomplete rules.

## Extending a rule's output side

A rule has **three** output blocks — `then`, `else` and `not_exists` — and they share one grammar. A new
clause in any of them touches, in order:

1. `ThenBlockParser.parse()` — the keyword dispatch (`"extract"` / `"set"` / `"add"` / `"stop"` / else an
   action name). Every branch shares this one function; `Parser.parseOptionalBranchBlock` calls it once
   per optional block, and `ThenBlockParser.BLOCK_TERMINATORS` is what stops a block at the next
   branch keyword.
2. A node in the flat `dsl/ast` package, plus `RuleAst`'s **hand-written** `equals`/`hashCode` — they
   exclude `line`/`column` deliberately, so a new field has to be added to both by hand.
3. `Validator.validateRule`, which calls the branch-agnostic `validateBranch` once per block. Never
   validate `rule.actions` directly, or the other two blocks go unchecked.
4. `Compiler.compileRule` and `CompiledRule`.
5. `RuleEngine.evaluateRule`, which maps the condition's verdict to a branch, plus `actionsOf` /
   `assignmentsOf` / `stopsAfter`, and `evaluateAll`, which owns the ordered loop and the `stop` break.
6. `VariableUsage` / `FieldUsage` / `VariableScopeValidator` if the clause reads or writes anything —
   each unions all three branches, so a new one has to be added to every union.
7. `RuleCatalogBuilder` plus the Markdown and DOCX renderers, or it is missing from every export.
8. `EvaluateCli.writeEvaluationResult` if it shows up in a result.

**Adding a whole branch is the same list plus the UI's.** `RuleBranch` in `ruleengine-model` is the
enum every `when` over branches is exhaustive on, which is what turns most of the above into compile
errors rather than omissions. See `.ai/instructions/ruleengine-ui.md` for the editor's side.

## Extending a path — a new `PathSegmentAst`

`take` / `takeLast` and `sortBy` are written as calls but parsed into path segments, which is what
lets them compose with projection, filtering and each other for free. A new one touches:

1. `Parser` — a name check in `parseFunctionCallOrFieldAccess` and a `parseXxx` that appends the
   segment and hands off to `parsePathContinuation`. **Also `isModernExpression`**, or a path carrying
   only the new segment falls back to the legacy named-operator path and the whole call is reported as
   an unknown field.
2. A node in `dsl/ast` and a compiled counterpart in `evaluator/compiled/value/path`. Both interfaces
   are sealed, so `ValueExpressionCompiler`, `ValueExpressionValidator`, `ValueExpressionRenderer`,
   `FieldUsage`, `VariableUsage` and `FieldAccessCompiledValueExpression` become compile errors.
3. `DslFunctions` — add the spelling to `SORT_NAMES` / `SLICE_NAMES` and to `pathFunctionNames()`, or
   the editor reads the word as an unknown identifier and the call editor starts offering it.
4. `FieldAccessCompiledValueExpression.applySegment`, working on the **raw** element list before
   anything becomes an `ExpressionValue` — that is what keeps `take(sortBy(x, ...), 3)` from
   converting the whole collection.

**The two that fail silently, with no compiler help:**

- `export/PlainLanguageRenderer.splitAggregatePath` picks decorations out with `filterIsInstance`, so a
  new segment simply vanishes from every Markdown and DOCX catalog export — and the prose then claims
  more than the rule does.
- `ui.builder.model.BuilderPathStep.withFilters` rebuilds the decoration list around the ones it knows.
  Anything it does not know is dropped, and since the Builder replaces the whole rule text on every
  edit, dropped means deleted from the file.

Both need the UI's side too — see `.ai/instructions/ruleengine-ui.md`.

## Three-valued condition evaluation

`CompiledExpression.evaluate` returns a `ConditionVerdict`, not a `Boolean`: `TRUE`, `FALSE`, or
`UNKNOWN` when the record carries no data to decide the test. Two rules govern it, and both are
load-bearing:

- **`UNKNOWN` is born in exactly two places** — a leaf whose prepared value is absent, and
  `ComparisonCompiledExpression` with a `MissingExpressionValue` operand. Functions keep their own
  contract (`count` over a missing collection is still `0`, `every` still vacuously true), so do not
  "fix" one to propagate missing.
- **`NotExpression.unknownAware` is why no existing rule changed behaviour.** `not` is the only
  operator where two- and three-valued logic disagree once an unknown collapses to false at the top of a
  rule. The compiler passes `unknownAware = ast.hasNotExistsBranch`, so a rule that never asked about
  missing data still reads `not <missing>` as true — which is what the documented guarded-accumulator
  pattern depends on. Any change here needs the regression tests in
  `NotExistsBranchIntegrationTest` and `KleeneLogicTest` to stay green.

## Keywords and reserved names

**A structural keyword must also block implicit `and`.** `Parser.INFIX_AND_BLOCK_KEYWORDS` is what stops
`when`'s implicit line-break `and` from reading the word as a field name — omit it and a misplaced
keyword is reported as an unknown field instead of a block ordering mistake. `stop` is deliberately *not*
in that set: it never legally follows a condition, and leaving it out keeps a schema field named `stop`
usable in a `when` block.

**A keyword an action cannot be named goes in `Validator.RESERVED_ACTION_NAMES`,** which reports it
against the action-schema declaration rather than against every rule that writes it. It holds `else`,
`not_exists`, `stop` and `add`.

## Validating part of an entry

`Validator.validate` answers "is this ordered list of rules valid". Two seams exist because callers need
less or more than that:

- `inheritedVariables` names variables that rules *before* this list publish, so a caller holding one
  rule file of a manifest does not see every cross-file `$name` as unknown. The editor uses it per
  keystroke.
- `EntryValidator` validates a whole entry **file by file**, stamping `ValidationDiagnostic.file` and
  keeping each line relative to its own file. It runs `Validator.validateSchemas` once for the entry and
  `Validator.validateRuleList` per file — do not collapse those back together, or a schema problem is
  reported once per rule file. `RuleEngineBuilder.loadEntryInputs` is the loader that feeds it, and
  `ValidatorCli --manifest` the command-line path.

**Declaration order is a guarantee, not an accident.** `set` and `stop` both depend on it. Anything that
reorders evaluation breaks them — which is why `shortCircuitByOutput` was removed rather than guarded a
third time.

## Adding normalizers

- Use `NormalizerRegistry.default` to access built-in normalizers, and `NormalizerRegistry.ids` to enumerate them (the schema editor and YAML completions both read that list).
- There is currently no registration API: `builtins` is a private immutable map, so adding a normalizer means editing `NormalizerRegistry` in `ruleengine-model`.
- `Normalizer` is a `fun interface` taking a `String` and returning a `String`.
- Use descriptive `snake_case` keys for normalizer IDs.

## Serialization

- Use Jackson 3 imports under `tools.jackson.*` for all JSON and YAML I/O.
- Use `ruleengine.jackson.JacksonUtil.jsonMapper` as the configured singleton.
- Schema and manifest files are YAML-first.
- When loading schema or manifest files, attempt YAML first and fall back to JSON only when necessary.
- Rule files use the `.rule` extension and the custom DSL. They are not YAML or JSON.
