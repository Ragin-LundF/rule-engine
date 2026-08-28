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
| `ui.<feature>.<role>` | Further behavior groups when the feature package exceeds 8 files: `ui.workbench.areas`, `ui.builder.outline`, `ui.builder.board`, `ui.builder.inspector`. |
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

## The Builder: two canvases, one selection

The Builder is two renderings of one rule. Adding a third, or changing either, means keeping the
invariants below — they are the reason the pair does not become two half-editors that disagree.

- **`ui.builder.outline`** — the reading canvas: one line per condition, joins as pills on the gutter,
  groups as bracket rails, and the outcome blocks in DSL order. Nothing in it expands, so the row being
  worked on never moves.
- **`ui.builder.board`** — the run: every rule in evaluation order along the top, then the selected rule
  as drag-and-droppable rails and three outcome lanes.
- **`ui.builder.inspector`** — the editing surface for **both**. There is no other one.

### The selection is one value, held where it already was

`InspectorItem.Condition(conditionId, steps)` and `InspectorItem.Statement(branch, statementId, steps)`
*are* the selection store. Both carry a `List<SelectionStep>` — a positional path into the expression
(`Left`, `Argument(2)`, `Segment(1)`, `Filter(0)`, …) — so depth is navigation, not layout.

There is deliberately **no** `BuilderSelectionController`. The dispatch path already existed
(`WorkbenchAction.SelectInspectorItem`), and a second store for node selection would be the same trap
this file already records for rule selection. Canvases dispatch; the Inspector reads; `CanvasSelection.of`
is the one place that decides which of the three pieces a canvas gets.

### The row's DSL form is derived, never toggled

`ui.builder.RowForm` decides whether a row is a *simple condition* or a *value-expression comparison*
from its operands, the way the parser decides it from the text:

- a plain field path (no filters, sort or slice, not a `$var`) against a literal or a list is a simple
  condition — the only form the **named** operators (`contains`, `between`, `in`, `startsWith`, …) exist
  in;
- anything computed is a comparison, and only `== != > >= < <=` can spell it.

`blockedPromotion` refuses, with a reason naming the operator, rather than converting. The button this
replaced was a **one-way door that lost data**: it hard-coded `==`, so `amount >= 300` became
`amount == 300`, and it dropped `valueTo`, `listItems` and `ignoreCase`. Because the Builder regenerates
the whole rule text on every edit, that was data loss on disk. Do not add a "convert to…" action that
cannot round-trip.

### Adding a canvas — the checklist

1. Add the value to `RuleMode`, `ViewMode` and **both** directions of `RuleModeMapping`. The exhaustive
   `when`s in `CenterEditorPanel` will then tell you what is missing.
2. Do **not** add a tab to the area header's `ModeTabs` if the new canvas shows the same rule with the
   same selection. Pass it to `AreaHeader(subTabs = …)` as a **subordinate** `ModeTabs` instead, the way
   Outline/Board is. The tabs change what the centre panel *is*; a canvas changes how one rule is drawn,
   and `subordinate = true` is what keeps the two from looking alike — no container, and the selected
   item is merely raised rather than accented. (This switch used to float on the canvas itself. It moved
   so that every view switch in the app is in one strip; the rule it still has to satisfy is that it
   never reads as a mode tab.)
3. Read the selection, never store it. Write it with `SelectInspectorItem` and open the panel with
   `RightPanelController.showInspector()`.
4. Render row text through `BuilderToRuleDsl.renderRow` and operands through `OperandText.toDsl`. Two
   renderers drift; one cannot.
5. Every new model state must round-trip through **both** `RuleAstToBuilderMapper` and
   `BuilderToRuleDsl`. Anything the mapper cannot represent is deleted from the file on the next edit.

### Things that must not change

- **`stop` is a `Boolean` on the branch and a badge in the UI**, never a row. A `stop` in the middle of a
  branch does not parse, and a flag cannot drift from the end of its block.
- **State a ribbon group's width from its item count** (`n × card + (n − 1) × arrow`), never from an
  intrinsic measurement. The HTML prototype of that ribbon painted cards on top of each other in two of
  three browser engines because each computed a different intrinsic width; `Row` + `horizontalScroll`
  with intrinsic children fails the same way and just as silently.
- **A refused gesture says why.** `blockedRemoval`, `blockedMove`, `blockedPromotion` and
  `validateDrop` all return a reason, and it reaches the status bar. A drag that springs back silently is
  indistinguishable from a broken drag, and a refusal is where the DSL gets taught.
- **Only a rule earlier in the run publishes a `$variable`.** `VariableFlow` encodes this; a reader whose
  producer runs later is an `orphanReader`, not a reader. That case — a rule that parses, validates, runs
  and can never fire — is the board's one genuine warning, and no single-rule view can see it.

### What the Builder cannot do yet, and why

Core diagnostics cannot be attributed to a **row**. A `ValidationDiagnostic` carries a file and a line;
nothing in the chain — parser AST → `RuleAstToBuilderMapper` → `BuilderRule` → `BuilderEditorState` —
records which line a row came from. Doing it properly means carrying line provenance the whole way
through. Until then the dock's **Checks** tab shows the open file's diagnostics, and
`ui.builder.RowIssues` covers the incomplete-row cases the Builder has complete information about on
its own.

Note what *is* now known, because the older wording said otherwise: the Builder can locate its rule in
the file exactly, via `findRuleBlockRange` in `ui/RuleDslBlocks.kt`. That is what the preview dock's
highlight is built on. What is still missing is the other direction — a row to a line — and
`rowLineRanges` only approximates it, by matching the row's generated text *inside* that block.

## The area header

`ui.components.header.AreaHeader` is the one header above all four editor areas, and it has four slots
in one order: **title** (what this is), **binding chip** (the file it is bound to), **mode tabs** (how it
is being shown), and **actions**, right-aligned. The body underneath is the only thing that differs
between areas. A fifth layout is not an option — that is what this replaced.

- **One vocabulary.** The first tab is always **Visual** and the text tab is always **Code**, in every
  area, from `displayName` on the mode enums in `ui.workbench.model.mode`. `ModeDisplayNamesTest` fails
  the moment an area invents a third word.
- **The mode is workbench state.** It lives in `RuleWorkbenchState` and changes by dispatching
  `SelectRuleMode` / `SelectSchemaMode` / `SelectActionMode` / `SelectManifestMode`. No panel owns its
  own mode, and `YamlModelSync` deliberately does not have one.
- **The area owns its collapse policy.** `tabs` and `subTabs` are slots that receive the measured
  `BarDensity`, and `AreaHeader` takes `fullWidth` / `compactWidth`, because a two-tab header and the
  five-tab Rules one do not need the same room. Measure against the **panel**, not the window: the
  centre panel gives up a rail and usually the Inspector.
- **Rank the actions, never squeeze them.** `ActionEmphasis.PRIMARY` keeps its label at every width and
  never moves into the overflow; `STANDARD` falls back to its glyph and keeps its label in the semantics
  tree; `OVERFLOW` is only ever in the `⋯` menu. The row this replaced shared one line with the tabs,
  and the last button's label wrapped one letter per line.
- **The binding chip is the elastic slot.** It has a ceiling per density and truncates inside it. Do not
  give it a `weight`: a weighted chip in a full row is measured at zero width and disappears, and the
  control naming the open file is the one thing that must not.

## The preview dock

`ui.dock` is one dock under five surfaces — the Builder's two canvases and the Schema, Actions and
Manifest editors — and the reason it is one is that five previews of "the file you are about to write"
would be five things to keep in step. It replaced `OutlineDock`, which was the Builder's alone.

- **`CanvasDockScaffold` lives inside the centre panel, never in `WorkbenchShell`.** Its
  `BoxWithConstraints` is what gives the height clamp the available height exactly; in the shell it
  would have to be inferred by subtracting the intrinsic heights of the top bar, the diagnostics section
  and the status bar, and getting that wrong pushes the status bar off the window.
- **Two clamps, and only one is stored.** `DockController.setHeight` clamps to the constants and
  persists. The scaffold caps the *rendered* height at `maxHeight - MIN_CANVAS_HEIGHT` and never writes
  that back, so a small window borrows height instead of overwriting a preference set on a large one.
  The right panel's `MAX_WIDTH` is an absolute constant and has the bug this avoids.
- **`Usages` and `Checks` are tabs of the dock, never modes of an area.** They used to be
  `SchemaMode.USAGES`, `ActionMode.USAGES` and `ManifestMode.CHECKS`, so looking at which rules read a
  field replaced the field being edited. The mode values are deleted; do not add them back. A mode
  replaces the canvas, and everything about the open file that is *not* the file belongs beside it.
- **A check names its subject rather than describing it.** Every `SchemaIssue` carries the path, action
  name or entry id it is about, which is what lets a `CheckList` row select it. A check whose text
  describes the row in prose cannot be clicked, and that was the whole difference between reporting a
  problem and going to it.
- **A highlight sets `background` and nothing else.** The syntax highlighters set `color`, weight, style
  and decoration and never `background`, which is the only reason the two layers compose. `OutlineDock`
  set `color = PrimaryBlue` too, so the one line the reader was looking at was the one line that lost
  its syntax colours.
- **Memoize an `annotate` call on `ThemeController.isDark`.** A colour read inside `remember` does not
  subscribe to the theme — see the note in `Theme.kt` — so without it the preview keeps the previous
  theme's colours until its text next changes.
- **`DockSurface` has four entries for five surfaces.** Outline and Board are one rule file with one
  selection, and whether the dock starts open is a property of the area. Giving the canvases separate
  identities is how switching canvas starts closing the dock.
- **The default open state lives on `DockSurface.openByDefault`**, not at the call sites. It is open for
  `RULES` — seeing the DSL a row generates is how the Builder teaches the language — and closed for the
  other three. This reverses `OutlineDock`'s "collapsed by default, it is reference material not the
  work", which was written when the dock showed only the generated rule and could not be resized.
- **Ranges belong in tested functions, not in the renderer.** `findRuleBlockRange` / `rowLineRanges`
  (`ui/RuleDslBlocks.kt`) and `schemaFieldRange` / `actionRange` / `manifestEntryRange`
  (`ui/dock/YamlRanges.kt`). The YAML ones encode the bridges' layout, so their tests drive the **real
  bridges** — a writer that changes its indentation then fails a test instead of silently moving a
  highlight.

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
- **`RightPanelController` is the only writer of the panel's open state, tab and width**, because all
  three are persisted through `RightPanelPersistence`. Flipping `RuleEditorState.rightPanelExpanded` or
  `rightPanelWidth` directly is what makes the stored value drift from the one on screen. The width in
  particular is clamped inside `setWidth` and again in `RightPanelPersistence.loadWidth`, so a value
  saved on a wide display cannot come back as a layout with the drag handle off the edge.

`InspectorPanel` takes `builderState` *and* `ruleStates` and they are not interchangeable: the condition
branch needs the open builder state, because that is where the inspected row lives, while the rule branch
needs the selected rule's own — in code mode the caret can sit in a rule the builder does not hold open,
and reading `builderState` there reports one rule's id with another rule's counts.

The 36 dp `ⓘ` button convention is **gone**, along with the tables it was a workaround for. It existed
because every cell of a schema or action row was a text field, so a row-wide click target would have
fought the editing under it. The canvases have no editing controls, so the whole row is the target and
the selection carries the **dotted path**.

## The three canvases

`ui.schema.canvas.SchemaCanvas`, `ui.actions.canvas.ActionsCanvas` and `ui.manifest.canvas.ManifestCanvas`
are the same shape as `ui.builder.outline`: one line per declaration, read-only, with the Inspector doing
the writing. The rules that are easy to break by adding something that looks right in isolation:

- **A row carries no editing control.** The controls that appear on the selected row are structural —
  add a member, remove — exactly as `OutlineRows.RowActions` is. Put an editor on a row and the area
  stops being readable, which is the whole defect the tables had.
- **A short label is `softWrap = false`.** The token `FlowRow` wraps between parts; a part never wraps
  inside itself. Without this, `integer` renders as `inte` / `ger` as soon as the Inspector is dragged
  wide enough to squeeze the canvas — measured in the HTML prototype, and it will happen again.
- **A nested structure is a bracket rail with `Modifier.height(IntrinsicSize.Min)`.** Without the
  intrinsic height the rail `Box` has none of its own and collapses, leaving the group with no bracket.
  Same requirement, same reason, as the Builder's condition groups.
- **A refused gesture says why.** `SchemaCanvasGuards.blockedRemoval` and `ActionCanvasGuards` refuse to
  delete a field a rule reads or an action a rule emits, naming the rules, and the reason reaches the
  status bar.
- **Issues come from `SchemaIssues` / `ActionIssues`, never computed in the renderer.** The row shows the
  first non-note issue and the dock's Checks tab lists them all; two implementations would be two
  answers, and the row is the one people would trust. A `NOTE` is not a row issue — "no rule reads this"
  is already said by the `unread` tag on the same line.
- **The manifest's paths are navigation.** It is the one file whose whole job is to point at the other
  three, so clicking a path opens that area.
- **A rule file's row says what it publishes and what it reads.** `ManifestVariableFlow` answers it, and
  it is built on `VariableFlow.of` + `RibbonModel.groups` — the board's own derivation — precisely so the
  two surfaces cannot give different answers about the same files. It is shown for the **active entry
  only**: the loaded rules are that entry's rules, and a sibling may list the same files in another
  order, which is exactly what changes the answer.
- **The active entry is the session's.** `session.activeEntryId` and
  `RuleEditorState.selectedManifestEntry` are two notions of the same thing, and nothing observed them
  into agreement — so renaming the active entry left the editor looking up an id the manifest no longer
  had, and the next `loadRuleFiles` wrote an empty map over the working copy. `ProjectWorkspace`
  adopts the session's entry on every apply and every save; `ManifestSessionSyncTest` is the invariant.
- **The active entry falls back to the first.** A sample has no `ProjectSession`, so
  `session.activeEntryId` is null and the sole entry would otherwise be labelled "not the entry being
  edited". `RuleEditorState.activeScope` already makes the same fallback, and so does the CLI for a
  manifest without `--entry`.

## The Inspector is the writer

The Schema, Actions and Manifest inspectors used to be read-only summaries while the tables beside them
did the editing. That is now reversed, and three invariants keep it that way.

- **One model, held above both.** `RuleEditorState.schemaEditor` and `.actionEditor` are
  `YamlModelSync` holders — the panel draws them and the Inspector writes them, so they cannot disagree
  about what the schema currently is. They used to be `remember`ed inside `SchemaEditorPanel`, which put
  them out of the Inspector's reach entirely.
- **The sync effects live in `RuleEditor`, not in the area.** The Inspector can edit a field while
  another area is on screen, and an effect that is not composed cannot push that edit to the YAML.
- **A model with a blank or duplicate key is not serialized.** `SyncModelAndYaml` skips the push, because
  the writers drop such entries — pushing would delete the row the author is still typing. This is why
  the model rather than the YAML is the in-memory source of truth, and why the dock labels the Schema and
  Actions previews *(last valid)* when the model has not been publishable.

**Two lists in the schema format are ordered, and both were rendered as sets.** `normalizers` is a chain
applied left to right; `argTypes` is a positional parameter list whose arity and per-index type
`Validator` checks. Use `OrderedListEditor` for either — `allowDuplicates` is the only difference, and it
is what makes `audit(string, string)` expressible. Do not put either back behind a chip row: a chip
cannot hold an order, and it cannot hold the same value twice.

**A path is typed first and picked second.** `PathField` is a text box with a `Choose…` button beside
it, never a button alone: a manifest path is frequently one that does not exist yet, because the saver
creates `rules/` and `schemas/`, and no dialog can offer a file nobody has written. The picker returns a
path **relative to the manifest**, relativized by `ProjectWorkspace` — the Inspector is `commonMain` and
has no business knowing where the project lives. Before the first save the button is disabled and
`ProjectWorkspace.chosenPathBlockedReason` says why, shown both on hover and as a line under the field;
`choosePathForManifest` refuses in that state too, so a caller that ignores the reason still cannot write
an absolute path into a file that addresses everything relatively.

**A value the engine forbids stays visible.** `ReasonedChipRow` renders it dashed, prints the reason, and
leaves it clickable *while it is selected* so it can be removed. Hiding it would let the editor silently
disagree with the file on disk; disabling it would leave the YAML tab as the only repair.

**A field is reached by its dotted path**, never by leaf name — `ui.schema.findByPath` /
`updateAtPath` / `removeAtPath`. A leaf name is not unique, and `EditableFieldPathsTest` pins that with
a `lender` under three different parents.

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
