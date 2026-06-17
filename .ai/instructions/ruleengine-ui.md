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

## Architecture & Constraints

### Kotlin Multiplatform (KMP) Boundaries
- **Logic/UI Separation:** All business logic, state calculation, and DSL parsing MUST reside in `commonMain` or `ruleengine-core`. `jvmMain`/`jsMain` should only contain `actual` implementations for platform-specific APIs (File I/O, Clipboard, Window management).
- **No Core Leaks:** Avoid importing `ruleengine-core` implementation details (like private internal engine classes) directly into UI components. UI should interact with the engine via high-level, stable interfaces.

### State Management & Unidirectional Data Flow (UDF)
- **State Ownership:** UI state must be hosted in ViewModels (or equivalent state holders) in `commonMain`. 
- **Flow Pattern:** Use `StateFlow` for exposing state to Composables. Composables should be "dumb" and only emit events via lambdas (e.g., `onRuleChanged: (String) -> Unit`).
- **Concurrency:** All UI state mutations must be performed on `Dispatchers.Main`. Use `viewModelScope` for launching coroutines.

### Error & Diagnostic Handling
- **Non-Fatal Errors:** Validation issues (e.g., syntax errors in a rule) must be treated as data (list of `ValidationDiagnostic`) and rendered as UI overlays/decorations. They should **not** throw exceptions that crash the UI.
- **Fatal Errors:** Unrecoverable errors (e.g., failed schema loading) should be caught at the platform level and displayed via a standard "Error Dialog" component.

## UI design system
- Colours and typography are defined in `ui.Theme` in `commonMain`.
- Use named colour constants from the theme (e.g., `PrimaryBlue`, `AccentOrange`, `TextSecondary`).
- Do not use hardcoded `Color(0x...)` literals.

## Syntax highlighting and autocomplete
- **Syntax highlighting:** Handled by `annotateRule`. It uses the `Lexer` from `ruleengine-core` to tokenize text and then applies colors based on token type and context (keywords, logic, field/action names). It also handles `#` comments and renders `ValidationDiagnostic` underlines (Red for ERROR, Orange for WARNING).
- **Autocomplete:** Implementation is encapsulated in `ui.autocompletion` using typealiases (e.g., `CompletionItem`) for the public `ui` API.
- **Updating patterns:**
    - New DSL keywords/operators must be added to `build*Completions` functions in `AutoComplete.kt`.
    - New DSL keywords/operators must also be added to the relevant `DSL_NAMED_OPS`, `DSL_STRUCTURE`, or `DSL_LOGIC` sets in `SyntaxHighlighter.kt`.

## When to also read core instructions

Also read `ruleengine-core.md` when a UI change touches or depends on:

- DSL tokens
- operators
- field types
- parser behavior
- validation behavior
- evaluation behavior
- syntax highlighting derived from core lexer output
