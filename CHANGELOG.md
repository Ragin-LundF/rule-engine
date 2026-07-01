# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).


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

