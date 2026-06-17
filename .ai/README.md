# AI Instruction Index

This folder contains the canonical AI instructions for this repository.

The goal is progressive loading: read only what is needed for the current task instead of loading all instructions every time.

## Always read first

- `instructions/coding-guidelines.md` when creating, editing, reviewing, or testing Kotlin code.

## Load by task scope

### Repository architecture or cross-module behavior

Read:

- `instructions/architecture-overview.md`

Use this for changes that affect module boundaries, DSL flow, public APIs, package layout, or interactions between `ruleengine-core` and `ruleengine-ui`.

### `ruleengine-core`

Read:

- `instructions/architecture-overview.md`
- `instructions/ruleengine-core.md`

Use these when touching files in or related to:

- `ruleengine-core`
- `ruleengine.core.*`
- `ruleengine.dsl.*`
- `ruleengine.compiler`
- `ruleengine.evaluator.*`
- `ruleengine.schema`
- `ruleengine.manifest`
- `ruleengine.jackson`
- `ruleengine.cli`

### `ruleengine-ui`

Read:

- `instructions/architecture-overview.md`
- `instructions/ruleengine-ui.md`

Use these when touching files in or related to:

- `ruleengine-ui`
- Compose UI code
- syntax highlighting
- autocomplete
- desktop/browser platform source sets

If the UI change depends on DSL tokens, operators, field types, validation, or evaluation behavior, also read:

- `instructions/ruleengine-core.md`

### Tests

Read:

- `instructions/testing.md`

Use this when adding, modifying, or reviewing tests.

For module-specific tests, combine it with the relevant module file:

- Core tests: `ruleengine-core.md` + `testing.md`
- UI tests: `ruleengine-ui.md` + `testing.md`

### Static analysis, Detekt, or lint cleanup

Read:

- `instructions/static-analysis.md`

Use this when fixing Detekt findings, adding suppressions, or changing style-related code.

## Conflict resolution

If instructions conflict:

1. Prefer user instructions in the current chat or task.
2. Prefer the most specific `.ai/instructions/*.md` file over a general one.
3. Prefer module-specific rules over repository-wide rules.
4. Preserve behavior before refactoring style.
5. Explain any unresolved conflict before changing architecture or behavior.

## Token-saving rule

Do not load every file in `.ai/instructions/` by default. Start with this index, then load only the files named above that match the current task.
