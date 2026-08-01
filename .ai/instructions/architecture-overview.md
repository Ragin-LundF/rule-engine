# Architecture Overview

Read this file for repository-level architecture decisions, cross-module changes, and changes that affect module boundaries.

## Modules

- `ruleengine-model` is the shared domain vocabulary: `FieldType`, `FieldDefinition`, `FieldSchema`, `ActionArgType`, `OperatorNames`, `OperatorUtils`, `Severity`, `NodeType`, `AggregateFunctionName` and the `FieldId` / `OperatorId` / `NormalizerId` value classes. It has **no dependencies** and must keep none — no engine, no I/O, no serialization. Both other modules depend on it, which is what lets the UI use the engine's own types instead of mirroring them.
- `ruleengine-core` is the rule execution library. It owns the DSL for defining rules and the full execution pipeline. It exposes `ruleengine-model` via `api`, so consumers of core see those types transitively.
- `ruleengine-ui` is the desktop UI for managing and validating rules. Its `commonMain` depends on `ruleengine-model` only; `ruleengine-core` is available in `jvmMain` and `jvmTest`.

Dependency direction is one-way: `ruleengine-ui` → `ruleengine-core` → `ruleengine-model`. Nothing in `ruleengine-model` may import from the other two.

## DSL processing pipeline

Every rule must go through exactly these stages in order:

```text
DSL text -> Lexer -> Token[] -> Parser -> RuleAst[]
         -> Validator (semantic) -> ValidationResult
         -> Compiler -> CompiledRule[]
         -> PreparedRuleContext.prepare()
         -> RuleEngine.evaluate() -> EvaluationResult
```

Never skip or reorder stages. Each stage has a single responsibility and its own package.

## Cross-module rules

- `ruleengine-core` owns DSL tokenization, parsing, validation, compilation, context preparation, and evaluation.
- `ruleengine-ui` may use core functionality but must not duplicate core DSL logic.
- Syntax highlighting in the UI must use the `Lexer` from `ruleengine-core`; do not implement separate tokenization logic in the UI.
- Changes to field types, operators, DSL keywords, validation, or evaluation may require coordinated updates in both modules.
