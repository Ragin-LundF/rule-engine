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

