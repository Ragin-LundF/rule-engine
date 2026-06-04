Rule Engine — Small DSL + YAML schema

Overview

This repository contains a small, pluggable rule engine in Kotlin with:
- YAML field schema loader
- small DSL for rules (.rule files)
- parser, validator, compiler and compiled evaluator
- tracing/decision tree export
- CLI validator with JSON output

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

File formats

- Field schema (YAML): `src/test/resources/sample-schema.yaml` is a small example used in tests.
- Actions schema (YAML): `src/test/resources/actions.yaml` is an example action schema.
- Rules (.rule): `src/test/resources/rules/*.rule` contains sample .rule files used by tests.

Development

Run the test suite:

```bash
./gradlew test
```

How to (project files / manifest)

The project can be described with a small manifest YAML that references field schema, actions schema and rule files. Place a `manifest.yaml` (or similarly named file) next to the referenced resources. The loader in `ruleengine.manifest.ManifestLoader` expects paths in the manifest to be relative to the manifest file location.

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

Notes:
- `schema` should point to a YAML file containing the field definitions used by the rules.
- `actions` should point to a YAML file defining available actions and their argument types.
- `rules` is a list of rule file paths (typically `.rule` DSL files). Paths are resolved relative to the manifest file location.
- The test suite includes an example manifest at `ruleengine-core/src/test/resources/manifest.yaml` that references the sample schema, actions and the `rules/rent.rule` sample rule.

Project structure notes

- ruleengine.dsl — lexer/parser for the DSL
- ruleengine.compiler — validator + compiler
- ruleengine.evaluator — compiled expressions and tracing
- ruleengine.schema — YAML loaders for schemas (fields and actions)
- ruleengine.cli — small CLI validator

Extensions and next steps

- Add more operators (regex, between)
- Add indexing / performance optimizations
- Provide a REST or library API to evaluate inputs and return DecisionTree JSON

If you want, I can add example evaluation commands that run the engine against a sample input and print the DecisionTree JSON.

