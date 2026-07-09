# Rule Engine — AI Specification

> **Purpose of this document**
> This specification is intended to be used as a background instruction for an AI assistant.
> Business analysts and domain experts describe their data structures and business rules (or paste rules from another system), and the AI translates those descriptions into the correct technical artifacts:
> - Field Schema YAML files
> - Action Schema YAML files
> - Rule files (`.rule` DSL)
> - Manifest YAML file(s)
>
> **IMPORTANT — No Hallucination Rule:** Every field type, operator, normalizer, action argument type, file key, and DSL keyword used in generated output **must** come exclusively from the lists in this specification. Do **not** invent types, operators, keywords, or file keys that are not listed here.

---

## Table of Contents

1. [System Overview](#1-system-overview)
2. [Four Artifacts You Must Produce](#2-four-artifacts-you-must-produce)
3. [Field Schema — Complete Reference](#3-field-schema--complete-reference)
4. [Action Schema — Complete Reference](#4-action-schema--complete-reference)
5. [Rule Files — Complete DSL Reference](#5-rule-files--complete-dsl-reference)
6. [Manifest — Complete Reference](#6-manifest--complete-reference)
7. [Translating Business Language to Rules](#7-translating-business-language-to-rules)
8. [Complete End-to-End Example](#8-complete-end-to-end-example)
9. [Validation Constraints — What the Engine Rejects](#9-validation-constraints--what-the-engine-rejects)
10. [Quick-Reference Checklists](#10-quick-reference-checklists)

---

## 1. System Overview

The Rule Engine evaluates business rules against structured input data. It does **not** execute actions itself — it returns a list of matched rules and the actions they declared. The consuming application decides what to do with those actions.

### Processing Pipeline

```
Field Schema (YAML)  ──► which data fields exist, their types and allowed operators
Action Schema (YAML) ──► which named outputs a rule can produce
Rule files (.rule)   ──► when <conditions> → then <actions>
Manifest (YAML)      ──► ties field schema + action schema + rule files together

          ↓ at startup
  [ Engine loads, parses, validates, and compiles everything ]
          ↓ at runtime (per record)
  [ Input data arrives → engine evaluates all rules → returns matched rules + actions ]
```

### Key Principles

- The engine **validates everything at load time**. Typos, unknown fields, wrong operators, and wrong argument types are all caught before any rule runs.
- Rules are **independent** — the engine checks every rule against the input and returns all that match; there is no priority or stop-first logic. Evaluation order is nonetheless deterministic: rules run in declaration order within a file, and across files in manifest `rules:` order, with matches returned in that same order.
- The engine **never modifies** input data. It only reads it and returns results.

---

## 2. Four Artifacts You Must Produce

When translating a business description into a rule engine configuration, always produce exactly these four artifact types:

| Artifact | File extension | Purpose |
|---|---|---|
| Field Schema | `.yaml` | Declares every data field: its type, text normalizers, and allowed operators |
| Action Schema | `.yaml` | Declares every named output a rule may produce and the type of its argument |
| Rule file(s) | `.rule` | Contains one or more rules: conditions + actions |
| Manifest | `.yaml` | Entry point that references the field schema, action schema, and all rule files |

### Recommended folder layout

```
<project-name>/
├── manifest.yaml
├── schemas/
│   ├── <domain>-schema.yaml
│   └── actions.yaml
└── rules/
    ├── <topic-1>.rule
    └── <topic-2>.rule
```

---

## 3. Field Schema — Complete Reference

### File structure

```yaml
schema: <schema-name>          # optional but recommended; use versioned names, e.g. "transaction-v1"

fields:
  <fieldName>:                 # camelCase identifier, must be unique within the schema
    type: <type>               # REQUIRED — exactly one value from the type table below
    normalizers:               # OPTIONAL — only valid on text and string_set fields
      - <normalizer>           # zero or more values from the normalizer table below, applied in order
    operators:                 # OPTIONAL — if omitted, all operators valid for the type are allowed
      - <operator>             # one or more values from the operator tables below
```

### 3.1 Field Types — Exhaustive List

Use **exactly** one of these values for the `type` key. No other values are valid.

| Canonical name | Accepted aliases | Use for |
|---|---|---|
| `text` | `string` | Free text: descriptions, names, codes, IBANs, etc. |
| `integer` | `int`, `long` | Whole numbers: counts, years, scores |
| `decimal` | `number`, `bigdecimal` | Numbers with decimal places: amounts, prices, percentages |
| `boolean` | `bool` | True/false flags |
| `string_set` | `stringset`, `set` | Multiple string values per field: tags, labels, categories |
| `date` | — | Dates (reserved for future use; limited operator support) |

> **Rule:** Always use the canonical name in generated output. Aliases are only accepted as input when a user writes them; always write the canonical form in output files.

### 3.2 Normalizers — Exhaustive List

Normalizers apply **only** to `text` and `string_set` fields. They are applied in the listed order before any rule comparison. They are **not valid** on `integer`, `decimal`, `boolean`, or `date` fields.

| Name | What it does | Typical use |
|---|---|---|
| `trim` | Removes leading and trailing whitespace | Almost every text field |
| `lowercase` | Converts all letters to lowercase | Natural language fields |
| `uppercase` | Converts all letters to uppercase | Code fields (SEPA codes, country codes) |
| `collapse_whitespace` | Replaces runs of whitespace with a single space | Free-text descriptions |
| `remove_punctuation` | Removes all punctuation characters | Free-text descriptions |
| `german_umlaut_fold` | Replaces German umlauts with ASCII equivalents (ä→ae, ö→oe, ü→ue, ß→ss) | German text fields |

> **Rule:** Do not invent normalizer names. Only the six names above are valid.

#### Common normalizer combinations

| Scenario | Normalizers to use |
|---|---|
| General free-text field | `trim`, `lowercase` |
| German free-text field | `trim`, `lowercase`, `german_umlaut_fold` |
| Code / identifier field (uppercase) | `trim`, `uppercase` |
| Noisy text with extra spaces | `trim`, `lowercase`, `collapse_whitespace` |
| Text with punctuation noise | `trim`, `lowercase`, `remove_punctuation` |

### 3.3 Operators — Exhaustive List by Type

The `operators` list restricts which comparison operations rule authors may use for that field. If you omit `operators`, all operators valid for the type are allowed.

#### Text field operators (`text`)

| Operator | Meaning | Example in a rule |
|---|---|---|
| `equals` | Exact match after normalisation | `country equals "de"` |
| `contains` | Field value contains the given substring | `purpose contains "rent"` |
| `startsWith` | Field value begins with the given text | `iban startsWith "DE"` |
| `endsWith` | Field value ends with the given text | `purpose endsWith "GmbH"` |
| `in` | Field value matches one entry in a list | `sepaCode in ["CCRD", "DCRD"]` |
| `regex` | Field value matches a regular expression | `iban regex "^DE[0-9]{20}$"` |

#### Integer field operators (`integer`)

| Operator | Symbolic alias | Meaning | Example in a rule |
|---|---|---|---|
| `equals` | `==` or `=` | Exact equality | `count equals 3` |
| `gt` | `>` | Greater than | `count gt 10` |
| `gte` | `>=` | Greater than or equal | `count >= 5` |
| `lt` | `<` | Less than | `count lt 0` |
| `lte` | `<=` | Less than or equal | `count <= 100` |
| `between` | — | Inclusive range | `count between 5 20` |

#### Decimal field operators (`decimal`)

| Operator | Symbolic alias | Meaning | Example in a rule |
|---|---|---|---|
| `equals` | `==` or `=` | Exact equality | `amount equals 0` |
| `gt` | `>` | Greater than | `amount gt 1000` |
| `gte` | `>=` | Greater than or equal | `amount >= 500` |
| `lt` | `<` | Less than | `amount lt 0` |
| `lte` | `<=` | Less than or equal | `amount <= 9999` |
| `between` | — | Inclusive range | `amount between 100 5000` |

#### String set field operators (`string_set`)

| Operator | Meaning | Example in a rule |
|---|---|---|
| `containsAny` | At least one listed value is in the set | `tags containsAny ["vip", "premium"]` |
| `containsAll` | All listed values are in the set | `tags containsAll ["verified", "active"]` |

#### Boolean field operators (`boolean`)

Boolean fields are compared directly in conditions (no explicit operator list is used in the schema; the comparison is implicit in the rule). Use `equals` with `true` or `false`:
```
isActive equals true
```

> **Rule:** Do **not** use text operators (`contains`, `startsWith`, etc.) on numeric or boolean fields. Do **not** use numeric operators on text fields. Do **not** use `between` on text fields. Do **not** use `containsAny` / `containsAll` on non-`string_set` fields.

### 3.4 Field Schema Example

```yaml
schema: transaction-v1

fields:

  # Free-text payment description
  purpose:
    type: text
    normalizers:
      - trim
      - lowercase
      - german_umlaut_fold
    operators:
      - equals
      - contains
      - startsWith
      - endsWith
      - in
      - regex

  # IBAN — kept uppercase for format checks
  iban:
    type: text
    normalizers:
      - trim
      - uppercase
    operators:
      - equals
      - startsWith
      - regex

  # SEPA transaction code
  sepaCode:
    type: text
    normalizers:
      - trim
      - uppercase
    operators:
      - equals
      - in

  # Signed transaction amount (negative = outgoing)
  amount:
    type: decimal
    operators:
      - equals
      - gt
      - gte
      - lt
      - lte
      - between

  # Number of transactions in a time window
  count:
    type: integer
    operators:
      - equals
      - gt
      - gte
      - lt
      - lte
      - between

  # Customer tags / labels
  tags:
    type: string_set
    normalizers:
      - trim
      - lowercase
    operators:
      - containsAny
      - containsAll
```

---

## 4. Action Schema — Complete Reference

### File structure

```yaml
actions:
  <actionName>:             # lowercase hyphenated or camelCase identifier, must be unique
    argTypes: [<argType>]   # REQUIRED — a list with exactly ONE element from the argType table
```

> **Important:** Each action accepts **exactly one argument**. The `argTypes` list always contains exactly one entry.

### 4.1 Argument Types — Exhaustive List

| Type | Accepted values in rules | Example |
|---|---|---|
| `string` | Any text in double quotes | `label "rent"` |
| `integer` | A whole number (no quotes) | `score 10` |
| `decimal` | A number with decimal places (no quotes) | `threshold 0.75` |

> **Rule:** Only `string`, `integer`, and `decimal` are valid argument types. No other types exist for actions.

### 4.2 Action Schema Example

```yaml
actions:
  label:
    argTypes: [string]
  category:
    argTypes: [string]
  flag:
    argTypes: [string]
  score:
    argTypes: [integer]
  alert:
    argTypes: [string]
  reject:
    argTypes: [string]
  notify:
    argTypes: [string]
```

### 4.3 Commonly Used Action Names (by convention)

You may define any action names that fit the domain. These are widely used conventions:

| Action | Arg type | Purpose |
|---|---|---|
| `label` | `string` | Assign a classification label (e.g. `"rent"`, `"salary"`) |
| `category` | `string` | Assign a broader category (e.g. `"housing"`, `"income"`) |
| `flag` | `string` | Mark for review or attention (e.g. `"review"`, `"compliance"`) |
| `score` | `integer` | Contribute a numeric score (e.g. risk score) |
| `alert` | `string` | Trigger an alert with a named reason |
| `reject` | `string` | Signal that the item should be rejected, with a reason |
| `notify` | `string` | Trigger a notification |

---

## 5. Rule Files — Complete DSL Reference

### 5.1 File basics

- Extension: **`.rule`**
- A single `.rule` file may contain **one or more rules**.
- Lines starting with `#` are **comments** and are ignored.
- Rules are evaluated **independently** — all matching rules fire; there is no stop-first or priority mechanism. Order is still deterministic: rules are evaluated in declaration order within a file (and across files in manifest `rules:` order), and matches are returned in that order.

### 5.2 Rule structure

```
rule "<rule-id>" {
  when
    <condition>

  then
    <action>
    <action>
    ...
}
```

| Part | Required | Notes |
|---|---|---|
| `rule "<id>"` | ✅ | ID must be unique across all loaded rule files. Use lowercase-hyphenated or UPPER_UNDERSCORE identifiers. |
| `when` | ✅ | Keyword, followed by one or more conditions. |
| `then` | ✅ | Keyword, followed by one or more actions. |

### 5.3 Conditions

A condition compares a field from the schema to a literal value using an operator:

```
<fieldName> <operator> <value>
```

The field name must exist in the field schema. The operator must be in the field's allowed operators list.

#### Text condition examples

```
purpose contains "rent"
iban startsWith "DE"
sepaCode equals "SALA"
sepaCode in ["CCRD", "DCRD", "PMNT"]
iban regex "^DE[0-9]{20}$"
counterparty endsWith "GmbH"
```

#### Numeric condition examples

```
amount >= 500
amount between 100 5000
count gt 10
amount equals 0
amount < -10000
```

> For `between`, write both bounds separated by a space: `amount between 100 5000` means `100 ≤ amount ≤ 5000` (both inclusive).

#### String set condition examples

```
tags containsAny ["vip", "premium"]
tags containsAll ["verified", "active"]
```

#### The `ignoreCase` modifier

For text operators (`equals`, `contains`, `startsWith`, `endsWith`, `regex`), append `ignoreCase` after the value to make the comparison case-insensitive:

```
counterparty equals "Netflix" ignoreCase
```

This is useful when a field does **not** have a `lowercase` or `uppercase` normalizer but you still need case-insensitive matching.

### 5.4 Combining conditions

#### AND — all conditions must be true

Use the `and` keyword between conditions, or simply place conditions on consecutive lines (implicit AND):

```
rule "high-risk" {
  when
    country equals "ng"
    and amount >= 10000

  then
    flag "review"
}
```

#### OR — at least one condition must be true

```
rule "vip-customer" {
  when
    tags containsAny ["vip"]
    or tags containsAny ["premium"]

  then
    label "vip"
}
```

#### NOT — negates a single condition or group

```
rule "non-dach-iban" {
  when
    not iban regex "^(DE|AT|CH)"

  then
    flag "foreign-iban"
}
```

#### Operator precedence (without parentheses)

From highest to lowest:
1. `not`
2. `and`
3. `or`

So `A or B and C` is interpreted as `A or (B and C)`.

#### Grouping with parentheses

Use parentheses to make complex logic unambiguous:

```
rule "chargeback" {
  when
    (purpose contains "chargeback"
    or purpose contains "cancellation"
    or purpose contains "reversal")
    and amount < 0

  then
    label "chargeback"
    flag "review"
}
```

### 5.5 Actions in rules

Actions appear in the `then` block. Each line is one action:

```
then
  label "rent"
  category "housing"
  score 10
```

- **String arguments** are always in double quotes.
- **Numeric arguments** are plain numbers — no quotes.
- A rule may have **any number of actions**.
- All declared actions are returned when the rule matches.

### 5.6 Rule ID conventions

- Must be **unique across all rule files** in the same manifest entry.
- Recommended formats:
  - Lowercase hyphenated: `rent-payment`, `fraud-keyword-purpose`
  - Uppercase underscore for formal codes: `LEGAL_1`, `AML_HIGH_RISK`
- Be descriptive — the ID appears in evaluation results and logs.

### 5.7 Complete rule file example

```
# transaction-classification.rule
# Classifies bank transactions by purpose and amount

rule "direct-debit" {
  when
    sepaCode in ["DMCT", "DRNL", "PRCT"]
  then
    label "direct-debit"
    category "banking"
}

rule "salary-credit" {
  when
    sepaCode equals "SALA"
    and amount > 0
  then
    label "salary"
    category "income"
}

rule "rent-payment" {
  when
    (purpose contains "miete"
    or purpose contains "rent"
    or purpose contains "pacht")
    and amount >= 300
  then
    label "rent"
    category "housing"
}

rule "premium-customer-transfer" {
  when
    tags containsAll ["premium", "verified"]
    and amount > 0
  then
    label "premium-credit"
    score 100
}
```

### 5.8 Value expressions — aggregate functions and arithmetic

Value expressions allow conditions to aggregate data from **nested lists of objects**.
Use them when a rule must reason about a collection (e.g. all transactions on an account) rather than a single field.

#### Syntax

```
aggregateFunction(fieldPath) comparisonOperator valueExpression
```

Both sides of the comparison can be aggregate function calls, arithmetic expressions, or numeric literals.

#### Comparison operators for value expressions

| Operator | Meaning |
|---|---|
| `==` | Equal |
| `!=` | Not equal |
| `>` | Greater than |
| `>=` | Greater than or equal |
| `<` | Less than |
| `<=` | Less than or equal |

> **Important:** Use symbolic operators (`==`, `>`, etc.) for value expression comparisons.
> The legacy named operators (`equals`, `gt`, etc.) are only valid for plain field comparisons.

#### Aggregate functions

All functions take exactly **one argument** — a field path that resolves to a collection.

| Function | Description |
|---|---|
| `count(path)` | Number of elements |
| `sum(path)` | Sum of numeric values |
| `subtract(path)` | First element minus all subsequent elements |
| `avg(path)` | Arithmetic mean |
| `median(path)` | Median value |
| `max(path)` | Maximum value |
| `min(path)` | Minimum value |

#### Field paths

A dot-separated path projects a field from each element of a list:

```
transactions.amount   →  [100.00, 90.00, ...]
```

A filter in `[...]` selects only matching elements before aggregation:

```
transactions[label == "risk"].amount
transactions[amount > 0]
```

#### Examples

```
count(transactions) > 2
sum(transactions.amount) > 1000
avg(transactions.amount) >= 25
max(transactions[label == "risk"].amount) > 500
sum(transactions[label == "risk"].amount) > sum(transactions[amount > 0].amount) * 0.03
```

#### Empty array behavior

- `count`, `sum`, `subtract` return `0` for an empty array — comparisons work normally.
- `avg`, `median`, `max`, `min` return a missing value for an empty array — the comparison always evaluates to `false`.

#### Arithmetic operators

Arithmetic can be applied to any value expression: `+`, `-`, `*`, `/`.
Standard precedence applies (`*`/`/` before `+`/`-`); use parentheses to override.

```
sum(transactions.amount) * 0.03
(sum(a.amount) + sum(b.amount)) / count(transactions)
```

#### Validation rules (what the engine rejects)

- Unknown function name → error.
- Wrong argument count (not exactly 1) → error.
- Argument that cannot resolve to a collection → error.
- Numeric aggregate compared with a text literal → error.
- `contains`, `startsWith`, etc. used with a value expression → error.

> For the full reference including all edge cases see [docs/expressions.md](docs/expressions.md).

---

## 6. Manifest — Complete Reference

### File structure

```yaml
name: <project-name>     # optional; human-readable name for the project

entries:
  - id: <entry-id>       # REQUIRED; unique identifier for this entry; appears in logs and results
    schema: <path>       # optional; relative path to the field schema YAML file
    actions: <path>      # optional; relative path to the action schema YAML file
    rules:               # REQUIRED; list of relative paths to .rule files
      - <path-to-rule-file.rule>
      - <path-to-another-rule-file.rule>
```

> All paths are **relative to the manifest file itself**. This means the entire project folder can be moved without changing any paths.

### 6.1 Top-level manifest fields

| Field | Required | Description |
|---|---|---|
| `name` | optional | Human-readable project name |
| `entries` | ✅ | List of one or more rule set entries |

### 6.2 Entry fields

| Field | Required | Description |
|---|---|---|
| `id` | ✅ | Unique identifier for this entry (lowercase-hyphenated recommended) |
| `schema` | optional | Relative path to the field schema YAML file |
| `actions` | optional | Relative path to the action schema YAML file |
| `rules` | ✅ | List of relative paths to `.rule` files |

> **No other keys are valid** at the entry level. Do not add keys like `version`, `description`, `priority`, or `enabled`.

### 6.3 Single-entry manifest example

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

### 6.4 Multi-entry manifest example

Use multiple entries when different parts of the system operate on different data models or rule sets. Each entry is completely independent.

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

---

## 7. Translating Business Language to Rules

This section describes how to map common business descriptions to the technical artifacts.

### 7.1 Mapping data fields to the Field Schema

| Business description | Field type to use |
|---|---|
| A text description, name, code, reference | `text` |
| A numeric amount, price, percentage | `decimal` |
| A count, year, quantity, integer score | `integer` |
| A yes/no flag, enabled/disabled state | `boolean` |
| A list of tags, labels, or categories | `string_set` |
| A date or date range | `date` |

**Ask yourself for each field:**
- Is it free text or a structured code? → `text`
- Does it have decimal places? → `decimal`, otherwise `integer`
- Can it hold multiple values at once? → `string_set`
- Is it only ever true or false? → `boolean`

### 7.2 Mapping business rule statements to DSL conditions

| Business statement | DSL condition |
|---|---|
| "purpose exactly matches 'SALA'" | `purpose equals "SALA"` |
| "purpose contains the word 'rent'" | `purpose contains "rent"` |
| "IBAN starts with DE, AT, or CH" | `iban regex "^(DE\|AT\|CH)"` |
| "SEPA code is one of CCRD, DCRD, or PMNT" | `sepaCode in ["CCRD", "DCRD", "PMNT"]` |
| "amount is at least 500" | `amount >= 500` |
| "amount is between 100 and 5000 (inclusive)" | `amount between 100 5000` |
| "amount is negative (outgoing payment)" | `amount < 0` |
| "count is more than 10" | `count gt 10` |
| "customer has at least one of: vip, premium" | `tags containsAny ["vip", "premium"]` |
| "customer has all of: verified AND active" | `tags containsAll ["verified", "active"]` |
| "IBAN does NOT start with DE" | `not iban startsWith "DE"` |
| "purpose contains 'rent' case-insensitively" | `purpose contains "rent" ignoreCase` |
| "both condition A and condition B" | `conditionA` + newline + `and conditionB` |
| "either condition A or condition B" | `conditionA` + newline + `or conditionB` |

### 7.3 Mapping outcomes to actions

| Business outcome | Suggested action |
|---|---|
| "classify as X" / "label it as X" | `label "X"` (arg type: `string`) |
| "put it in category X" | `category "X"` (arg type: `string`) |
| "mark for review" / "flag it" | `flag "review"` (arg type: `string`) |
| "assign a risk score of N" | `score N` (arg type: `integer`) |
| "send an alert" | `alert "alert-name"` (arg type: `string`) |
| "reject / block" | `reject "reason"` (arg type: `string`) |
| "send notification" | `notify "notification-name"` (arg type: `string`) |

> Define every action the business needs in the Action Schema before referencing it in a rule.

### 7.4 Handling German / international text fields

When data originates from German-language systems, always add these normalizers to free-text fields:

```yaml
normalizers:
  - trim
  - lowercase
  - german_umlaut_fold
```

This makes rule comparisons work correctly for inputs like "Miete" (which becomes "miete") and "Müller" (which becomes "mueller").

### 7.5 Structuring multiple rule topics

Group rules by business topic, one file per topic. Examples:

| Business domain | Suggested file name |
|---|---|
| Transaction classification | `classification.rule` |
| Fraud / AML detection | `fraud-detection.rule` |
| Chargeback detection | `chargebacks.rule` |
| Customer risk scoring | `customer-risk.rule` |
| VIP customer detection | `vip-detection.rule` |

---

## 8. Complete End-to-End Example

### Business description (input from a business analyst)

> We process bank transactions. Each transaction has:
> - `purpose`: a free-text payment description (German or English)
> - `iban`: the counterparty IBAN
> - `sepaCode`: a SEPA transaction code (always uppercase, e.g. "SALA", "DMCT")
> - `amount`: the signed transaction amount (negative = outgoing)
> - `count`: number of similar transactions in the last 30 days
> - `tags`: a list of customer tags
>
> We want to:
> 1. Label transactions as "rent" if the purpose contains "miete", "rent", or "pacht", and amount is at least 300.
> 2. Label transactions as "salary" if sepaCode is "SALA" and amount is positive.
> 3. Flag transactions as "foreign-iban" if the IBAN does not start with DE, AT, or CH.
> 4. Flag and alert on transactions that look like structuring: count between 5 and 20, amount between 8000 and 9999.
> 5. Block transactions from customers with tags "blocked" or "sanctioned".

---

### Output: `schemas/transaction-schema.yaml`

```yaml
schema: transaction-v1

fields:

  purpose:
    type: text
    normalizers:
      - trim
      - lowercase
      - german_umlaut_fold
    operators:
      - equals
      - contains
      - startsWith
      - endsWith
      - in
      - regex

  iban:
    type: text
    normalizers:
      - trim
      - uppercase
    operators:
      - equals
      - startsWith
      - regex

  sepaCode:
    type: text
    normalizers:
      - trim
      - uppercase
    operators:
      - equals
      - in

  amount:
    type: decimal
    operators:
      - equals
      - gt
      - gte
      - lt
      - lte
      - between

  count:
    type: integer
    operators:
      - equals
      - gt
      - gte
      - lt
      - lte
      - between

  tags:
    type: string_set
    normalizers:
      - trim
      - lowercase
    operators:
      - containsAny
      - containsAll
```

---

### Output: `schemas/actions.yaml`

```yaml
actions:
  label:
    argTypes: [string]
  flag:
    argTypes: [string]
  alert:
    argTypes: [string]
  reject:
    argTypes: [string]
```

---

### Output: `rules/classification.rule`

```
# classification.rule — transaction classification rules

rule "rent-payment" {
  when
    (purpose contains "miete"
    or purpose contains "rent"
    or purpose contains "pacht")
    and amount >= 300

  then
    label "rent"
}

rule "salary-credit" {
  when
    sepaCode equals "SALA"
    and amount > 0

  then
    label "salary"
}
```

---

### Output: `rules/fraud-detection.rule`

```
# fraud-detection.rule — fraud and AML detection rules

rule "non-dach-iban" {
  when
    not iban regex "^(DE|AT|CH)"

  then
    flag "foreign-iban"
}

rule "structuring-suspicion" {
  when
    count between 5 20
    and amount between 8000 9999

  then
    flag "structuring"
    alert "aml-structuring-suspicion"
}

rule "blocked-customer" {
  when
    tags containsAny ["blocked", "sanctioned"]

  then
    flag "compliance"
    alert "flagged-customer-transaction"
    reject "aml-block"
}
```

---

### Output: `manifest.yaml`

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

---

## 9. Validation Constraints — What the Engine Rejects

The engine validates everything at load time and rejects the following. Never generate output that violates these constraints.

### Field schema constraints

| Constraint | Example of invalid usage |
|---|---|
| Unknown field type | `type: number_list` (not a valid type) |
| Normalizer on a non-text field | Adding `lowercase` to an `integer` field |
| Unknown normalizer name | `normalizers: [strip]` (`strip` does not exist; use `trim`) |
| Unknown operator name | `operators: [matches]` (`matches` does not exist; use `regex`) |
| Operator not valid for the field type | `operators: [contains]` on an `integer` field |

### Action schema constraints

| Constraint | Example of invalid usage |
|---|---|
| Unknown argument type | `argTypes: [bool]` (use `string`, `integer`, or `decimal`) |
| More than one argument type | `argTypes: [string, integer]` (exactly one is allowed) |
| Empty argTypes list | `argTypes: []` (must have exactly one entry) |

### Rule DSL constraints

| Constraint | Example of invalid usage |
|---|---|
| Unknown field name in condition | `purpse contains "rent"` (typo — field does not exist in schema) |
| Operator not allowed for field | `amount contains "500"` (`contains` is not valid for `decimal`) |
| Wrong literal type | `score "high"` when `score` expects `integer` |
| Duplicate rule ID | Two rules in any loaded file sharing the same ID |
| Action not defined in action schema | `notify "x"` when `notify` is not in the action schema |
| `between` on a text field | `purpose between "a" "z"` (not valid) |
| List literal on a non-`in` / non-`containsAny/All` operator | `purpose equals ["a", "b"]` |

### Manifest constraints

| Constraint | Example of invalid usage |
|---|---|
| Missing `entries` key | A manifest YAML with no `entries` list |
| Missing `id` in an entry | An entry without an `id` field |
| Missing `rules` in an entry | An entry with no `rules` list |
| Unknown key in an entry | Adding `priority: 1` or `enabled: true` to an entry |
| Non-existent file path | A `schema:` path that does not resolve to an actual file |

---

## 10. Quick-Reference Checklists

### Checklist: Field Schema

- [ ] Every data field the business analyst described has an entry under `fields:`.
- [ ] Each field has exactly one `type:` from the valid types list.
- [ ] Normalizers are only applied to `text` or `string_set` fields.
- [ ] All normalizer names come from the six built-in normalizers.
- [ ] All operator names match the valid operators for the field's type.
- [ ] The schema has a versioned `schema:` name (e.g. `my-schema-v1`).

### Checklist: Action Schema

- [ ] Every outcome the business analyst described has a named action.
- [ ] Every action has exactly one entry in `argTypes:`.
- [ ] All arg types are `string`, `integer`, or `decimal`.

### Checklist: Rule Files

- [ ] Every rule has a unique, descriptive ID.
- [ ] Every field name in every condition exists in the field schema.
- [ ] Every operator used in a condition is in the field's allowed operators list.
- [ ] `between` is only used on `integer` or `decimal` fields (two numeric bounds, no quotes).
- [ ] `in` / `containsAny` / `containsAll` use a JSON-style list: `["a", "b"]`.
- [ ] Every action in every `then` block is defined in the action schema.
- [ ] String action arguments are in double quotes; numeric arguments have no quotes.
- [ ] Parentheses are used wherever AND/OR grouping could be ambiguous.
- [ ] Related rules are grouped in thematic files.
- [ ] Each file has a comment header explaining its purpose.

### Checklist: Manifest

- [ ] The manifest has a `name:` and an `entries:` list.
- [ ] Each entry has an `id:`, a `schema:` path, an `actions:` path, and a `rules:` list.
- [ ] All paths are relative to the manifest file.
- [ ] All referenced files are listed (no file is missing from the `rules:` list).
- [ ] Entry IDs are descriptive and use lowercase-hyphenated naming.

---

*This specification is complete. Do not add field types, operators, normalizers, action argument types, or manifest keys that are not listed in this document.*

