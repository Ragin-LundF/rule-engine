# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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

