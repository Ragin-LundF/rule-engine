Rule Engine — UI and Core

This workspace contains two main modules:
- ruleengine-core — the rule engine core (parser, compiler, validation, schema loaders)
- ruleengine-ui   — a simple Compose Desktop + web UI to load schema and rules, validate, save or copy rules

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

- The desktop UI uses `FieldSchemaLoader.loadFromString(...)` to load schemas from pasted or opened files and `Parser(...)` + `Validator.validate(...)` to validate rules. Diagnostics are shown in the UI.
- YAML parsing is handled by creating a YAML parser and using the centrally configured JSON mapper to deserialize into domain DTOs (this keeps configuration consistent across JSON and YAML).
- You may see a few deprecation warnings from the Jackson dataformat API (createParser(InputStream) is marked deprecated). These are non-blocking and the parsing flow is compatible with the core.

If you want, I can:
- Add better support for action schemas in the UI and allow loading/applying them to validation (currently validation runs without actions).
- Improve the YAML parsing path to remove Jackson deprecation warnings entirely by using an alternate approach.
- Implement the web UI wiring (the repository contains a simple `jsMain` UI but the Gradle module is currently configured for desktop).
