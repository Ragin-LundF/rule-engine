# Manifest

The **Manifest** is the entry point for a rule project.
It is a single YAML file that ties everything together: it names the project, and lists one or more **entries**, each of which combines a field schema, an action schema, and a set of rule files.

---

## Why a Manifest?

When working with more than a handful of rule files, it quickly becomes inconvenient to specify every path individually.
The manifest provides a single place to describe a complete rule configuration — making it easy to load, validate, and run everything with one reference.

It also supports **multiple entries**, which lets you manage distinct rule sets (for example, transaction classification rules and fraud detection rules) inside a single project.

---

## File Format

```yaml
name: <project-name>

entries:
  - id: <entry-id>
    schema: <path-to-field-schema.yaml>
    actions: <path-to-action-schema.yaml>
    rules:
      - <path-to-rule-file.rule>
      - <path-to-another-rule-file.rule>
```

All paths are **relative to the manifest file itself**.
This means you can move the entire project folder without changing any paths.

---

## Fields

### Top-level

| Field | Required | Description |
|---|---|---|
| `name` | optional | A human-readable name for the project |
| `entries` | ✅ | List of rule set entries |

### Per Entry

| Field | Required | Description |
|---|---|---|
| `id` | ✅ | Unique identifier for this entry |
| `schema` | optional | Relative path to the field schema YAML file |
| `actions` | optional | Relative path to the action schema YAML file |
| `rules` | ✅ | List of relative paths to `.rule` files. **The list order defines execution order** — rules are evaluated file by file in this order, then in declaration order within each file, and matches are returned in that order. |

---

## Simple Example

```yaml
name: transaction-rules

entries:
  - id: transactions
    schema: schemas/transaction-schema.yaml
    actions: schemas/actions.yaml
    rules:
      - rules/classification.rule
      - rules/fraud-detection.rule
```

With this manifest, the engine loads:
- the field schema from `schemas/transaction-schema.yaml`
- the action schema from `schemas/actions.yaml`
- all rules from `rules/classification.rule` and `rules/fraud-detection.rule`

---

## Multiple Entries

A manifest can contain multiple entries.
Each entry is completely independent — it has its own schema, action schema, and rules.
This is useful when different parts of a system operate on different data models or have separate rule sets.

```yaml
name: banking-rules

entries:
  - id: transaction-classification
    schema: schemas/transaction-schema.yaml
    actions: schemas/transaction-actions.yaml
    rules:
      - rules/transactions/classification.rule
      - rules/transactions/chargebacks.rule

  - id: customer-risk
    schema: schemas/customer-schema.yaml
    actions: schemas/customer-actions.yaml
    rules:
      - rules/customers/risk-scoring.rule
      - rules/customers/vip-detection.rule
```

Each entry is loaded and evaluated independently.

---

## Rule Order and Variables

For plain rules the order of the `rules:` list is only visible in the results: every rule is checked
against every record, and the order decides in which order the matches come back.

That changes as soon as a rule publishes a [variable](rules.md#variables--the-set-clause). A `set`
clause makes its value available to the rules **after** it in this list, so the order becomes part of
the meaning:

```yaml
entries:
  - id: orders
    schema: schema.yaml
    actions: actions.yaml
    rules:
      # First: publishes $orderTotal, which the rules below read.
      - rules/totals.rule
      - rules/pricing.rule
      - rules/routing.rule
```

Moving `totals.rule` down would make every `$orderTotal` read a forward reference, and the build would
fail with `reads unknown variable '$orderTotal'`.

Variables are scoped to a single entry and a single evaluation. Two entries never see each other's
variables, and nothing carries over from one input record to the next.

The `shortCircuitByOutput` option evaluates rules by output group rather than in declaration order, so
it cannot be combined with variables; the build fails with an explicit message if you try.

---

## Recommended Project Layout

Here is a clean folder structure for a rule project using a manifest:

```
my-rules/
├── manifest.yaml
├── schemas/
│   ├── transaction-schema.yaml
│   └── actions.yaml
└── rules/
    ├── classification.rule
    ├── fraud-detection.rule
    └── chargebacks.rule
```

The `manifest.yaml` would then look like:

```yaml
name: my-rules

entries:
  - id: transactions
    schema: schemas/transaction-schema.yaml
    actions: schemas/actions.yaml
    rules:
      - rules/classification.rule
      - rules/fraud-detection.rule
      - rules/chargebacks.rule
```

---

## Real-World Example

The following is the manifest used in the full example project:

```yaml
name: full-sample

entries:
  - id: full
    schema: full-schema.yaml
    actions: actions.yaml
    rules:
      - rules/rent.rule
      - rules/vip.rule
      - rules/fraud.rule
```

And for the KLS legal-affairs use case:

```yaml
name: kls

entries:
  - id: legal_affairs
    schema: fields_transaction.yaml
    actions: actions_transaction.yaml
    rules:
      - label_de_legal_affairs_1.rule
```

---

## Validation

When the engine loads a manifest, it performs the following checks:
- All referenced files exist.
- The field schema is valid YAML and contains correctly typed fields.
- The action schema (if provided) is valid.
- All rule files parse without syntax errors.
- All rules are valid against the field schema and action schema.
- All rule IDs across all loaded files are unique within an entry.
- Every `$variable` a rule reads is assigned by a `set` clause in an earlier rule of the same entry.

If any check fails, the engine reports detailed errors and does not start.

---

## Tips and Best Practices

- **Use one manifest per project or application** — keep it at the root of your rules folder.
- **Group related rules in the same entry** — rules within an entry are evaluated together against the same schema.
- **Version your schemas** (e.g. `transaction-schema-v2.yaml`) — when you change a schema, you can update the manifest reference without breaking existing files.
- **Keep rule files focused** — one file per topic (classification, fraud, chargebacks) is easier to review and maintain than one large file.
- **Use meaningful entry IDs** — the entry ID appears in logs and results; `transactions` is more informative than `entry1`.

