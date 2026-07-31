# Rule Engine — Introduction

Welcome to the Rule Engine documentation.
This guide is written for **data scientists, business analysts, and project managers** who need to define, maintain, and understand business rules — without writing code.

---

## What Is the Rule Engine?

The Rule Engine lets you describe **business logic as plain-text rules**.
You write conditions ("when this is true") and outcomes ("then do this"), and the engine evaluates every rule against incoming data and tells you which ones matched and what they say should happen.

Think of it like a checklist that runs automatically:

> _"If a bank transaction mentions 'rent' and the amount is at least 500, label it as 'rent'."_

Instead of hard-coding this logic in software, you write a small rule file, and the engine takes care of the rest.

---

## Core Concepts

The rule engine is built around four simple building blocks:

| Concept | What it is | File type |
|---|---|---|
| **Field Schema** | Defines the data fields available for rules, including nested lists and records | YAML (`.yaml`) |
| **Action Schema** | Defines what actions a rule can trigger | YAML (`.yaml`) |
| **Rules** | The actual business logic — conditions and actions | `.rule` |
| **Manifest** | A project file that ties everything together | YAML (`.yaml`) |

Each concept is explained in its own section.
You can also jump directly to:

- [Field Schema](./field-schema.md) — defining your data
- [Action Schema](./actions.md) — defining what rules can do
- [Rules](./rules.md) — writing conditions and outcomes
- [Value Expressions](./expressions.md) — totals, averages and counts over nested lists
- [Manifest](./manifest.md) — organising your rule project

---

## How It Works — Big Picture

```
Field Schema (YAML)   ──►  which fields exist and their types
Action Schema (YAML)  ──►  which actions rules can use
Rules (.rule files)   ──►  when X → then Y

         ↓  at startup
   [ Engine loads & validates everything ]
         ↓  at runtime
   [ Input data arrives (e.g. a transaction) ]
         ↓
   [ Engine evaluates all rules ]
         ↓
   [ Returns: which rules matched, what actions they produced ]
```

All validation happens when the engine loads.
At evaluation time, the engine only executes pre-compiled expressions — making it very fast.

---

## A Minimal Working Example

### 1 — Field Schema (`schema.yaml`)

Describes the fields in the data you will process:

```yaml
schema: transaction-v1

fields:
  purpose:
    type: text
    normalizers:
      - trim
      - lowercase
    operators:
      - equals
      - contains

  amount:
    type: decimal
    operators:
      - equals
      - gt
      - gte
      - lt
      - lte
```

### 2 — Action Schema (`actions.yaml`)

Describes the actions your rules can produce:

```yaml
actions:
  label:
    argTypes: [string]
  score:
    argTypes: [integer]
```

### 3 — Rule (`rules/rent.rule`)

```
rule "rent-payment" {
  when
    purpose contains "rent"
    and amount >= 500

  then
    label "rent"
    score 10
}
```

### 4 — Manifest (`manifest.yaml`)

```yaml
name: my-project

entries:
  - id: transactions
    schema: schema.yaml
    actions: actions.yaml
    rules:
      - rules/rent.rule
```

### 5 — Result

Given input data `{ "purpose": "Rent apartment January", "amount": 750 }`, the engine returns:

```json
{
  "matches": [
    {
      "ruleId": "rent-payment",
      "actions": [
        { "name": "label", "arguments": ["rent"] },
        { "name": "score", "arguments": [10] }
      ]
    }
  ]
}
```

---

## What Happens When a Rule Matches?

The engine never acts directly on data.
It returns a **list of matches**, each containing:
- the **rule ID** that matched
- the **actions** the rule declared (e.g. `label "rent"`)

Your application (or integration) then decides what to do with those actions — for example, store a category, send an alert, or display a badge.

---

## Key Design Principles

- **Rules are data, not code.** Non-developers can write, read, and maintain them.
- **Validation before execution.** The engine catches mistakes (wrong field names, incompatible operators, wrong literal types, etc.) before any rule runs. Paths into nested data are checked as far as the schema declares them, so declaring the members of a collection is what buys you that safety.
- **No surprises at runtime.** If a rule file loads successfully, it will behave exactly as written.
- **Fast evaluation.** Rules are compiled into optimised internal structures; no re-parsing at runtime.

---

## Next Steps

| I want to… | Go to |
|---|---|
| Define what data my rules work on | [Field Schema](./field-schema.md) |
| Define what my rules can output | [Action Schema](./actions.md) |
| Write my first rules | [Rules](./rules.md) |
| Organise multiple rule files | [Manifest](./manifest.md) |
| Integrate the engine into an application | [Integration Guide](./integration-guide.md) |

