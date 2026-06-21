# Rule Engine

![logo.png](docs/assets/logo.png)

A lightweight, pluggable rule engine written in Kotlin.
Write business logic as plain-text rules — no code required for rule authors.

---

## What It Does

The Rule Engine lets you describe business decisions as human-readable rules.
You define **conditions** ("when this is true") and **outcomes** ("then do this"),
and the engine evaluates every rule against incoming data automatically.

**Example — flag a bank transaction as a rent payment:**

```
rule "rent-payment" {
  when
    purpose contains "rent"
    and amount >= 500

  then
    label "appartment-rent"
}
```

**Example — flag accounts where risk transactions exceed 3 % of total volume:**

```
rule "risk-ratio" {
  when
    sum(transactions[label == "risk"].amount) > sum(transactions[amount > 0].amount) * 0.03

  then
    flag "high_risk_ratio"
}
```

Rules are stored as plain `.rule` files, validated against a field schema, and evaluated at runtime without redeployment.

---

## Key Features

- **Human-readable DSL** — rule authors do not need to write code
- **Field schema** — defines the data fields and their types; the engine validates rules against it
- **Action schema** — defines what outcomes a rule can produce
- **Aggregate functions** — `sum`, `count`, `avg`, `median`, `max`, `min`, `subtract` over nested lists
- **Filtered array paths** — `transactions[label == "risk"].amount` selects and projects in one step
- **Arithmetic** — combine aggregates with `+`, `-`, `*`, `/`
- **Manifest** — a single YAML file that ties schema, actions, and rule files together
- **CLI tools** — validate and evaluate rules from the command line
- **Visual editor** — browser-based UI for editing, validating, and visualising rules
- **Tracing** — optional decision-tree export for every evaluation

---

## Documentation

| Document | Audience | What you will learn |
|---|---|---|
| [Introduction](docs/introduction.md) | Everyone | What the engine is, core concepts, how rules work |
| [Field Schema](docs/field-schema.md) | Rule authors | How to define the data fields rules operate on |
| [Action Schema](docs/actions.md) | Rule authors | How to define the actions rules can produce |
| [Rules](docs/rules.md) | Rule authors | How to write conditions, combine them, and define outcomes |
| [Manifest](docs/manifest.md) | Rule authors & developers | How to organise rule files into a project |
| [Value Expressions](docs/expressions.md) | Rule authors | Aggregate functions, arithmetic, filtered array paths |
| [Integration Guide](docs/integration-guide.md) | Developers | Embedding the engine as a library, API reference |
| [Full reference index](docs/README.md) | Everyone | Overview of all documentation |

---

## AI Rule Generation

The file [RULE-SPEC.md](RULE-SPEC.md) is a machine-readable specification you can hand to an AI assistant to generate or convert rules automatically.

---

## Quick Start (CLI)

Validate rules:

```bash
./gradlew run -PmainClass=ruleengine.cli.ValidatorCli \
  --args="--schema src/test/resources/sample-schema.yaml --rules src/test/resources/rules"
```

Evaluate an input file and print a trace:

```bash
./gradlew run -PmainClass=ruleengine.cli.EvaluateCli \
  --args="--schema src/test/resources/sample-schema.yaml --rules src/test/resources/rules \
          --input-file src/test/resources/sample-input.json --trace --format pretty-json"
```

---

## Visual Editor

See [README_UI.md](README_UI.md) for the desktop app rule editor.
