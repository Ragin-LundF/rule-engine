# ruleengine-ui Instructions

Use this file when touching `ruleengine-ui`, UI source sets, Compose code, autocomplete, or syntax highlighting.

## Platform targets

`ruleengine-ui` is a Kotlin Multiplatform module with three source sets:

- `commonMain`: `expect` declarations for platform operations such as file pickers, clipboard, save dialogs, and `@Composable expect fun RuleEditor()`.
- `jvmMain`: `actual` implementations for JVM desktop using Compose for Desktop.
- `jsMain`: `actual` implementations for the browser using Compose for Web / Skiko.

## Source-set rules

- Put shared Composable logic and shared state in `commonMain`.
- Put only platform-specific I/O or platform API calls in `jvmMain` and `jsMain` `actual` implementations.
- Do not duplicate shared UI state or business rules across platform source sets.

## UI design system

- Colours and typography are defined in `ui.Theme` in `jvmMain`.
- Use named colour constants such as `PrimaryBlue`, `AccentGreen`, `AccentRed`, `BgSurface`, and `TextPrimary`.
- Do not use hardcoded `Color(0x...)` literals outside `Theme.kt`.

## Syntax highlighting and autocomplete

- Syntax highlighting via `annotateRule` is driven directly by the `Lexer` from `ruleengine-core`.
- Do not duplicate tokenisation logic in the UI.
- Autocomplete is context-sensitive via `DslCursorContext` and `DslSection`.
- New DSL keywords or operators must be added to relevant `build*Completions` functions in `AutoComplete.kt`.
- New DSL keywords or operators must also be added to the relevant `DSL_NAMED_OPS`, `DSL_STRUCTURE`, or `DSL_LOGIC` sets in `SyntaxHighlighter.kt`.

## When to also read core instructions

Also read `ruleengine-core.md` when a UI change touches or depends on:

- DSL tokens
- operators
- field types
- parser behavior
- validation behavior
- evaluation behavior
- syntax highlighting derived from core lexer output
