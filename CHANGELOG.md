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

