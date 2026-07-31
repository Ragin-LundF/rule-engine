# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).


## Release 1.3.0

### Added

- **Nested data in the field schema** — two new field types, `collection` (a list of records) and
  `object` (a single nested record), each declaring its members under a recursive nested `fields:`
  block. Declared members are validated at any depth, so `sum(orders[status == "paid"].items.price)`
  is checked segment by segment instead of being assumed numeric. Schemas that do not declare members
  keep working exactly as before.
- **Boolean conditions** — `isActive equals true` now parses, validates, compiles and evaluates.
  Boolean values may arrive as a `Boolean` or as the strings `"true"` / `"false"`.
- **Date support** — `date` fields are usable with `equals`, `gt`, `gte`, `lt`, `lte` and `between`,
  comparing quoted ISO-8601 calendar dates. Input may be a `LocalDate`, `LocalDateTime`, `Instant` or
  ISO string; anything carrying a time is reduced to its date.
- **Implicit AND** — conditions on consecutive lines are joined with `and`, so the keyword is optional.
- **Zero-argument actions** — an action declared `argTypes: []` is written in a rule as the bare name.
  The action schema already supported this; the parser demanded a literal.
- **Advanced expressions in the visual Builder** — aggregates, arithmetic, filtered paths at every
  level, `not` and `ignoreCase` are editable as condition rows instead of showing "Advanced syntax
  detected". Aggregate and calculation operands are gated to numeric comparisons. The schema editor
  edits nested fields as indented child rows.
- **Documentation fidelity test** — every rule example in `RULE-SPEC.md`, `README.md` and `docs/` is
  parsed, validated and compiled by an automated test, so the specification handed to AI assistants
  cannot drift from what the engine accepts.
- **`date_time` field type** (aliases `datetime`, `timestamp`) — a date with a time of day, compared at
  time precision with the same six operators as `date`. Input may be a `LocalDateTime`, a `LocalDate`
  (starting at midnight), an `Instant` (resolved at UTC) or a string.
- **Every field type is offered by "+ Add field"** in the visual schema editor. The menu previously
  hardcoded seven templates and omitted `string_set`, `date` and `date_time`, so those types were
  reachable only by adding a blank field and changing its type afterwards. The list is now derived from
  one place and a test asserts it covers every type.
- **Operator chips are filtered to the field's type** in the schema editor, so a `date` field no longer
  offers `contains` and a `string_set` field no longer offers `between`. An operator already present in a
  loaded schema but invalid for its type is still shown, marked, so it can be removed.
- **Unknown operator names are rejected when the schema loads** — `operators: [greaterThan]` now fails
  with `Unknown operator 'greaterThan' for field 'amount'` instead of silently restricting the field to a
  name no rule can use. `starts_with` / `ends_with` / `startswith` / `matches` are accepted as aliases of
  the canonical names, so schemas written by earlier versions of the editor keep loading — and their
  conditions now work rather than being rejected.
- **Per-field date format** — a `date` / `date_time` field may declare `format: "dd.MM.yyyy"`, a
  `DateTimeFormatter` pattern that governs both the incoming data value and the literal written in every
  rule for that field. Omitting it keeps today's ISO-8601 behaviour, so existing schemas and rules are
  unaffected. A `format` on a non-date field, a malformed pattern, or one that cannot represent a
  complete value is rejected when the schema loads. The schema editor edits the pattern per field, and
  the Builder and autocomplete offer value placeholders in it.

### Fixed

- **RULE-SPEC.md documented three features the engine could not perform** — implicit AND, boolean
  conditions and date fields. All three are now implemented rather than removed from the spec.
- **Rule replacement in the editor** dropped or duplicated rules whose body contained a `}` — most
  often inside a regex pattern such as `"^DE\d{18}$"`. Rule bodies are now located by counting braces
  outside strings and comments instead of by a regex.
- **Filter segments were validated against the top-level schema**, so `sum(transactions[label == "risk"].amount)`
  reported a spurious `Unknown field 'label'`. Filter fields now resolve against the element being
  filtered.
- **Bundled samples did not load.** Five rules used `between 20 and 500`, which the DSL does not accept;
  one used a zero-argument action the parser rejected; one used `between` on a field whose schema
  omitted that operator. All samples are now covered by a test.
- The editor's `between` value placeholder inserted the numeric `0 100` for date fields as well, which
  could never compile; date fields now get a pair of quoted date samples.
- **The schema editor offered operators and normalizers the engine does not have.** `not_equals`,
  `not_contains`, `isEmpty` and `isNotEmpty` were selectable but have no implementation anywhere in the
  engine; `starts_with` / `ends_with` were spelled in a form the validator did not recognise; `regex` was
  missing although text fields support it. The normalizer chips were missing `collapse_whitespace` and
  `remove_punctuation`, and the YAML autocomplete offered `ascii_fold`, which does not exist and produced
  a schema that failed to load.
- Normalizer chips are no longer shown for types the engine never normalizes (anything but `text` and
  `string_set`).
- The YAML `type:` autocomplete was missing `collection` and `object`, and spelled `stringSet` where the
  editor writes `string_set`.
- The rule editor's schema **Example** button inserted `greaterThan` / `lessThan`, which are not engine
  operators, so the example it produced rejected every rule written against it.
- The Builder offered `in` on numeric fields and `contains` on `string_set` fields; the engine allows
  neither.
- `sample-schema.yaml` declared `starts_with` on its `country` field, so `country startsWith "DE"` could
  not be used with the schema the README quick-start points at.

### Updated
- Upgraded `jackson` to `3.2.1`
- Upgraded `kotlin` to `2.4.10`


## Release 1.2.0

### Added

- **Sample Gallery** in the UI workbench — a built-in library of ready-to-run rule sets covering four
  domains: Financial Transactions (fraud detection, VIP classification, rent-payment routing),
  Log Filter (severity routing, slow-request escalation, service alerts), Product Recommendation
  (premium badges, category boosts, discount eligibility), and Access Control (IP filtering,
  role checks, time restrictions). Open a sample to instantly populate the editor with schema,
  actions, and rules without touching the file system.
- **Rule Table view** (`TABLE` tab in the center panel) — a scrollable, structured overview of all
  loaded rules showing conditions, actions, and match status at a glance, complementing the
  existing Code, Diagram, and Test views.
- **Manifest file picker** — a dropdown (☰) in the rule-editor header lets you switch between
  individual rule files within a manifest entry. In Diagram and Test views an additional
  **All files** option loads every rule file of the selected entry in one step.

### Changed

- Upgraded `kotlinx-coroutines-core` to `1.11.0` (explicit override of the older version
  previously pulled in transitively by Compose).

## Release 1.1.0

### Added

- **Output-based short-circuit evaluation** in `RuleEngine`, enabled via the new
  `shortCircuitByOutput` constructor flag (defaults to `false`). When enabled, rules are
  grouped by every static output they produce (`actionName:value`); each group is evaluated
  only until its first matching rule, then evaluation moves to the next group. This avoids
  redundant (and potentially expensive, e.g. regex) evaluations for outputs that are already
  settled — a significant speedup for large rulesets where many rules map to few outputs.
  A rule with multiple static outputs belongs to multiple groups and is reported at most
  once; rules with dynamic outputs (e.g. regex extraction) are always evaluated.

### Changed

- **BREAKING:** Removed the unused `schema` parameter from the `RuleEngine` constructor.
  Update call sites from `RuleEngine(compiledRules = rules, schema = schema)` to
  `RuleEngine(compiledRules = rules)`.
- With `shortCircuitByOutput = true`, matches are returned in output-group order rather than
  rule-declaration order. Default behavior (`false`) preserves full in-order evaluation.

