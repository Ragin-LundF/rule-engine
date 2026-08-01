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

That second example reads a **nested list** of transactions. Data does not have to be flat — declare a
list of records as a `collection` and its members become available to rules at any depth:

```yaml
fields:
  orders:
    type: collection
    fields:
      status:
        type: text
      total:
        type: decimal
      items:
        type: collection
        fields:
          price:
            type: decimal
```

```
rule "large-paid-order" {
  when
    sum(orders[status == "paid"].items.price) > 1000

  then
    flag "large-paid-order"
}
```

Rules are stored as plain `.rule` files, validated against a field schema, and evaluated at runtime without redeployment.

---

## Key Features

- **Human-readable DSL** — rule authors do not need to write code
- **Field schema** — defines the data fields and their types; the engine validates rules against it
- **Nested data** — `collection` and `object` field types describe lists of records and nested records to any depth
- **Action schema** — defines what outcomes a rule can produce
- **Aggregate functions** — `sum`, `count`, `avg`, `median`, `max`, `min`, `subtract` over nested lists
- **Filtered array paths** — `orders[status == "paid"].items[price > 0].price` filters at every level
- **Arithmetic** — combine aggregates with `+`, `-`, `*`, `/`
- **Text, numbers, flags and dates** — normalized text matching, numeric ranges, `true`/`false` flags, and date / date-time comparisons with an optional per-field date format
- **Manifest** — a single YAML file that ties schema, actions, and rule files together
- **CLI tools** — validate and evaluate rules from the command line
- **Visual editor** — desktop app for editing, validating, and visualising rules, including aggregates and calculations
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
| [Performance](docs/performance.md) | Developers | Benchmark figures, where evaluation time goes, tuning, thread safety |
| [Full reference index](docs/README.md) | Everyone | Overview of all documentation |

---

## AI Rule Generation

The file [RULE-SPEC.md](RULE-SPEC.md) is a machine-readable specification you can hand to an AI assistant to generate or convert rules automatically.

Every rule example in it is executed by an automated test, so the spec cannot drift away from what the engine actually accepts.

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

A Compose Desktop app for authoring rules without touching the DSL text: a schema editor with nested
fields, and a rule builder whose condition rows cover plain comparisons as well as aggregates,
calculations, filters, `not` and `ignoreCase`. Rules stay plain `.rule` files — the builder reads and
writes the same text you would write by hand.

See [README_UI.md](README_UI.md) to run it.
