# Architecture

- `ruleengine-core` - Rule execution library with a DSL for defining rules and executing them.
- `ruleengine-ui` - UI for desktop and web applications to manage and validate rules.

## Module: ruleengine-core

### Package Layout

| Package                         | Purpose                                                                                                                                                    |
|---------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `ruleengine.core.domain`        | Shared domain models (`FieldSchema`, `FieldDefinition`, `FieldType`, `RuleMatch`, etc.) and inline value classes (`FieldId`, `OperatorId`, `NormalizerId`) |
| `ruleengine.core.errors`        | Exception types (`CompilationException`, `SchemaLoadException`, `RuleEngineException`) and `ValidationDiagnostic` / `Severity`                             |
| `ruleengine.core.normalizer`    | `Normalizer` functional interface, `NormalizerProfile`, and `NormalizerRegistry` singleton                                                                 |
| `ruleengine.dsl.lexer`          | `Lexer`, `Token`, `TokenType` – raw tokenisation of the rule DSL                                                                                           |
| `ruleengine.dsl.parser`         | `Parser` – converts token stream into AST nodes                                                                                                            |
| `ruleengine.dsl.ast`            | Immutable AST data classes (`RuleAst`, `ConditionAst`, `AndAst`, `OrAst`, `NotAst`, literals)                                                              |
| `ruleengine.dsl.diagnostics`    | `ParseException`                                                                                                                                           |
| `ruleengine.compiler`           | `Validator` (semantic checks → `ValidationDiagnostic` list) and `Compiler` (AST → `CompiledRule`) plus `operators/` helpers                                |
| `ruleengine.evaluator`          | `RuleEngine`, `CompiledRule`                                                                                                                               |
| `ruleengine.evaluator.compiled` | `CompiledExpression` interface + all concrete expression implementations                                                                                   |
| `ruleengine.evaluator.context`  | `RuleContext`, `PreparedRuleContext`, `PreparedValue` sealed hierarchy                                                                                     |
| `ruleengine.evaluator.trace`    | `TraceCollector`, `RecordingTraceCollector`, `NoopTraceCollector`, `DecisionTree` / `DecisionNode`                                                         |
| `ruleengine.schema`             | `FieldSchemaLoader`, `ActionSchemaLoader` and their DTO classes                                                                                            |
| `ruleengine.manifest`           | `ProjectManifest`, `ManifestLoader`                                                                                                                        |
| `ruleengine.jackson`            | `JacksonUtil` singleton for JSON / YAML (de)serialisation                                                                                                  |
| `ruleengine.cli`                | `EvaluateCli`, `ValidatorCli` – command-line entry points                                                                                                  |

### DSL Processing Pipeline

Every rule goes through exactly these stages in order:

```
DSL text → Lexer → Token[] → Parser → RuleAst[]
         → Validator (semantic) → ValidationResult
         → Compiler → CompiledRule[]
         → PreparedRuleContext.prepare()
         → RuleEngine.evaluate() → EvaluationResult
```

Never skip or reorder stages. Each stage has a single responsibility and its own package.

### Design Patterns for ruleengine-core

- **Stateless singleton services** – use `object` for `Compiler`, `Validator`, `NormalizerRegistry`, `ManifestLoader`,
  `FieldSchemaLoader`, `ActionSchemaLoader`, and CLI entry-points.
- **Immutable domain models** – represent all domain data as `data class`.
- **Type-safe identifiers** – wrap primitive string IDs in `@JvmInline value class` (e.g. `FieldId`, `OperatorId`,
  `NormalizerId`). Never pass raw strings where a typed ID is expected.
- **Sealed hierarchies** – use `sealed interface` or `sealed class` for closed type sets (e.g. `PreparedValue`,
  `ExpressionAst`/`LiteralAst`).
- **Functional interfaces** – use `fun interface` for single-method abstractions (e.g. `Normalizer`).
- **Error vs diagnostic** – throw exceptions (`CompilationException`, `ParseException`, `SchemaLoadException`) for
  unrecoverable structural failures; accumulate `ValidationDiagnostic` entries (with `Severity.ERROR` / `WARNING`) for
  semantic issues that must be reported without aborting early.

### Extending Field Types and Operators

When adding a new `FieldType` or operator:

1. Add the constant to `FieldType` (domain).
2. Add a case to `PreparedRuleContext.prepare()` to produce the matching `PreparedValue` subtype.
3. Add compile logic in `Compiler` (new `compileXxxCondition` private function).
4. Add validation logic in `Validator.validateCondition`.
5. Add a new `CompiledExpression` implementation in `ruleengine.evaluator.compiled`.
6. Update `AutoComplete.kt` (`defaultOperatorsForType`, `valuePlaceholderForOperator`) in `ruleengine-ui`.

### Adding New Normalizers

Register them in `NormalizerRegistry.builtins`. Use a descriptive snake_case key. The `Normalizer` functional interface
takes and returns a `String`.

### Serialisation

- Use Jackson 3 (`tools.jackson.*`) for all JSON and YAML I/O. The configured singleton is
  `ruleengine.jackson.JacksonUtil.jsonMapper`.
- Schema and manifest files are YAML-first. When loading, attempt YAML first and fall back to JSON only when necessary (
  see `ManifestLoader`).
- Rule files use the `.rule` extension and the custom DSL; they are **not** YAML/JSON.

---

## Module: ruleengine-ui

### Platform Targets

`ruleengine-ui` is a **Kotlin Multiplatform** module with three source sets:

- `commonMain` – `expect` declarations for platform operations (file pickers, clipboard, save dialogs) and the
  `@Composable expect fun RuleEditor()`.
- `jvmMain` – `actual` implementations for JVM desktop (Compose for Desktop).
- `jsMain` – `actual` implementations for the browser (Compose for Web / Skiko).

Always place shared Composable logic and state in `commonMain`. Only put platform-specific I/O or API calls in
`jvmMain` / `jsMain` `actual` implementations.

### UI Design System

All colours and typography are defined in `ui.Theme` (jvmMain). Use the named colour constants (`PrimaryBlue`,
`AccentGreen`, `AccentRed`, `BgSurface`, `TextPrimary`, etc.) – never use hardcoded `Color(0x...)` literals outside
`Theme.kt`.

### Syntax Highlighting & Autocomplete

- Syntax highlighting (`annotateRule`) is driven directly by the `Lexer` from `ruleengine-core`; do not duplicate
  tokenisation logic in the UI.
- Autocomplete is context-sensitive via `DslCursorContext` / `DslSection`. New DSL keywords or operators must also be
  added to the relevant `build*Completions` functions in `AutoComplete.kt` and to the `DSL_NAMED_OPS` /
  `DSL_STRUCTURE` / `DSL_LOGIC` sets in `SyntaxHighlighter.kt`.

---

## Testing Guidelines

### ruleengine-core Tests

- **Unit tests** are mirroring main source packages.
- **Integration tests** (e.g. `FullManifestIntegrationTest`) load real YAML schemas and `.rule` files from `src/test/resources/`. New integration scenarios should follow this pattern.
- Inline DSL strings in tests must be valid rule-DSL (parseable by `Parser`). Use `trimIndent()` for multi-line strings.
- Always assert both positive (match) and negative (no match) evaluation paths.
- When testing validation, assert the exact `Severity` and inspect the `diagnostics` list, not just `isValid`.

---

## Static Analysis

- Detekt is configured in `config/detekt.yml`. All code must pass Detekt checks.
- Use `@Suppress` with a named reason only for individual, justified suppressions (e.g. `@Suppress("TooManyFunctions")`).
- Never suppress whole files.

# Code-Style Guidelines

- Use always named arguments when Kotlin or with Kotlin implemented functions are called. Use also named arguments for constructors and assertions.
- Use `runCatching` instead of try/catch if possible
- Functions should only be implemented as block code, not as expression bodies.
- Functions should have a single responsibility and be small in size. If functions are too complex, consider refactoring into smaller functions.
- Use descriptive variable and function names.
- Avoid deep nesting and use early returns to simplify control flow.
- Avoid files with multiple classes if possible. Classes should live in their own files.
- Use meaningful comments to explain complex logic or non-obvious decisions.
- Keep class and function names consistent with their purpose and functionality.
- Follow the Kotlin style guide for consistent formatting and naming conventions for everything which is not defined here.
- Testing should use `kotlin.test` annotations and assertions when possible.
- The tests should cover all code paths and edge cases.
