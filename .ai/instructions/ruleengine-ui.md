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
    - A new *structural* keyword (one that opens a block, like `when` / `then` / `else` / `not_exists`)
      additionally needs: a `DslSection` case plus its transition in `ui/dsl/DslContext.kt` (add it to
      `BRANCH_KEYWORDS`, and to `DslSection.isBranch()` if it holds output clauses — `literal()` and
      `closeBracket()` both dispatch on that), an entry in `DSL_BLOCK_KEYWORDS` in
      `ui/editor/rules/DesktopRuleEditorItems.kt`, and a branch in `buildContextualCompletions` whose
      `followingBranchKeywords` lists only the blocks the parser still accepts after it.
    - A new **output branch** is the same plus: `RuleBranch` in `ruleengine-model` (which makes most of
      the rest a compile error), `BuilderRule.Supported` and `BuilderEditorState` (`actionsOf`,
      `variablesOf`, `stopOf`, `setStop`, `hasBranch`, the id counters, `fromBuilderRule`, `removeAction`,
      `removeVariable`), an `OptionalBranch` call in `RuleBuilderView` **in the order the DSL requires**,
      `branchTitle` / `branchSubtitle` in `BranchSectionView`, a `RuleMatchStatus` case with its filter,
      colour, label and count in `ui/tester/RuleResultsView.kt`, and `RuleTablePanel` /
      `InspectorPanel` so the branch is not silently missing from the overview.
    - A clause with no value to edit (`stop`) belongs in the Builder as a **removable badge with an add
      button**, not as a row: a row would offer a dropdown and a value box for choices that do not exist.
      Hold it as a `Boolean` on the branch rather than an entry in the action list — that is what keeps it
      pinned to the end of the block however the author edits around it.
    - A new clause inside a rule block must round-trip through **both** `ui/builder/RuleAstToBuilderMapper.kt`
      and `ui/builder/BuilderToRuleDsl.kt`. The Builder replaces the whole rule text on every edit, so
      anything the mapper drops is deleted from the file.
    - A new **path segment** (`take` / `takeLast` / `sortBy` are all one) needs: a
      `BuilderPathDecoration` variant plus its accessor and `withXxx` in `ui/builder/model/BuilderPathStep.kt`,
      a branch in `OperandText.pathToDsl` **and** `pathToLabel`, a branch in
      `ui/builder/ValueExpressionMapper.kt`'s `mapPath`, a control in
      `ui/builder/components/path/PathBreadcrumb.kt`, and a marker in `PathSegmentPill`. Check
      `BuilderPathStep.withFilters` keeps the new decoration — it rebuilds the list, and what it does
      not know it drops. Only `ui/autocompletion/WhenCompletions.kt` needs the completion by hand;
      highlighting and the aggregate lists derive from `DslFunctions`.

## Validation in the editor

Three passes, and they are not interchangeable:

- **Per keystroke** — `RuleValidationRunner.run` on the open buffer, with
  `RuleEditorState.inheritedVariablesForOpenBuffer()` supplying the variables the manifest files listed
  *before* it publish. Without that, every cross-file `$name` reads as unknown.
- **The Validate button** — `validateOpenEntry()`, which runs core's `EntryValidator` over the whole
  entry and falls back to the per-buffer pass when there is no entry to validate.
- **Underlines** — only diagnostics that pass `ValidationDiagnostic.isAbout(openFile)`. An entry-wide
  result reports every file with lines relative to *its* file, so underlining the lot marks the wrong
  lines. The diagnostics panel shows them all and labels the ones from elsewhere.

## The Inspector and the right panel

Two invariants here are easy to break by adding code that looks correct in isolation.

- **The Inspector's rule selection is derived, not dispatched.** There are three separate notions of
  "selected rule": `RuleWorkbenchState.selectedRuleId` (what the Inspector reads),
  `BuilderRulesController.selectedId` (what every view highlights) and `TestInputState.selectedRuleId`
  (which rule to *run*, where blank means all). The live one is the second. `RuleEditor` reads it once —
  via `inspectorSelectionFor` — and dispatches `SelectRule` from a single `LaunchedEffect`, so a new way
  of choosing a rule cannot forget to update the panel. Do not add a `SelectRule` dispatch at a click
  site; make the click reach `BuilderRulesController` and the panel follows.
  The effect is guarded on `appArea == RULES` so a field or action selected in the Schema or Actions
  area survives until the user returns to the rules.
- **`RightPanelController` is the only writer of the panel's open state and tab**, because both are
  persisted through `RightPanelPersistence`. Flipping `RuleEditorState.rightPanelExpanded` directly is
  what makes the stored value drift from the one on screen.

`InspectorPanel` takes `builderState` *and* `ruleStates` and they are not interchangeable: the condition
branch needs the open builder state, because that is where the inspected row lives, while the rule branch
needs the selected rule's own — in code mode the caret can sit in a rule the builder does not hold open,
and reading `builderState` there reports one rule's id with another rule's counts.

A row in the schema or action tables gets its inspect affordance as a **36 dp button**, not a click on
the row: every cell is a text field, so a row-wide target fights the editing under it, and the header
reserves exactly 36 dp per trailing button — a default-width `TextButton` takes 64 dp out of the weighted
columns beside it, which is enough to squeeze the longest chip to one letter per line.

## The project session and the manifest entry

There are **three** separate notions of "the selected entry", and mixing them up has caused the same
bug twice.

| store | authoritative for |
|---|---|
| `ProjectSession.activeEntryId` | the project on disk: the save target, the linked-file chips, the status bar |
| `RuleEditorState.selectedManifestEntry` | everything read from the parsed manifest: rule files, scope, diagrams, export |
| `TestInputState.selectedRuleId` | which rule to *run* (blank means all) — unrelated, do not reuse |

Nothing observes them into agreement; sync is imperative and funnels through
`ProjectWorkspace.performSelectEntry`. So:

- **Anything that replaces what is on screen must go through `ProjectWorkspace`,** not just
  `RuleEditorState`. `applySample` writing only the editor state is what left the session — and with it
  the save target — describing the project a sample had just replaced. `ProjectWorkspace.loadSample`
  takes the editor write as a lambda for exactly this reason: it cannot be called without the clearing.
- **A load that clears buffers must clear the dirty baselines too** (`ProjectLoader.load` does;
  `applySample` did not). Dirtiness is a comparison against the last thing read from disk, so stale
  baselines make "unsaved" a claim about the wrong files.
- **Do not invent a `ProjectSession` for something with no location on disk.** `root` is a real `Path`;
  a null session means scratch, and the first save asks where to put it.
- **Read entry state through `manifestEntrySelection`**, not off the session directly — the session is
  null for a sample, and the parsed manifest is the source that always describes what is on screen.

Three further desyncs are known and not yet fixed: renaming the active entry in the Manifest area
leaves `selectedManifestEntry` pointing at an id that no longer exists (which empties
`currentEntryRulePaths()` and lets `loadRuleFiles` wipe `inMemoryRuleFiles` — data loss);
`onReloadConflict` discards its `ProjectLoadResult`; and a scratch project's first save sets
`activeEntryId` without ever setting `selectedManifestEntry`. If you touch this area, note that **no
test asserts `session.activeEntryId == state.selectedManifestEntry`** — that gap is why all four
survived.

## When to also read core instructions

Also read `ruleengine-core.md` when a UI change touches or depends on:

- DSL tokens
- operators
- field types
- parser behavior
- validation behavior
- evaluation behavior
- syntax highlighting derived from core lexer output
