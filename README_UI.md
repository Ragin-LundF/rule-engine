Rule Engine — UI and Core

This workspace contains two main modules:
- `ruleengine-core` — the rule engine core (parser, compiler, validation, schema loaders)
- `ruleengine-ui`   — a simple Compose Desktop + web UI to load schema and rules, validate, save or copy rules

Quick commands

From the project root:

Run core tests:

```bash
./gradlew :ruleengine-core:test --no-daemon --console=plain
```

Build the full project:

```bash
./gradlew build --no-daemon --console=plain
```

Run the desktop UI (Compose Desktop):

```bash
./gradlew :ruleengine-ui:run --no-daemon --console=plain
```

Notes

- The desktop UI uses `FieldSchemaLoader.loadFromString(...)` to load schemas from pasted or opened files and `ruleengine.dsl.parser.Parser(...)` + `ruleengine.compiler.Validator.validate(...)` to validate rules. Diagnostics are shown in the UI.
- YAML parsing is handled by creating a YAML parser and using the centrally configured JSON mapper to deserialize into domain DTOs (this keeps configuration consistent across JSON and YAML).
- You may see a few deprecation warnings from the Jackson dataformat API (createParser(InputStream) is marked deprecated). These are non-blocking and the parsing flow is compatible with the core.

UI Implementation Guide
-----------------------

The `ruleengine-ui` module contains a Compose-based editor component (`ui/RuleEditor.kt`) that demonstrates how to embed the core functionality in a desktop or web UI.

Key integration points
- Load field schema from a string or file: `ruleengine.schema.FieldSchemaLoader.loadFromString(content, nameHint)`
- Parse rules: `ruleengine.dsl.parser.Parser(input).parseRules()`
- Validate: `ruleengine.compiler.Validator.validate(asts, schema, actions?)`
- Optional: use `ruleengine.manifest.ManifestLoader` to parse a `manifest.yaml` and load multiple entries.

Example (UI flow in Kotlin)

1) User pastes or opens a YAML schema -> call `FieldSchemaLoader.loadFromString(schemaText, "ui-schema")`.
2) User edits/pastes rules -> call `Parser(input = rulesText).parseRules()` and then `Validator.validate(asts, schema)` to get diagnostics.
3) Show diagnostics and, if valid, allow saving or exporting compiled rules via `Compiler.compileRules(...)`.

Notes and next steps
- The UI currently validates rules without applying the actions schema by default; adding an option to load and apply `actions.yaml` is straightforward using the same ActionSchema loader in the core.
- The project contains a `jsMain` entry with a minimal web UI; the Gradle configuration currently runs the desktop app by default but can be adapted to build a web bundle.

If you'd like, I can add a small demo that runs a complete edit -> validate -> evaluate cycle inside the UI and exports a DecisionTree JSON result.
