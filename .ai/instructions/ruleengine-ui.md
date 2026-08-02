# ruleengine-ui Instructions

Use this file when touching `ruleengine-ui`, UI source sets, Compose code, autocomplete, or syntax highlighting.

## Platform targets

`ruleengine-ui` is a Kotlin Multiplatform module with a single `jvm()` target and two source sets:

- `commonMain`: `expect` declarations for platform operations such as file pickers, clipboard, and `@Composable expect fun RuleEditor()`.
- `jvmMain`: `actual` implementations for JVM desktop using Compose for Desktop.

A browser/JS target is explicitly out of scope. Do not add `expect`/`actual` indirection for its own sake.

## Source-set rules

- Put shared Composable logic and shared state in `commonMain`.
- Put only platform-specific I/O or platform API calls in `jvmMain` `actual` implementations.
- Do not duplicate shared UI state or business rules across platform source sets.

## Package layout

Each feature package (`ui.builder`, `ui.tester`, `ui.project`, `ui.workbench`, `ui.diagrams`, `ui.yaml`, `ui.dsl`, …) is organised the same way:

| Package | Holds |
|---|---|
| `ui.<feature>` | Composables, controllers, services, mappers — anything with behavior. |
| `ui.<feature>.model` | Every model and enum of that feature. See the Models and DTOs rules in `coding-guidelines.md`. |
| `ui.<feature>.<role>` | Further behavior groups when the feature package exceeds 8 files: `ui.workbench.areas`, `ui.builder.view`, `ui.diagrams.render`, `ui.builder.components.dropdown`. |
| `ui.<feature>.model.<group>` | Model groups when `model` itself exceeds 8 files: `ui.workbench.model.mode`, `ui.project.model.dialog`. |

- A new data class or enum goes in `model`, never beside the composable that uses it.
- `ui.components` holds only cross-feature Compose widgets — nothing feature-specific.
- Keep at most 8 files per directory.
- `RuleEditor` and the other `actual` declarations must stay in package `ui` in `jvmMain`, matching their `expect` in `commonMain`.

## Architecture & Constraints

### Kotlin Multiplatform (KMP) Boundaries
- **Logic/UI Separation:** All business logic, state calculation, and DSL parsing MUST reside in `commonMain` or `ruleengine-core`. `jvmMain` should only contain `actual` implementations for platform-specific APIs (File I/O, Clipboard, Window management).
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
- **Autocomplete:** Implementation lives in `ui.autocompletion` (`Builders.kt`, `Model.kt`).
- **Updating patterns:**
    - New DSL keywords/operators must be added to the `build*Completions` functions in `ui/autocompletion/Builders.kt`.
    - New DSL keywords/operators must also be added to the relevant `DSL_NAMED_OPS`, `DSL_STRUCTURE`, or `DSL_LOGIC` sets in `SyntaxHighlighter.kt`.
    - A new *structural* keyword (one that opens a block, like `when` / `then` / `else`) additionally needs:
      a `DslSection` case plus its transition in `ui/dsl/DslContext.kt`, an entry in `DSL_BLOCK_KEYWORDS`
      in `ui/editor/rules/DesktopRuleEditorItems.kt`, and a branch in `buildContextualCompletions`.
    - A clause with no value to edit (`stop`) belongs in the Builder as a **removable badge with an add
      button**, not as a row: a row would offer a dropdown and a value box for choices that do not exist.
      Hold it as a `Boolean` on the branch rather than an entry in the action list — that is what keeps it
      pinned to the end of the block however the author edits around it.
    - A new clause inside a rule block must round-trip through **both** `ui/builder/RuleAstToBuilderMapper.kt`
      and `ui/builder/BuilderToRuleDsl.kt`. The Builder replaces the whole rule text on every edit, so
      anything the mapper drops is deleted from the file.

## When to also read core instructions

Also read `ruleengine-core.md` when a UI change touches or depends on:

- DSL tokens
- operators
- field types
- parser behavior
- validation behavior
- evaluation behavior
- syntax highlighting derived from core lexer output
