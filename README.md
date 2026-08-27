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
- **Value functions** — `abs`, `daysBetween` for magnitudes and calendar-day arithmetic, `isAvailable` and `isEmpty`
  to ask whether a record carries a value at all
- **Missing data as an outcome** — a rule may declare a `not_exists` branch for the case where the
  record carries no data to decide its condition, instead of silently reading it as "false"
- **Collection tools** — `sortBy` ordering, `take` / `takeLast` slicing, `in` membership filters,
  `every` / `any` predicates, and `sumByKey` joins across collections
- **Per-member evaluation** — a manifest entry may declare `scope: <collection>` to run its rules
  once per member instead of once per document
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

`ruleengine-core` ships two command-line entry points. They are ordinary `main` classes on the library's
own classpath — there is no separate launcher — so run them from a project that has the dependency
([Maven Central](https://mvnrepository.com/artifact/io.github.ragin-lundf/ruleengine-core)):

```kotlin
// build.gradle.kts
dependencies { implementation("io.github.ragin-lundf:ruleengine-core:1.9.0") }

tasks.register<JavaExec>("validateRules") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "ruleengine.cli.ValidatorCli"
    args("--manifest", "rules/manifest.yaml")
}
```

Validate a whole manifest entry — schema, actions, rule files and the variables that cross them:

```bash
./gradlew validateRules
# or, with a classpath you already have:
java -cp "<runtime classpath>" ruleengine.cli.ValidatorCli --manifest rules/manifest.yaml
```

In a checkout of this repository the same two tasks are already defined:

```bash
./gradlew :ruleengine-core:validateRules --args="--manifest rules/manifest.yaml"
./gradlew :ruleengine-core:evaluateRules --args="--manifest rules/manifest.yaml --input-file record.json --trace"
```

Evaluate an input file and print a trace:

```bash
java -cp "<runtime classpath>" ruleengine.cli.EvaluateCli \
  --manifest rules/manifest.yaml --input-file record.json --trace --format pretty-json
```

Both also accept `--schema <file> --rules <dir>` instead of `--manifest`, for a rule set that has no
manifest. Exit codes: `0` valid, `1` invalid, `2` usage, `3` anything thrown.

> The runtime classpath needs `kotlin-reflect` as well as Jackson; both come transitively with the
> dependency, so a `JavaExec` task or `mvn exec:java` has them already.

---

## Visual Editor

A Compose Desktop app for authoring rules without touching the DSL text: a schema editor with nested
fields, and a rule builder whose condition rows cover plain comparisons as well as aggregates,
calculations, filters, `not` and `ignoreCase`. Rules stay plain `.rule` files — the builder reads and
writes the same text you would write by hand.

See [README_UI.md](README_UI.md) to run it.
