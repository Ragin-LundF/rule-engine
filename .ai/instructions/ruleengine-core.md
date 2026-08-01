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
| `ruleengine.compiler` | The public entry points only: `Compiler`, `Validator`, `ValidationResult`. |
| `ruleengine.compiler.support` | Internal helpers: `OperatorSupport` (which operators a type accepts), `LiteralValidation`, `Suggestions`, `FieldPathMessages`. |
| `ruleengine.compiler.value` | `ValueExpressionCompiler`, `ValueExpressionValidator`; the value-expression half of compilation. |
| `ruleengine.compiler.operators` | Per-type operator compilation helpers. |
| `ruleengine.evaluator` | `RuleEngine`, `CompiledRule`. |
| `ruleengine.evaluator.compiled` | `CompiledExpression` interface, `EvaluationCost`, `EvaluationCache`, `CompiledActionArgument`. Concrete expressions live in subpackages, grouped by the field type they test: `text/`, `numeric/`, `temporal/`, `stringset/`, `bool/`, the structural combinators in `logic/`, and the value-expression machinery in `value/`. Kotlin does not import from a parent package, so a new expression needs an explicit `import ruleengine.evaluator.compiled.CompiledExpression`. |
| `ruleengine.evaluator.compiled.value` | `CompiledValueExpression` and its implementations (arithmetic, field access, function call, literal, comparison). |
| `ruleengine.evaluator.compiled.value.result` | The `ExpressionValue` sealed hierarchy — what a value expression evaluates to. |
| `ruleengine.evaluator.compiled.value.path` | The `CompiledPathSegment` sealed hierarchy: `CompiledFieldSegment`, `CompiledFilterSegment`. |
| `ruleengine.evaluator.context` | `RuleContext`, `PreparedRuleContext`. Its `dto/` holds the whole `PreparedValue` sealed hierarchy and stays flat — sealed subclasses must share a package. |
| `ruleengine.evaluator.trace` | `TraceCollector`, `RecordingTraceCollector`, `NoopTraceCollector`, plus the internal `MutableNode` the collector builds. Immutable `DecisionTree` / `DecisionNode` live in its `dto/`. |
| `ruleengine.schema` | `FieldSchemaLoader`, `ActionSchemaLoader`, and DTO classes. |
| `ruleengine.manifest` | `ProjectManifest`, `ManifestLoader`. |
| `ruleengine.jackson` | `JacksonUtil` singleton exposing the configured `jsonMapper`. YAML is read by feeding a `YAMLFactory` parser to that mapper (see `FieldSchemaLoader`). |
| `ruleengine.cli` | `EvaluateCli`, `ValidatorCli`; command-line entry points. |
| `ruleengine.builder` | `RuleEngineBuilder`, `LoadedRuleEngine`; assembles a ready-to-evaluate engine from a manifest or directory. |
| `ruleengine.export` | Rule catalog export: `RuleCatalogBuilder`, `PlainLanguageRenderer`, `CatalogText`, `FieldLabels`, plus `docx/`, `markdown/` and `dto/`. |
| `ruleengine.core.io` | `FileInputSupport`; bounded file reads and rule-file discovery. |
| `ruleengine.core.analysis` | `FieldUsage`; reports which field paths a rule reads, by walking the AST. Used by the export catalog and by the UI's field-flow diagram. |

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

`FieldType`, `ActionArgType`, `OperatorNames`, `Severity` and `AggregateFunctionName` live in
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
