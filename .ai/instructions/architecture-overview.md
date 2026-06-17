# Architecture Overview

Read this file for repository-level architecture decisions, cross-module changes, and changes that affect module boundaries.

## Modules

- `ruleengine-core` is the rule execution library. It owns the DSL for defining rules and the full execution pipeline.
- `ruleengine-ui` is the UI for desktop and web applications to manage and validate rules.

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
