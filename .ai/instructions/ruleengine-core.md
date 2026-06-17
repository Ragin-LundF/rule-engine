# ruleengine-core Instructions

Use this file when touching `ruleengine-core` or core packages.

## Package layout

| Package | Purpose |
|---|---|
| `ruleengine.core.domain` | Shared domain models such as `FieldSchema`, `FieldDefinition`, `FieldType`, `RuleMatch`, and inline value classes such as `FieldId`, `OperatorId`, `NormalizerId`. |
| `ruleengine.core.errors` | Exception types such as `CompilationException`, `SchemaLoadException`, `RuleEngineException`, plus `ValidationDiagnostic` and `Severity`. |
| `ruleengine.core.normalizer` | `Normalizer` functional interface, `NormalizerProfile`, and `NormalizerRegistry` singleton. |
| `ruleengine.dsl.lexer` | `Lexer`, `Token`, `TokenType`; raw tokenisation of the rule DSL. |
| `ruleengine.dsl.parser` | `Parser`; converts token stream into AST nodes. |
| `ruleengine.dsl.ast` | Immutable AST data classes such as `RuleAst`, `ConditionAst`, `AndAst`, `OrAst`, `NotAst`, and literals. |
| `ruleengine.dsl.diagnostics` | `ParseException`. |
| `ruleengine.compiler` | `Validator`, `Compiler`, and `operators/` helpers. |
| `ruleengine.evaluator` | `RuleEngine`, `CompiledRule`. |
| `ruleengine.evaluator.compiled` | `CompiledExpression` interface and concrete expression implementations. |
| `ruleengine.evaluator.context` | `RuleContext`, `PreparedRuleContext`, and `PreparedValue` sealed hierarchy. |
| `ruleengine.evaluator.trace` | `TraceCollector`, `RecordingTraceCollector`, `NoopTraceCollector`, `DecisionTree`, and `DecisionNode`. |
| `ruleengine.schema` | `FieldSchemaLoader`, `ActionSchemaLoader`, and DTO classes. |
| `ruleengine.manifest` | `ProjectManifest`, `ManifestLoader`. |
| `ruleengine.jackson` | `JacksonUtil` singleton for JSON and YAML serialization. |
| `ruleengine.cli` | `EvaluateCli`, `ValidatorCli`; command-line entry points. |

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

1. Add the constant to `FieldType` in the domain model.
2. Add a case to `PreparedRuleContext.prepare()` to produce the matching `PreparedValue` subtype.
3. Add compile logic in `Compiler`, preferably as a focused private `compileXxxCondition` function.
4. Add validation logic in `Validator.validateCondition`.
5. Add a new `CompiledExpression` implementation in `ruleengine.evaluator.compiled`.
6. Update `AutoComplete.kt` in `ruleengine-ui`, especially `defaultOperatorsForType` and `valuePlaceholderForOperator`.

If the change affects DSL keywords or operators, also update relevant UI syntax highlighting and autocomplete rules.

## Adding normalizers

- Register built-in normalizers in `NormalizerRegistry.builtins`.
- Use descriptive `snake_case` keys.
- `Normalizer` takes a `String` and returns a `String`.

## Serialization

- Use Jackson 3 imports under `tools.jackson.*` for all JSON and YAML I/O.
- Use `ruleengine.jackson.JacksonUtil.jsonMapper` as the configured singleton.
- Schema and manifest files are YAML-first.
- When loading schema or manifest files, attempt YAML first and fall back to JSON only when necessary.
- Rule files use the `.rule` extension and the custom DSL. They are not YAML or JSON.
