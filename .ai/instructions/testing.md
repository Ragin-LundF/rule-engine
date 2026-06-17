# Testing Instructions

Use this file when adding, modifying, or reviewing tests.

## General test rules

- Use `kotlin.test` annotations and assertions when possible.
- Use named arguments for assertions.
- Cover positive paths, negative paths, edge cases, and error paths.
- Add or update tests for behavior changes.
- Keep test names descriptive and behavior-focused.

## ruleengine-core tests

- Unit tests mirror main source packages.
- Integration tests such as `FullManifestIntegrationTest` load real YAML schemas and `.rule` files from `src/test/resources/`.
- New integration scenarios should follow the existing real-resource pattern.
- Inline DSL strings in tests must be valid rule DSL parseable by `Parser`.
- Use `trimIndent()` for multi-line DSL strings.
- Always assert both positive match and negative no-match evaluation paths.
- When testing validation, assert exact `Severity` values.
- When testing validation, inspect the `diagnostics` list instead of only checking `isValid`.
