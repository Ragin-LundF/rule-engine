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
>
> Every rule example in this document is executed by an automated test
> (`ruleengine-core/src/test/kotlin/ruleengine/docs/SpecExampleTest.kt`), which parses, validates and
> compiles it against the schema examples shown here. If an example ever stops working, that test fails.

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
- Rules are **independent by default** — the engine checks every rule against the input and returns all that match; there is no priority or stop-first logic. Evaluation order is nonetheless deterministic: rules run in declaration order within a file, and across files in manifest `rules:` order, with matches returned in that same order.
- The one exception is a **variable**: a rule's `then` block may `set` a named value that the rules after it read as `$name` (see §5.6). Order is then part of the meaning, not just of the output.
- The engine **never modifies** input data. It only reads it and returns results. A variable lives for the duration of one evaluation and is never written back into the input.

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
| `date` | — | Calendar dates, compared with quoted literals |
| `date_time` | `datetime`, `timestamp` | Dates with a time of day, compared at time precision |
| `collection` | `list`, `array` | A **list of objects**: transactions, order items, positions |
| `object` | `map` | A **single nested record**: customer, address, counterparty |

> **Rule:** Always use the canonical name in generated output. Aliases are only accepted as input when a user writes them; always write the canonical form in output files.

> **Rule:** `collection` and `object` are **structure types**. They are never compared directly — you
> navigate into them with a dotted path (`customer.country`) or aggregate over them
> (`sum(transactions.amount)`). Writing `transactions equals "x"` is an error. See
> [3.5 Nested Data](#35-nested-data--collections-and-objects).

### 3.2 Normalizers — Exhaustive List

Normalizers apply **only** to `text` and `string_set` fields. They are applied in the listed order before any rule comparison. They are **not valid** on `integer`, `decimal`, `boolean`, `date`, `date_time`, `collection`, or `object` fields.

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

`equals` is the **only** operator for boolean fields. The value is the bare word `true` or `false` — never quoted, never `"yes"` / `1`.

| Operator | Meaning | Example in a rule |
|---|---|---|
| `equals` | Flag has this value | `isActive equals true` |

```
isActive equals true
isActive equals false
not isActive equals true      # equivalent to `isActive equals false` for a field that is present
```

> **Note:** `not isActive equals true` also matches records where the field is **absent**, because a
> missing value makes the inner condition false. Use `isActive equals false` when the flag must be
> present and false.

#### Date field operators (`date`, `date_time`)

Both types use the same six operators. There is no separate "before" or "after" operator: **`lt` is
"before" and `gt` is "after"**.

Literals are always **quoted**. By default they are ISO-8601 — `"2024-01-31"` for a `date`,
`"2024-06-15T09:30:00"` for a `date_time` — and any other spelling is rejected at load time. A field
that declares a [`format`](#the-format-key) uses that pattern instead.

A `date` comparison is by calendar date: a value carrying a time is truncated to its date. A
`date_time` comparison keeps the time, so `bookedAt gt "2024-06-15T09:00:00"` is false for a value at
exactly 09:00:00 and true one second later.

| Operator | Symbolic alias | Meaning | Example in a rule |
|---|---|---|---|
| `equals` | `==` or `=` | Same day (`date`) / same instant (`date_time`) | `bookingDate equals "2024-06-15"` |
| `gt` | `>` | After | `bookingDate gt "2024-01-01"` |
| `gte` | `>=` | On or after | `bookingDate >= "2024-01-01"` |
| `lt` | `<` | Before | `bookingDate lt "2020-01-01"` |
| `lte` | `<=` | On or before | `bookingDate <= "2024-12-31"` |
| `between` | — | Inclusive range | `bookingDate between "2024-01-01" "2024-12-31"` |

> **Rule:** Do **not** use text operators (`contains`, `startsWith`, etc.) on numeric, boolean, or date fields. Do **not** use numeric operators on text fields. Do **not** use `between` on text fields. Do **not** use `containsAny` / `containsAll` on non-`string_set` fields. Do **not** use any operator directly on a `collection` or `object` field.

#### The `format` key

A `date` or `date_time` field may declare a `format`: a
[`java.time.format.DateTimeFormatter`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/time/format/DateTimeFormatter.html)
pattern such as `dd.MM.yyyy`. It is the field's **only** date format — it governs both the incoming data
value and the literal written in every rule for that field:

```yaml
schema: invoices-v1

fields:
  dueDate:
    type: date
    format: "dd.MM.yyyy"
  paidAt:
    type: date_time
    format: "dd.MM.yyyy HH:mm"
```

```
rule "overdue-invoice" {
  when
    dueDate lt "31.01.2024"

  then
    flag "overdue"
}
```

> **Rule:** A declared `format` **replaces** ISO, it does not extend it. With the schema above,
> `dueDate equals "2024-01-31"` is an error and the input value `"2024-01-31"` does not match.
> Omit `format` whenever the data is ISO-8601 — that is the default and needs no declaration.

| Constraint | Notes |
|---|---|
| Valid on `date` and `date_time` only | A `format` on any other type is rejected when the schema loads |
| Must be a usable pattern | Rejected at load time when it is malformed (`QQQQQQ`) or cannot represent a complete value (`MM-dd` on a `date`, `yyyy-MM-dd` on a `date_time`) |
| Applies to text values | An input already typed as a date (a `LocalDate`, `LocalDateTime` or `Instant` handed to the engine by the host application) carries no text, so no pattern applies to it |
| Prefer separators | An all-digit pattern such as `yyyyMMdd` works in a hand-written rule but is not round-trip-safe in the visual Builder, which cannot tell that value from a number |

> **Note:** A `format` on a member of a **`collection`** is accepted and checked, but has no effect at
> runtime: a path that reads through a list is projected and compared as raw text. A member of an
> **`object`** is a normal typed field, so its `format` does apply (see
> [3.5 Nested Data](#35-nested-data--collections-and-objects)).

#### Named operators vs. symbolic operators — important

Both spellings exist, and they do **not** take the same path through the engine:

| Comparison | Write | Why |
|---|---|---|
| A field against a literal | **Named**: `equals`, `gt`, `gte`, `lt`, `lte`, `between`, `contains`, … | Fully validated: the field's declared `operators:` list is enforced, the literal type is checked, and text normalizers are applied to the literal as well as the field |
| A value expression (aggregate or arithmetic) | **Symbolic**: `==`, `!=`, `>`, `>=`, `<`, `<=` | Required — the engine only routes a condition through the expression engine for symbolic operators |

> **Rule:** For a plain field-vs-literal comparison, prefer the **named** operator.
> `==` and `!=` always route to the expression engine, which does **not** enforce the field's declared
> `operators:` list and does **not** normalize the literal. On a field with a `lowercase` normalizer,
> `counterparty equals "ACME"` matches the value `"acme"`, while `counterparty == "ACME"` does **not**.
> `>`, `>=`, `<`, `<=` on a plain field are equivalent to their named forms.

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

  # Yes/no flag
  isActive:
    type: boolean
    operators:
      - equals

  # Calendar date — ISO literals, because no `format` is declared
  bookingDate:
    type: date
    operators:
      - equals
      - gt
      - gte
      - lt
      - lte
      - between

  # Date with a time of day
  bookedAt:
    type: date_time
    operators:
      - equals
      - gt
      - gte
      - lt
      - lte
      - between
```

### 3.5 Nested Data — Collections and Objects

Input data is often not flat: a record may carry a list of transactions, or a nested customer object.
Declare those with `collection` (a list) or `object` (a single record) and a nested `fields:` block.

`fields:` is **recursive** — a nested member may itself be a `collection` or `object` with its own
`fields:`, so nesting depth is unlimited.

```yaml
schema: orders-v1

fields:

  orders:
    type: collection          # a list of order objects
    fields:
      status:
        type: text
      total:
        type: decimal
      customer:
        type: object          # an object inside a collection
        fields:
          country:
            type: text
      items:
        type: collection      # a collection inside a collection
        fields:
          sku:
            type: text
          price:
            type: decimal
```

Given that schema, rules can navigate and aggregate to any declared depth:

```
count(orders) > 3
sum(orders.total) > 1000
sum(orders[status == "paid"].items[price > 0].price) > 500
count(orders[customer.country == "DE"]) > 0
```

#### A path into an `object` is a normal field; a path through a `collection` is not

An `object` holds exactly one record, so a path into it has exactly one value. Such a path is used like any
other field — every operator, its declared `normalizers:` and its `format` all apply:

```yaml
schema: shipments-v1

fields:
  shipment:
    type: object
    fields:
      transitDays:
        type: integer
      pickedUpAt:
        type: date
        format: dd.MM.yyyy
      customer:
        type: object
        fields:
          tier:
            type: text
            normalizers:
              - trim
              - lowercase
```

```
rule "fast-gold-shipment" {
  when
    shipment.transitDays <= 2
    and shipment.customer.tier equals "gold"
    and shipment.pickedUpAt >= "01.03.2026"

  then
    label "premium-on-time"
}
```

A `collection` holds many records, so a path through it yields **one value per element** and cannot be
compared to a single literal. The engine rejects it and names the collection:

```text
Field 'orders.total' reads through collection 'orders', which yields one value per element and cannot be
compared directly; use an aggregate function such as count(orders), sum(orders.total) or a filter such as
orders[...]
```

> **Rule:** Compare a path into an `object` directly. Wrap a path through a `collection` in an aggregate
> function or a filter.

#### Declaring nested members is optional but recommended

| Nested `fields:` declared | What you get |
|---|---|
| Yes | Member names are validated, wrong nesting is an error at load time, leaf types are known, and the visual Builder offers the members in its dropdowns |
| No | Paths still work at runtime, but nothing below the declared field is checked — a typo like `orders.totl` goes unnoticed |

> **Rule:** When the business description mentions a list of records or a nested record, declare it as
> `collection` / `object` **with** its members. Only omit the members when the data shape is genuinely
> unknown.

> **Rule:** `normalizers:` and `operators:` are not valid on a `collection` or `object` field itself.
> Declare them on the nested scalar members instead.

#### Worked example

[`ruleengine-core/src/test/resources/warehouse-shipments`](ruleengine-core/src/test/resources/warehouse-shipments)
is a complete bundle built on both shapes: a `shipment` object read by plain conditions, and `parcels` and
`checkpoints` collections read by aggregates and filters. It ships two input files and is executed by
`WarehouseShipmentsIntegrationTest`, so every path in it is known to work.

---

## 4. Action Schema — Complete Reference

### File structure

```yaml
actions:
  <actionName>:             # lowercase hyphenated or camelCase identifier, must be unique
    argTypes: [<argType>]   # REQUIRED — one element from the argType table, or [] for no argument
```

> **Important:** An action takes **at most one argument**. `argTypes` holds exactly one entry for an
> action that takes a value, or is **empty** (`[]`) for an action that is just a signal.

### 4.1 Argument Types — Exhaustive List

| Type | Accepted values in rules | Example |
|---|---|---|
| `string` | Any text in double quotes | `label "rent"` |
| `integer` | A whole number (no quotes) | `score 10` |
| `decimal` | A number with decimal places (no quotes) | `threshold 0.75` |
| *(none)* | `argTypes: []` — the rule writes the bare action name | `suppress` |

> **Rule:** Only `string`, `integer`, and `decimal` are valid argument types. No other types exist for actions.

> **Rule:** The number of arguments in a rule must match `argTypes` exactly. An action declared
> `argTypes: [string]` must be given one quoted value; an action declared `argTypes: []` must be given
> none. `argTypes` never holds more than one entry.

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
  suppress:
    argTypes: []            # a signal with no argument
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
| `suppress` | *(none)* | Drop the record from downstream processing |

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
  description "<one sentence explaining what the rule is for>"

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
| `description "<text>"` | ⬜ | One double-quoted sentence. If present it must be the **first** thing inside `{`, before `when`. May appear at most once per rule. |
| `when` | ✅ | Keyword, followed by one or more conditions. |
| `then` | ✅ | Keyword, followed by one or more actions and/or `set` clauses (§5.6). |

**No other keys are valid inside a rule block.** Do not invent `priority`, `enabled`, `version`, `tags`, `salience` or `else` — the engine rejects them.

#### The `description` clause

Always emit a `description` when translating a business statement into a rule: the business analyst's own sentence is exactly what belongs there. It is the only part of the rule written for a human rather than the engine, and it is what appears in an exported rule overview handed to someone who has never seen this DSL.

```
rule "rent-payment" {
  description "A recurring payment of at least 300 whose purpose mentions rent."

  when
    purpose contains "rent"
    and amount >= 300

  then
    label "rent"
}
```

Rules:

- Optional — omitting it produces a **warning**, never an error, and the rule still loads, compiles and evaluates.
- Has **no effect on matching**. Never encode logic in it.
- Describe the **business intent**, not the mechanics. Write `"A valuable shipment needs a cover note."`, not `"Checks declaredValue between 1000 and 25000."`
- A `#` comment is **not** a description: comments are stripped when the file is read and never reach the engine. Use `#` for notes to other rule authors and `description` for the business reader.

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

#### Boolean condition examples

```
isActive equals true
isActive equals false
```

#### Date condition examples

```
bookingDate equals "2024-06-15"
bookingDate >= "2024-01-01"
bookingDate between "2024-01-01" "2024-12-31"
bookedAt gt "2024-06-15T09:00:00"
dueDate lt "31.01.2024"
```

> Date values are always quoted. Without a `format` they are ISO — `YYYY-MM-DD` for a `date`,
> `YYYY-MM-DDTHH:MM:SS` for a `date_time` — so `bookingDate > 20240101` and
> `bookingDate equals "15.06.2024"` are both rejected at load time. A field that declares a
> [`format`](#the-format-key) uses that pattern instead, which is why `dueDate` above is written
> `"31.01.2024"`.

#### Nested path condition examples

When a field is declared `object`, navigate into it with a dotted path and compare it like any other field —
named and symbolic operators both work:

```
shipment.customer.tier equals "gold"
shipment.transitDays <= 2
```

A path that reads through a `collection` cannot be compared directly, because it yields one value per
element. Aggregate or filter it instead — see
[5.8 Value Expressions](#58-value-expressions--aggregate-functions-and-arithmetic):

```
count(orders[customer.country == "DE"]) > 0
sum(orders.total) > 1000
```

#### The `ignoreCase` modifier

For text operators (`equals`, `contains`, `startsWith`, `endsWith`, `regex`) and string-set operators, append `ignoreCase` after the value to make the comparison case-insensitive:

```
counterparty equals "Netflix" ignoreCase
```

This is useful when a field does **not** have a `lowercase` or `uppercase` normalizer but you still need case-insensitive matching.

> **Rule:** `ignoreCase` only applies to `text` and `string_set` conditions. On a numeric, boolean, or
> date condition it is accepted but does nothing — do not write it there. It also cannot be combined
> with a symbolic operator: `counterparty == "Netflix" ignoreCase` is a **parse error**. Use the named
> operator (`equals`) when you need `ignoreCase`.

### 5.4 Combining conditions

#### AND — all conditions must be true

Use the `and` keyword between conditions:

```
rule "high-risk" {
  when
    country equals "ng"
    and amount >= 10000

  then
    flag "review"
}
```

Conditions on **consecutive lines** are also joined with AND, with no keyword needed. These two rules
are identical:

```
rule "high-risk-implicit" {
  when
    country equals "ng"
    amount >= 10000

  then
    flag "review"
}
```

> **Rule:** Prefer the explicit `and` in generated output. It reads unambiguously and survives
> reformatting; the implicit form depends on the line break.

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
- **Actions declared `argTypes: []`** are written as the bare name, with nothing after it:
  ```
  then
    suppress
    tag "noise"
  ```
- A rule may have **any number of actions**.
- All declared actions are returned when the rule matches.

### 5.6 Variables — the `set` clause

A rule's `then` block may publish a named value that the rules **after** it read. Use it when several
rules need the same computed value, so it is expressed and evaluated once.

```
then
  set <name> = <value expression>
```

Read it with a `$` prefix, anywhere a value expression may stand (§5.9):

```
when
  $<name> >= 1000
```

Worked example — the setter must come first:

```
rule "account-totals" {
  description "Computes the account turnover once for the tier rules that follow."

  when
    count(transactions) > 0

  then
    set turnover = sum(transactions.amount)
}

rule "high-turnover-account" {
  description "An account with a very high turnover is reviewed by hand."

  when
    $turnover >= 100000

  then
    label "manual-review"
    flag "high-turnover"
}
```

Rules:

| Aspect | Behaviour |
|---|---|
| Visibility | Only rules **after** the assigning rule, in the same manifest entry. "After" means manifest `rules:` file order, then declaration order within the file. |
| When it runs | Only if the rule matched — `set` sits in `then`, like an action. |
| Own actions | Assignments are applied **before** the same rule's actions resolve, so `score $turnover` in that rule works. |
| Never set | Reading it yields a missing value, so the condition is **false**. Evaluation never fails. |
| Re-assignment | Allowed; the last matching rule wins. Produces a **warning**. |
| Type | None declared — a variable carries whatever its expression produced, and the operand type check is skipped for it. |
| Lifetime | One evaluation of one entry. Never written back into the input; never carried to the next record. |

Writing rules:

- The name is written **without** `$` after `set`, and **with** `$` everywhere it is read.
- Name spelling follows field names: letters, digits, `_`, `-`; must not start with a digit.
- `$1`, `$2`, … are **not** variables — an all-digit name is a regex capture group of an `extract`
  clause (see `docs/rules.md`).
- A variable must not be named like a schema field.
- Do **not** use a variable in a named-operator condition (`$turnover gte 100`). A variable is only
  valid with a symbolic comparison: `==`, `!=`, `>`, `>=`, `<`, `<=`.
- Variables make rule order semantically significant. Say so in the manifest with a comment when you
  generate one (§6).

### 5.7 Rule ID conventions

- Must be **unique across all rule files** in the same manifest entry.
- Recommended formats:
  - Lowercase hyphenated: `rent-payment`, `fraud-keyword-purpose`
  - Uppercase underscore for formal codes: `LEGAL_1`, `AML_HIGH_RISK`
- Be descriptive — the ID appears in evaluation results and logs.

### 5.8 Complete rule file example

```
# transaction-classification.rule
# Classifies bank transactions by purpose and amount

rule "direct-debit" {
  description "The transaction carries one of the SEPA direct-debit codes."
  when
    sepaCode in ["DMCT", "DRNL", "PRCT"]
  then
    label "direct-debit"
    category "banking"
}

rule "salary-credit" {
  description "An incoming payment carrying the SEPA salary code."
  when
    sepaCode equals "SALA"
    and amount > 0
  then
    label "salary"
    category "income"
}

rule "rent-payment" {
  description "A payment of at least 300 whose purpose mentions rent."
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
  description "A verified premium customer made an incoming transfer."
  when
    tags containsAll ["premium", "verified"]
    and amount > 0
  then
    label "premium-credit"
    score 100
}
```

### 5.9 Value expressions — aggregate functions and arithmetic

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

Paths may be **any depth**, following the nested `fields:` you declared in the schema
([3.5](#35-nested-data--collections-and-objects)):

```
sum(orders.items.price)
count(orders[customer.country == "DE"])
```

> **Important — projection flattens.** `sum(orders.items.price)` is the sum across **all items of all
> orders**, not a per-order total. Every level is flattened into one list of values. The engine has no
> grouping construct; if you need a per-parent figure, the input data must supply it as a field.

A filter in `[...]` selects only matching elements. **Each** segment of a path may carry its own
filter:

```
transactions[label == "risk"].amount
transactions[amount > 0]
orders[status == "paid"].items[price > 0].price
```

Field names inside `[...]` refer to fields of the **element being filtered**, not to top-level fields.

#### Operators allowed inside a filter

A filter is a single comparison. This is narrower than a normal condition:

| Allowed inside `[...]` | Not allowed inside `[...]` |
|---|---|
| `==`, `!=`, `>`, `>=`, `<`, `<=` | `and`, `or`, `not` |
| named `gt`, `gte`, `lt`, `lte` | `equals`, `contains`, `in`, `between`, `regex`, `containsAny`, `containsAll` |

> **Rule:** Inside a filter, write equality as `==`, never as `equals`. To require two conditions on
> the same element, chain filters: `transactions[label == "risk"][amount > 100]`.
> Violations of this table are reported when the rules are compiled, not as validation warnings.

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
| A date | `date` |
| A date with a time of day ("booked at 09:30") | `date_time` |
| A date in a non-ISO format ("31.01.2024") | `date` / `date_time` + `format:` |
| A **list of records** ("each transaction has an amount and a label") | `collection` + nested `fields:` |
| A **single nested record** ("the customer has a country and an IBAN") | `object` + nested `fields:` |

**Ask yourself for each field:**
- Is it free text or a structured code? → `text`
- Does it have decimal places? → `decimal`, otherwise `integer`
- Is it only ever true or false? → `boolean`
- Is it a calendar date? → `date`, or `date_time` when the time of day matters
- Does the data spell dates in something other than ISO? → declare a `format:` on that field
- Can it hold multiple **plain strings** at once? → `string_set`
- Is it a list of things that each have their **own fields**? → `collection`, and declare those fields
- Is it one nested record with its own fields? → `object`, and declare those fields

> **Distinguishing `string_set` from `collection`:** a list of bare strings (`["vip", "premium"]`) is a
> `string_set`. A list of objects (`[{"amount": 100, "label": "risk"}]`) is a `collection`. Rules
> aggregate over a `collection`; they only test membership on a `string_set`.

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
  description "A payment of at least 300 whose purpose mentions rent."

  when
    (purpose contains "miete"
    or purpose contains "rent"
    or purpose contains "pacht")
    and amount >= 300

  then
    label "rent"
}

rule "salary-credit" {
  description "An incoming payment carrying the SEPA salary code."

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
  description "The counterparty IBAN is not German, Austrian or Swiss."

  when
    not iban regex "^(DE|AT|CH)"

  then
    flag "foreign-iban"
}

rule "structuring-suspicion" {
  description "Repeated payments just under the reporting threshold look like structuring."

  when
    count between 5 20
    and amount between 8000 9999

  then
    flag "structuring"
    alert "aml-structuring-suspicion"
}

rule "blocked-customer" {
  description "The customer is blocked or sanctioned, so the transaction is rejected."

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
| Unknown normalizer name | `normalizers: [strip]` (`strip` does not exist; use `trim`) |
| Unknown operator name | `operators: [greaterThan]` (`greaterThan` does not exist; use `gt`) |
| Nested `fields:` on a scalar type | `amount: {type: decimal, fields: {...}}` — only `collection` and `object` may nest |
| `format` on a non-date field | `purpose: {type: text, format: "dd.MM.yyyy"}` — only `date` and `date_time` accept it |
| Malformed `format` pattern | `format: "QQQQQQ"` (not a valid `DateTimeFormatter` pattern) |
| `format` that cannot represent the value | `format: "MM-dd"` on a `date` (no year), `format: "yyyy-MM-dd"` on a `date_time` (no time) |
| Empty `format` | `format: ""` — omit the key instead to get ISO |

> **Operator names must be canonical.** Write the spelling from the operator tables in
> [3.3](#33-operators--exhaustive-list-by-type): `startsWith`, not `starts_with` or `startswith`;
> `regex`, not `matches`. The engine accepts those variants as aliases so older schemas keep loading,
> but generated output should always use the canonical name. A name the engine has no implementation for
> — `greaterThan`, `not_contains`, `isEmpty` — is rejected when the schema loads.
>
> **A declared `operators:` list is a whitelist, not a type check.** Listing an operator the field's type
> does not support (`operators: [contains]` on an `integer`) still loads; the error surfaces on the rule
> that uses it. Declaring only a subset restricts the field to that subset, so a rule using any other
> operator is rejected even though the type supports it.

### Action schema constraints

| Constraint | Example of invalid usage |
|---|---|
| Unknown argument type | `argTypes: [bool]` (use `string`, `integer`, or `decimal`) |
| More than one argument type | `argTypes: [string, integer]` (at most one is allowed) |
| Argument count mismatch in a rule | `suppress "x"` when `suppress` is declared `argTypes: []`, or a bare `label` when `label` expects a string |

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
| Quoted or non-boolean value on a boolean field | `isActive equals "true"`, `isActive equals 1` |
| Non-ISO date literal on a field with no `format` | `bookingDate equals "15.06.2024"` (use `"2024-06-15"`) |
| ISO literal on a field that declares a `format` | `dueDate equals "2024-01-31"` when `dueDate` declares `format: "dd.MM.yyyy"` (use `"31.01.2024"`) |
| Date-only literal on a `date_time` field | `bookedAt equals "2024-06-15"` (use `"2024-06-15T00:00:00"`) |
| Comparing a structure directly | `transactions equals "x"` — navigate into it or aggregate over it |
| Unknown member of a declared structure | `orders.totl` when `orders` declares `total` |
| `and` / `or` inside a filter | `transactions[a > 1 and b > 2]` — chain filters instead |
| Named `equals` inside a filter | `transactions[label equals "risk"]` (use `==`) |
| Text operator inside a filter | `transactions[label contains "risk"]` |
| `ignoreCase` after a symbolic operator | `name == "Acme" ignoreCase` (use `equals`) |
| Reading a variable no earlier rule assigns | `$turnover >= 100` with no preceding `set turnover = …` (typo, or the setter is listed later) |
| Naming a variable like a schema field | `set amount = 1` when `amount` is declared in the field schema |
| Writing `$` on the left of `set` | `set $turnover = …` (the name is written bare after `set`) |

> **One warning, not an error:** a multi-segment path whose **root** is not declared in the schema
> produces a warning and the rule still loads, because the root may be an undeclared structure read
> straight from the input. `sum(unknownThing.amount) > 1` is therefore accepted with a warning, while a
> single-segment `unknownThing > 1` is an error. Declare the structure to get real checking.

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
- [ ] Lists of records are `collection`, nested records are `object`, and their members are declared
      under a nested `fields:`.
- [ ] Normalizers are only applied to `text` or `string_set` fields — never to a `collection` or
      `object` itself.
- [ ] All normalizer names come from the six built-in normalizers.
- [ ] All operator names match the valid operators for the field's type.
- [ ] No `operators:` or `normalizers:` on a `collection` / `object` field.
- [ ] A date whose time of day matters is a `date_time`, not a `date`.
- [ ] `format:` appears only on `date` / `date_time` fields, and only when the data is not ISO-8601.
- [ ] The schema has a versioned `schema:` name (e.g. `my-schema-v1`).

### Checklist: Action Schema

- [ ] Every outcome the business analyst described has a named action.
- [ ] Every action has one entry in `argTypes:`, or `[]` when it takes no argument.
- [ ] All arg types are `string`, `integer`, or `decimal`.

### Checklist: Rule Files

- [ ] Every rule has a unique, descriptive ID.
- [ ] Every field name in every condition exists in the field schema.
- [ ] Every operator used in a condition is in the field's allowed operators list.
- [ ] Named operators (`equals`, `gt`, …) are used for field-vs-literal comparisons; symbolic
      operators (`==`, `>`, …) only where a value expression is involved.
- [ ] `between` is only used on `integer`, `decimal`, `date` or `date_time` fields — two numeric bounds
      without quotes, or two quoted date values.
- [ ] Boolean values are the bare words `true` / `false`; date values are quoted, in the field's
      declared `format` when it has one and `YYYY-MM-DD` / `YYYY-MM-DDTHH:MM:SS` otherwise.
- [ ] `in` / `containsAny` / `containsAll` use a JSON-style list: `["a", "b"]`.
- [ ] Filters inside `[...]` use only `==`, `!=`, `>`, `>=`, `<`, `<=` and contain no `and` / `or`.
- [ ] Every action in every `then` block is defined in the action schema, with a matching argument count.
- [ ] String action arguments are in double quotes; numeric arguments have no quotes; zero-argument
      actions are written bare.
- [ ] Parentheses are used wherever AND/OR grouping could be ambiguous.
- [ ] `and` is written explicitly rather than relying on the implicit line-break AND.
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

