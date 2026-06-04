Rule Engine — Small DSL + YAML schema

Overview

This repository contains a small, pluggable rule engine written in Kotlin. Key features:
- YAML field schema loader
- Small DSL for rules (.rule files)
- Parser, validator, compiler and compiled evaluator
- Tracing / decision tree export
- CLI tools for validation and evaluation

Quick CLI examples

Validate rules (human-readable):

```bash
./gradlew run -PmainClass=ruleengine.cli.ValidatorCli --args="--schema src/test/resources/sample-schema.yaml --rules src/test/resources/rules"
```

Validate rules and print JSON diagnostics:

```bash
./gradlew run -PmainClass=ruleengine.cli.ValidatorCli --args="--schema src/test/resources/sample-schema.yaml --rules src/test/resources/rules --format json"
```

Validate rules with a custom action schema:

```bash
./gradlew run -PmainClass=ruleengine.cli.ValidatorCli --args="--schema src/test/resources/sample-schema.yaml --rules src/test/resources/rules --actions src/test/resources/actions.yaml"
```

Run a sample evaluation using the CLI (evaluate an input JSON):

```bash
./gradlew run -PmainClass=ruleengine.cli.EvaluateCli --args="--schema src/test/resources/sample-schema.yaml --rules src/test/resources/rules --input-file src/test/resources/sample-input.json --trace --format pretty-json"
```

File formats

- Field schema (YAML): `src/test/resources/sample-schema.yaml` (see tests for an example)
- Actions schema (YAML): `src/test/resources/actions.yaml`
- Rules (.rule): `src/test/resources/rules/*.rule` (sample rules used by tests)

Rules
-----

The engine uses a concise DSL for rules. A rule file looks like:

```text
rule "rent-payment" {
  when
	purpose contains "miete"
	and amount >= 500

  then
	label "rent"
}
```

DSL overview
- Each rule begins with `rule "id" {` and contains a `when` block (the condition) and a `then` block (a sequence of actions).
- Conditions are boolean expressions built from field comparisons combined with `and`, `or` and `not`.

Supported operators (examples)
- Text fields (FieldType.TEXT):
  - equals: `name equals "Alice"`
  - contains: `description contains "urgent"`
  - startsWith / endsWith: `title startsWith "Mr"`
  - in: `country in ["DE","AT"]` or `country in "DE"`
  - regex: `email regex "^.+@example\\.com$"` (pattern must be a string)
  - optional modifier: `ignoreCase` after the literal: `name equals "alice" ignoreCase`

- Numeric fields (FieldType.INTEGER / FieldType.DECIMAL):
  - equality: `amount equals 100`, also `==`, `=`, `eq` are accepted
  - comparisons: `>`, `<`, `>=`, `<=` or `gt`, `lt`, `gte`, `lte`
  - between: `age between 18 65` (two numeric bounds)

- String set fields (FieldType.STRING_SET):
  - containsAny / containsAll: `tags containsAny ["premium","vip"]`
  - single string treated as containsAny: `tags containsAny "vip"`

Actions and action schema
- Actions appear in the `then` block as identifiers followed by one or more literal arguments.
- The available actions and their expected argument types can be defined in an actions YAML file. Example (`src/test/resources/actions.yaml`):

```yaml
actions:
  label:
	argTypes: [string]
  score:
	argTypes: [integer]
```

- When provided, the compiler/validator checks that rule actions exist and that arguments match expected types.

Manifest
- A small `manifest.yaml` can point to the schema, actions and a list of rule files. The manifest loader (`ruleengine.manifest.ManifestLoader`) resolves paths relative to the manifest location.

Example `manifest.yaml`:

```yaml
name: my-rule-project
entries:
  - id: sample
	schema: sample-schema.yaml    # relative path to a field schema YAML
	actions: actions.yaml         # relative path to an actions schema YAML
	rules:
	  - rules/rent.rule           # one or more rule files (relative paths)
```

Implementation Guide
--------------------

This section describes the main components and how to extend or embed the engine.

Core components
- `ruleengine.dsl` — lexer and parser for the `.rule` DSL. Use `ruleengine.dsl.parser.Parser` to parse rule text into ASTs:

```kotlin
val ruleText = java.nio.file.Files.readString(java.nio.file.Path.of("rules/rent.rule"))
val asts = ruleengine.dsl.parser.Parser(input = ruleText).parseRules()
```

- `ruleengine.compiler.Validator` — validates ASTs against a `FieldSchema` and optional `ActionSchema`.
- `ruleengine.compiler.Compiler` — compiles ASTs into `ruleengine.evaluator.CompiledRule` objects.
- `ruleengine.evaluator.RuleEngine` — evaluates compiled rules against prepared inputs and can produce a DecisionTree trace.
- `ruleengine.schema.FieldSchemaLoader` — loads field schema YAML files. There's also `FieldSchemaLoader.loadFromString(...)` for UI flows.

Typical Kotlin embedding (manifest-first example)

Start by loading a manifest (paths are relative to the manifest file). The example below shows a runnable flow that:
1) loads the manifest, 2) loads schema and actions, 3) parses/validates/compiles rules, and 4) evaluates a JSON input file referenced relative to the manifest.

```kotlin
// 1) Load project manifest (paths inside are resolved relative to this file)
val manifestPath = java.nio.file.Path.of("src/test/resources/full-manifest.yaml")
val manifest = ruleengine.manifest.ManifestLoader.load(path = manifestPath)

// pick the first entry (example manifest contains a single entry named "full")
val entry = manifest.entries.first()
val baseDir = manifestPath.parent

// 2) Load schema and actions (paths are relative to the manifest)
val schema = ruleengine.schema.FieldSchemaLoader.load(path = baseDir.resolve(entry.schema!!))
val actions = ruleengine.schema.ActionSchemaLoader.load(path = baseDir.resolve(entry.actions!!))

// 3) Read and parse rules referenced by the manifest
val ruleAsts = entry.rules.flatMap { rel ->
  val rulePath = baseDir.resolve(rel)
  ruleengine.dsl.parser.Parser(input = java.nio.file.Files.readString(rulePath)).parseRules()
}

// 4) Validate and compile
val validation = ruleengine.compiler.Validator.validate(asts = ruleAsts, schema = schema, actions = actions)
if (!validation.isValid) {
  throw IllegalStateException("Invalid rules: ${'$'}{validation.diagnostics}")
}
val compiled = ruleengine.compiler.Compiler.compileRules(asts = ruleAsts, schema = schema)
val engine = ruleengine.evaluator.RuleEngine(compiledRules = compiled, schema = schema)

// 5) Evaluate an example JSON input file stored next to the manifest
val inputPath = baseDir.resolve("inputs/rent-input.json")
val inputJson = java.nio.file.Files.readString(inputPath)
val inputMap = runCatching { ruleengine.jackson.JacksonUtil.jsonMapper.readValue(inputJson, Map::class.java) as Map<String, Any?> }.getOrElse { throw it }
val ruleContext = ruleengine.evaluator.context.RuleContext.of(entries = inputMap.entries.map { it.key to it.value }.toTypedArray())
val prepared = ruleengine.evaluator.context.PreparedRuleContext.prepare(ctx = ruleContext, schema = schema)
val result = engine.evaluate(prepared = prepared, includeTrace = true)
println(result)
```

The repository includes a test that demonstrates this flow using `src/test/resources/full-manifest.yaml` and a set of sample inputs at `src/test/resources/inputs/`. See `ruleengine-core/src/test/kotlin/ruleengine/FullManifestIntegrationTest.kt` for a fully runnable example that executes the manifest entry and asserts expected actions for the provided JSON inputs.

Extending the engine
- Adding operators: implement new compilation logic in `ruleengine.compiler.operators` and update `Compiler.compileTextCondition` / numeric handlers to call your new operator.
- Adding normalizers: register a `Normalizer` in `ruleengine.core.normalizer.NormalizerRegistry` so `PreparedRuleContext` and compiler will apply it during preparation and compilation.
- Actions: extend the actions YAML and, if needed, add runtime wiring for action handling after evaluation.

Project structure notes
- `ruleengine.dsl` — lexer/parser for the DSL
- `ruleengine.compiler` — validator + compiler
- `ruleengine.evaluator` — compiled expressions, evaluation and trace
- `ruleengine.schema` — YAML loaders for schemas (fields and actions)
- `ruleengine.cli` — small CLI tools for validation and evaluation

Development

Run the test suite:

```bash
./gradlew test
```

If you'd like, I can add a short example project or a small Gradle task that runs a sample evaluation and prints the DecisionTree JSON.
