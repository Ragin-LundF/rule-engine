# Coding Guidelines

Use these rules for all Kotlin code unless a more specific instruction file says otherwise.

## Kotlin style

- Use named arguments whenever calling Kotlin functions, constructors, or assertions where named arguments are supported.
- Do not rely on named arguments for Java APIs or APIs where Kotlin named arguments are unavailable.
- Use `runCatching` instead of `try/catch` when it keeps the code correct and readable.
- Use `try/catch` only when `runCatching` would make behavior less correct or less clear, for example with `finally`, resource cleanup, cancellation propagation, or explicit exception flow.
- Implement functions with block bodies. Do not use expression-body functions.
- Keep functions small and focused on a single responsibility.
- Split functions when they become too long, too deeply nested, or mix multiple responsibilities.
- Use descriptive variable, class, and function names.
- Avoid deep nesting. Prefer early returns and guard clauses.
- Avoid files with multiple classes, enums, interfaces, or objects. Use one top-level declaration per file.
- Use meaningful comments for complex logic, non-obvious decisions, trade-offs, or domain rules.
- Do not add comments that merely restate obvious code.
- Keep class and function names consistent with their purpose and behavior.
- Follow the official Kotlin style guide for formatting, naming, and conventions not defined here.

## Design rules

- Classes should follow single responsibility.
- Functions should follow single responsibility.
- Keep public APIs explicit and predictable.
- Prefer immutable data structures and values where practical.
- Preserve existing behavior unless the task explicitly asks for a behavior change.
- Code must be high-performant, clean, readable, and maintainable.
- Be "lazy" when writing code. Don't repeat yourself and don't write unnecessary or bloated code.
- No God classes or methods.
- Avoid code duplication.

## Package layout

- Keep at most 8 Kotlin files per directory. When a directory grows past 8, split it into subpackages by responsibility.
- Direct subclasses of a `sealed` type must stay in the same package as the sealed declaration. Never split a sealed hierarchy across packages — Kotlin forbids it.
- An `actual` declaration must stay in the same package as its `expect`.
- Mirror the main package structure in tests. A test moves only when the type it covers moves.

## Models and DTOs

A **model** is a pure data declaration with no behavior: `data class`, `enum class`, `sealed interface` / `sealed class` hierarchies of data, `@JvmInline value class`, and `data object`.

- **`ruleengine-ui`:** every model and every enum lives in a `model` subpackage of its feature package — `ui.tester.model`, `ui.project.model`, `ui.builder.model`. Never leave a model beside composables or services.
  - Group inside `model` when it exceeds 8 files: `ui.workbench.model.mode`, `ui.project.model.dialog`.
  - Composables, services, controllers, mappers, and objects holding logic never go in a `model` package.
  - Extension functions belong next to the type they extend, including inside a `model` package.
- **`ruleengine-core` / `ruleengine-model`:** models live in `dto` packages, grouped by subject — `ruleengine.core.domain.dto.field`, `ruleengine.core.domain.dto.action`.
- One top-level declaration per file, named after the declaration.
- Models are immutable. Wrap primitive identifiers in `@JvmInline value class` (`FieldId`, `OperatorId`), never pass raw strings where a typed id is expected.
- `ruleengine.core.domain.dto.*` is published API. Moving or renaming anything there is a breaking change — record it in `CHANGELOG.md` and update `docs/integration-guide.md`.

## Tests

- Use `kotlin.test` annotations and assertions when possible.
- Use named arguments for assertions, for example `assertEquals(expected = expectedValue, actual = actualValue)`.
- Cover relevant code paths, edge cases, and error paths.
- Add or update tests for behavior changes.

## Common rules
- Never commit code. Everything must be reviewed and commited by the user.
- When executing plans, follow the instructions provided in the plan. Ask after every step if the result is correct before continuing, unless it is explicitly unwanted by user prompt.
- Ask always for clarification if needed.
