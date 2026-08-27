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
>
> **You can check your own output the same way.** The engine ships command-line validation, so generated
> artifacts do not have to be handed over untested — see [§10](#10-validating-what-you-generated).

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
10. [Validating What You Generated](#10-validating-what-you-generated)
11. [Quick-Reference Checklists](#11-quick-reference-checklists)

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
- Every rule is checked against the input and **all that match are returned** — one rule matching never suppresses another by itself, and there is no implicit priority.
- **Evaluation order is fixed and load-bearing.** Rules run in manifest `rules:` file order, then in declaration order within each file, and matches come back in that same order. The engine guarantees this because two constructs depend on it: a `set` clause publishes a value only the rules after it can read (§5.6), and a branch ending in `stop` ends the run at its own position (§5.11). Reordering the manifest can therefore change the result, not just its sequence.
- A rule may also declare an **`else` branch** — output for the case where its condition is false (§5.10). It changes what a single rule can produce, not how rules relate: a rule that does not match still produces nothing unless it says otherwise.
- A rule may also declare a **`not_exists` branch** — output for the case where the record does not carry the data the condition reads (§5.12). Without it, missing data makes the condition false, which is what every rule did before the branch existed.
- A branch may end in **`stop`** (§5.11), which ends the run: the rules declared after it are not evaluated at all for that record. This is the one construct that lets one rule suppress another.
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
    alias: <alias>             # OPTIONAL — a second name for this field, unique across the whole schema
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
| A field against a literal | **Named**: `equals`, `gt`, `gte`, `lt`, `lte`, `between`, `contains`, … | Fully validated: the field's declared `operators:` list is enforced and the literal type is checked |
| A value expression (aggregate or arithmetic) | **Symbolic**: `==`, `!=`, `>`, `>=`, `<`, `<=` | Required — the engine only routes a condition through the expression engine for symbolic operators |

> **Rule:** For a plain field-vs-literal comparison, prefer the **named** operator. `==` and `!=`
> always route to the expression engine, which does **not** enforce the field's declared `operators:`
> list. `>`, `>=`, `<`, `<=` on a plain field are equivalent to their named forms.
>
> Both spellings normalize the literal. On a field with a `lowercase` normalizer,
> `counterparty equals "ACME"` and `counterparty == "ACME"` both match the value `"acme"` — the
> symbolic form used to not, which made two spellings of one comparison answer differently.

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

  # Technical source name kept, with a readable alias for rule authors
  sepa_transaction_amount_decimal:
    type: decimal
    alias: transferAmount
    operators:
      - gt
      - gte
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
          loyaltyTier:
            type: text
            alias: tier
            normalizers:
              - trim
              - lowercase
```

```
rule "fast-gold-shipment" {
  when
    shipment.transitDays <= 2
    # 'tier' is an alias for 'loyaltyTier'; 'tier' on its own works too
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

### 3.6 Field Aliases

`alias:` gives one field a second name. Both names mean the same field and either may be written in a rule.
An alias exists for **readability**: use it when the source field name is technical (`sepa_txn_amt_dec`) or
when a deeply nested path would be repeated in many rules.

```yaml
schema: reports-v1

fields:
  reports:
    type: object
    fields:
      income:
        type: object
        fields:
          daysOfReport:
            type: integer
            alias: transactionHistoryDays
```

Both spellings compile to the same field:

```
when transactionHistoryDays >= 85                       # the alias on its own
when reports.income.transactionHistoryDays >= 85         # the alias in place of the segment it renames
when reports.income.daysOfReport >= 85                   # the declared path
```

#### Rules for aliases

| Rule | Why |
|---|---|
| An alias must be **unique across the whole schema**, at every depth | Two fields sharing one alias is a load-time ERROR |
| An alias must not equal any declared field name or dotted path | The declared name wins, so the alias would be unreachable — a load-time WARNING |
| An alias on a field **inside a `collection`** can never be used on its own | A path through a collection yields one value per element; write it inside the aggregate or the filter |
| A `collection` or `object` field may carry an alias | Use it where the structure itself is named: `count(orders)` → `count(purchases)` |
| Do **not** alias a field whose name is already business-readable | An alias per field doubles the vocabulary and buys nothing |

#### Illegal — a bare alias for a collection member

```yaml
fields:
  orders:
    type: collection
    fields:
      total:
        type: decimal
        alias: orderTotal
```

```
when orderTotal > 100          # REJECTED — reads through collection 'orders'
```

Write it in its path position instead:

```
when sum(orders.orderTotal) > 100
when count(orders[orderTotal > 100]) > 0
```

#### Illegal — a duplicate alias

```yaml
fields:
  income:
    type: object
    fields:
      total: {type: decimal, alias: total_amount}
  spending:
    type: object
    fields:
      total: {type: decimal, alias: total_amount}   # ERROR: duplicate alias 'total_amount'
```

> **Rule:** Declare an alias only when it earns its place. When in doubt, omit `alias:` and write the full
> dotted path in the rule — that always works and is never ambiguous.

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
| `variable_string` | A `$name` reference to a variable published with `set` (§5.6) | `reason $why` |
| `variable_list` | A `$name` reference to a list variable accumulated with `add` (§5.6.1) | `topics $topics` |
| *(none)* | `argTypes: []` — the rule writes the bare action name | `suppress` |

> **Rule:** Only `string`, `integer`, `decimal`, `variable_string` and `variable_list` are valid argument
> types. No other types exist for actions.

#### Variable arguments

Passing a variable to an action has always worked and needs no declaration: `label $why` is accepted for
an action declared `argTypes: [string]`, and the engine does not check what the variable holds.

Declaring `variable_string` or `variable_list` says that the argument **is** a variable reference, which
turns that into something the engine checks and an editor can complete against:

```yaml
actions:
  reason:
    argTypes: [variable_string]   # must be a $name written with `set`
  topics:
    argTypes: [variable_list]     # must be a $name written with `add`
```

```
then
  set why = "amount-too-low"
  add "billing" to topics
  reason $why                     # ✅
  topics $topics                  # ✅
  reason $topics                  # ❌ written with `add`, not `set`
  reason "amount-too-low"         # ❌ a literal where a reference is declared
```

The declaration changes nothing about how a rule is written — only what is checked and what the editor
offers. A `variable_list` argument reaches the consuming application as a list; a variable no rule that
ran published arrives as `null`.

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
- All matching rules fire; there is no implicit priority. A rule can suppress the rules after it only by ending its branch with `stop` (§5.11).
- Order is **fixed and guaranteed**: rules are evaluated in manifest `rules:` file order, then in declaration order within each file, and matches are returned in that order. `set` (§5.6) and `stop` (§5.11) both depend on it, so the order is part of the meaning.

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

  else                         # OPTIONAL — see §5.10
    <action>
    ...
    stop                       # OPTIONAL — see §5.11

  not_exists                   # OPTIONAL — see §5.12
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
| `else` | ⬜ | Keyword, followed by the output for a **false** condition. Same contents as `then`. At most once, and only after `then`. See §5.10. |
| `not_exists` | ⬜ | Keyword, followed by the output for a condition the record's data could not decide. Same contents as `then`. At most once, and **after** `else` when both are present. See §5.12. |
| `stop` | ⬜ | Bare word, the **last** statement of a `then`, `else` or `not_exists` block. Ends the run: the rules after this one are not evaluated. See §5.11. |

**No other keys are valid inside a rule block.** Do not invent `priority`, `enabled`, `version`, `tags` or `salience` — the engine rejects them.

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
| Never set | Reading it yields a missing value, so the condition is **false** — or, in a rule that declares a `not_exists` branch, *undecided* and that branch is taken (§5.12). Evaluation never fails either way. |
| Re-assignment | Allowed; the last matching rule wins. Produces a **warning**. |
| Type | None declared — a variable carries whatever its expression produced, and the operand type check is skipped for it. An action *may* declare that its argument is a variable (§4.1), which checks the clause that writes it but still does not type its value. |
| Lifetime | One evaluation of one entry. Never written back into the input; never carried to the next record. |

Writing rules:

- The name is written **without** `$` after `set`, and **with** `$` everywhere it is read.
- Name spelling follows field names: letters, digits, `_`, `-`; must not start with a digit.
- `$1`, `$2`, … are **not** variables — an all-digit name is a regex capture group of an `extract`
  clause (see `docs/rules.md`).
- A variable must not be named like a schema field.
- Do **not** use a variable in a named-operator condition (`$turnover gte 100`). A variable is valid
  with a symbolic comparison — `==`, `!=`, `>`, `>=`, `<`, `<=` — and with `contains`, which is the
  one named operator it accepts (§5.6.1).
- Variables make rule order semantically significant. Say so in the manifest with a comment when you
  generate one (§6).

#### 5.6.1 List variables — the `add` clause

A `set` publishes one value. To collect **several** values across rules, use `add`:

```
then
  add <value expression> to <name>
```

Read it back with `contains`:

```
when
  $<name> contains "something"
```

Use it for labelling: many rules producing the same outcome from different evidence, where each rule
should skip its work once the outcome is already recorded.

```
rule "billing-from-refund" {
  description "A refund request is a billing matter."

  when
    not $topics contains "billing"
    and purpose contains "refund"

  then
    label "billing"
    add "billing" to topics
}

rule "billing-from-invoice" {
  description "So is an invoice question — but the topic is only claimed once."

  when
    not $topics contains "billing"
    and purpose contains "invoice"

  then
    label "billing"
    add "billing" to topics
}

rule "route-billing" {
  description "Routing reads the finished list instead of the text again."

  when
    $topics contains "billing"

  then
    category "finance-team"
}
```

The guard is what makes this scale. `and` stops at the first false condition, and the engine
evaluates the cheapest condition of an `and` first — a list lookup is cheaper than a text search — so
a rule whose topic is already recorded never runs its text matching, whichever order the two
conditions are written in. An `or` is unaffected: it still evaluates its other branch.

| Aspect | Behaviour |
|---|---|
| Duplicates | Ignored. Adding a value the list already holds changes nothing, so two rules reaching the same conclusion produce one entry. |
| Order | Insertion order, kept. |
| Visibility | The rules after the `add`, **and the condition of the rule that writes it** — which is what lets a rule guard on the list it fills in. |
| Never added | Reading yields a missing value, so `contains` is **false** and `not … contains` is **true**. That is why the first rule of a guarded set fires. |
| Several writers | Expected, and not a warning — unlike two rules `set`ting one name. |
| Mixing | A name written by both `set` and `add` is an **error**: a variable is either a plain value or a list. |
| Value | Any value expression, as with `set`. A missing value adds nothing but still creates the list. |
| Result | Arrives as a list in `EvaluationResult.variables`. |

`contains` reads a list as membership and a text value as a substring. On the expression path it does
**not** apply the field's normalizers, so add values already in the form you will test for.

> **`add` is a keyword.** An action may not be named `add`; the engine reports it as an error and the
> action has to be renamed.

> **Do not give a guarded accumulator rule a `not_exists` branch.** The guard works because an unwritten
> list reads as missing and `not … contains` is therefore true. A rule that declares `not_exists` asks to
> hear about missing data instead, so the same guard becomes undecided and takes that branch (§5.12).

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

Each takes exactly **one argument** — a field path that resolves to a collection.

| Function | Description |
|---|---|
| `count(path)` | Number of elements |
| `sum(path)` | Sum of numeric values |
| `subtract(path)` | First element minus all subsequent elements |
| `avg(path)` | Arithmetic mean |
| `median(path)` | Median value |
| `max(path)` | Maximum value |
| `min(path)` | Minimum value |

#### Value functions

These do not reduce a collection; they transform values.

| Function | Description |
|---|---|
| `abs(value)` | Magnitude of a number. Zero and positives are unchanged, negatives become positive. |
| `daysBetween(from, to)` | Whole calendar days from `from` to `to`, **signed** — a `to` that comes first is negative. |
| `isAvailable(value)` | Whether the record carries the value at all — `true` or `false`, never missing. Accepts a field, a nested path, a whole `object` or `collection`, an aggregate or a variable. See §5.12. |

`abs` accepts a field, an aggregate, an arithmetic expression or a variable:

```
abs(sum(transactions.amount)) > 1000
```

`daysBetween` accepts `date` and `date_time` fields, and an ISO-8601 date literal. A `date_time` is
compared at calendar-day precision, so the time of day never adds a day. Either operand missing or
unreadable as a date yields a **missing** result, and a comparison against missing is false — the
rule does not match, and nothing is thrown.

```
daysBetween(registeredAt, submittedAt) >= 90
daysBetween(registeredAt, "2024-04-01") >= 90
```

#### Slicing an ordered collection

`take(path, n)` keeps at most the first `n` elements in source order; `takeLast(path, n)` keeps at
most the last `n`. `n` must be a non-negative whole number. A collection shorter than `n`, or empty,
simply yields what it has.

A slice is part of the path, so projection, filtering and aggregation continue from it:

```
sum(take(orders, 3).total) > 5000
count(takeLast(loginEvents, 10)[successful == false]) >= 3
```

Order matters. `takeLast(events, 10)[failed == true]` counts failures **among the last ten events**;
`takeLast(events[failed == true], 10)` counts **the last ten failures**.

#### Ordering a collection

`sortBy(path, asc|desc)` puts a collection of values in order; `sortBy(path, "member", asc|desc)`
orders a collection of objects by one of their members. The direction is **required**, and a member
name is always **quoted**.

An ordering is part of the path, so projection, filtering, slicing and aggregation continue from it:

```
sum(take(sortBy(orders, "total", desc), 3).total) > 5000
count(sortBy(loginEvents, "at", desc)) > 0
sortBy(priorityCustomerIds, asc) contains "acme"
```

Numbers order numerically, dates chronologically, text alphabetically, `false` before `true`. Values
of different kinds group by kind — numbers, dates, text, booleans — rather than ordering arbitrarily.
Ties keep their source order, which is what makes a `take` after an ordering deterministic.

Elements with nothing to order by go **last in both directions** — an absent member, a `null`, a
nested object or a nested list is not a value, so `take(sortBy(orders, "total", desc), 3)` gives the
three largest orders rather than three that never carried a total.

Order matters. `take(sortBy(orders, "total", desc), 3)` is **the three largest** orders;
`sortBy(take(orders, 3), "total", desc)` is **the first three**, put in order.

`sortBy` accepts a `collection` or a `string_set`. A collection of objects requires the member name;
a `string_set` or a collection of plain values must not be given one.

#### Membership — the `in` operator

`element in source` is true when the source holds a value equal to the element. The source may be a
`string_set` field, a projection across a collection, or a list variable:

```
sum(invoices[customerId in priorityCustomerIds].amount) > 10000
count(events[eventType in $importantEventTypes]) > 0
```

Both sides are matched under the normalizers declared on the fields, so `" ACME "` finds `"acme"`
when the field declares `trim` and `lowercase`. An **empty or missing source selects nothing**.
Membership composes with other filters on the same collection.

> A written-out list — `country in ["de", "at"]` — is the plain field comparison of §5.3, not a value
> expression. It keeps enforcing the field's declared `operators:` list.

#### Collection predicates — `every` and `any`

`every(collection[condition])` is true when every element satisfies the condition;
`any(collection[condition])` is true when at least one does. Both stop as soon as the answer is
decided.

```
every(lineItems[quantity >= 1])
any(alerts[severity == "high"])
```

**Empty collections:** `every` is **true** (there is no element that fails) and `any` is **false**
(there is no element that succeeds). A missing collection behaves like an empty one.

Both are ordinary boolean conditions, so they combine with `and`, `or` and `not`, and they work over
raw, filtered, sliced and joined collections:

```
every(take(lineItems, 5)[quantity >= 1]) and not any(alerts[severity == "high"])
```

#### Joining collections on a key — `sumByKey`

`sumByKey(key, source, source, ...)` aligns two or more collections on a shared member and returns
**one total per key**. The first argument is the key member's name as a string literal; each source
is `<collection>.<numericMember>`.

```
min(sumByKey("month", salesByMonth.amount, refundsByMonth.amount)) >= 0
```

- **Outer join:** every key any source mentions appears; a source that does not mention it contributes `0`.
- **Duplicate keys** within one source are summed, so the total is preserved.
- **Key order** is first-seen, reading the sources left to right.
- The result is an ordinary list of numbers, so `min`, `max`, `sum`, `count` and `every` apply to it.

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
| `==`, `!=`, `>`, `>=`, `<`, `<=`, `in`, `contains` | `between`, `startsWith`, `endsWith`, `regex`, `containsAny`, `containsAll` |
| named `equals`, `gt`, `gte`, `lt`, `lte` | `ignoreCase` |
| `and`, `or`, `not` between predicates | |

> **Rule:** Both sides of a filter predicate are full value expressions — an aggregate, arithmetic, a
> call, or a path that filters again are all legal, on either side. Names resolve against the element
> with the document's fields behind them, the element winning for a name they share.
>
> `and`, `or` and `not` combine predicates over the same element. Chaining —
> `transactions[label == "risk"][amount > 100]` — expresses the same thing as `and`, and is the only
> spelling that predates it; there is no chained form of `or`.
>
> Violations of this table are reported at validation and again when the rules are compiled.

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
- Wrong argument count for the function → error.
- Argument that cannot resolve to a collection → error.
- Numeric aggregate compared with a text literal → error.
- `contains`, `startsWith`, etc. used with a value expression → error.
- `abs` applied to a text field → error.
- `daysBetween` given an operand that is not readable as a date → error.
- `take` / `takeLast` with a negative or fractional size, or applied to something that is not a collection → error.
- `sortBy` applied to something that is not a collection or a `string_set`, given a member name for a
  collection of plain values, given none for a collection of objects, ordering by a member the
  collection does not declare, or ordering by a member that is itself a collection or object → error.
  A direction other than `asc` / `desc`, or an unquoted member name, is a parse error.
- `every` / `any` without a condition, e.g. `every(orders)` → error.
- `sumByKey` without a key literal, with fewer than two sources, joining on a member a source does not
  declare, on members whose types disagree, or summing a member that is not numeric → error.

#### Worked example

Schema:

```yaml
name: billing
fields:
  priorityCustomerIds:
    type: string_set
    normalizers: [trim, lowercase]
  reviewDate:
    type: date
  registeredAt:
    type: date
  invoices:
    type: collection
    fields:
      customerId:
        type: text
        normalizers: [trim, lowercase]
      amount:
        type: decimal
  loginEvents:
    type: collection
    fields:
      successful:
        type: boolean
  lineItems:
    type: collection
    fields:
      quantity:
        type: integer
  salesByMonth:
    type: collection
    fields:
      month:
        type: text
      amount:
        type: decimal
  refundsByMonth:
    type: collection
    fields:
      month:
        type: text
      amount:
        type: decimal
```

Rules — one per feature:

```
rule "priority-exposure" {
  description "Priority customers owe more than 10000."
  when
    sum(invoices[customerId in priorityCustomerIds].amount) > 10000
  then
    flag "exposure"
}

rule "recent-login-failures" {
  description "At least three of the last ten logins failed."
  when
    count(takeLast(loginEvents, 10)[successful == false]) >= 3
  then
    flag "login-failures"
}

rule "net-position" {
  description "Every month nets out non-negative."
  when
    min(sumByKey("month", salesByMonth.amount, refundsByMonth.amount)) >= 0
  then
    flag "net-position"
}

rule "established-account" {
  description "Registered at least 90 days before the review date."
  when
    daysBetween(registeredAt, reviewDate) >= 90
  then
    flag "tenure"
}

rule "balance-drift" {
  description "Invoices are more than 1000 away from balanced, either way."
  when
    abs(sum(invoices.amount)) > 1000
  then
    flag "drift"
}

rule "line-item-sanity" {
  description "Every line item has a real quantity."
  when
    every(lineItems[quantity >= 1])
  then
    flag "line-items"
}
```

> For the full reference including all edge cases see [docs/expressions.md](docs/expressions.md).

### 5.10 The optional `else` branch

A rule may declare what to produce when its condition is **false**. Write it as an `else` block after
the `then` block:

```
rule "order-tier" {
  description "An order of at least 1000 gets priority handling, anything smaller the standard path."

  when
    amount >= 1000

  then
    label "priority"

  else
    label "standard"
}
```

Use it when a business statement has two outcomes over one threshold. Without `else` that needs two
rules with the boundary written twice — `amount >= 1000` and `amount < 1000` — and the two drift apart
the first time someone changes only one of them.

The `else` block takes **exactly what a `then` block takes**: actions, `extract` clauses (§5.5) and
`set` clauses (§5.6).

```
rule "order-tier" {
  description "An order of at least 1000 is tier 2, anything smaller is tier 1."

  when
    amount >= 1000

  then
    label "priority"
    set tierLevel = 2

  else
    label "standard"
    set tierLevel = 1
}
```

Rules:

| Aspect | Behaviour |
|---|---|
| Optional | A rule without `else` behaves exactly as before: a false condition produces nothing. |
| Position | After the `then` block, before the closing `}`. |
| At most once | A second `else` on the same rule is an error. |
| Never empty | `else` with no action and no `set` is an error — drop the keyword instead. |
| Exclusive | Exactly one branch produces output per record. Never both, never neither. |
| Not a match | The rule's condition was **false**. `else` says what to output, not that the rule matched. |
| Variables | A `set` in `else` publishes to the following rules exactly as one in `then` does (§5.6). |

The engine reports an `else` result alongside the ordinary matches, tagged with the branch that
produced it, so a consumer can tell the two apart. Reading the result is covered in
[docs/integration-guide.md](docs/integration-guide.md).

`else` covers the case where the condition was **false**. For the case where the record carries no data
to decide it at all, see the `not_exists` branch (§5.12) — without it, missing data also lands here.

> **`else` is a keyword.** An action may not be named `else`. If an action schema declares one, the
> engine reports it as an error and the action has to be renamed.

#### When to use separate rules instead

`else` fits **one** condition with **two** outcomes. It does not extend to three or more bands: an
`else` fires whenever its own condition is false, including for records another rule already handled,
so chaining rules with `else` blocks makes a record collect every band it is not in.

For three or more bands, give each band its own rule and no `else`:

```
rule "tier-high" {
  description "An order of at least 1000 is high tier."
  when
    amount >= 1000
  then
    label "high"
}

rule "tier-mid" {
  description "An order from 100 up to 1000 is mid tier."
  when
    amount >= 100
    and amount < 1000
  then
    label "mid"
}

rule "tier-low" {
  description "An order below 100 is low tier."
  when
    amount < 100
  then
    label "low"
}
```

### 5.11 Ending the run — the `stop` keyword

A branch may end the run. Write `stop` as the **last** statement of a `then` or `else` block:

```
rule "blocked-country" {
  description "A payment to a sanctioned country is rejected outright; nothing else applies."

  when
    country in ["xx", "yy"]

  then
    label "rejected"
    stop
}
```

When that branch fires, the rule's own output is collected and then **no rule declared after it is
evaluated** for that record. This is the only construct in the DSL by which one rule suppresses another.

`stop` belongs to a branch, not to a rule, so a rule can halt on one verdict and carry on with the other:

```
rule "must-be-known-country" {
  description "An unknown country is rejected and nothing further is assessed."

  when
    country in ["de", "at", "ch"]

  then
    label "known-country"

  else
    label "rejected"
    stop
}
```

An `else` block containing nothing but `stop` is valid and means "halt when this condition does not hold".

Rules:

| Aspect | Behaviour |
|---|---|
| Position | The **last** statement of its block. Anything written after it is an error. |
| Scope | The remaining rules of the same manifest entry, across rule-file boundaries. |
| Own output | Collected first — `stop` halts what comes *after* the rule, not the branch it sits in. |
| Per branch | Valid in `then`, in `else`, or both. |
| Variables | Compatible. A variable published before the `stop` is in the result; the rules that would have read it are simply never reached. |

> **`stop` is a keyword.** An action may not be named `stop`. If an action schema declares one, the engine
> reports it as an error and the action has to be renamed.

> **Order becomes load-bearing.** A rule set using `stop` depends on its manifest order: moving a guard
> rule below the rules it was meant to guard silently stops guarding them. Say so in a comment next to the
> manifest `rules:` list when you generate one (§6).

#### When to use it

Use `stop` for a **guard**: a condition that settles the record outright, where every rule below it is
not merely overridden but inapplicable — a sanctioned counterparty, a missing mandatory field, a record
already rejected. Do not use it to express precedence between rules that should all contribute; that is
what separate conditions are for.

---

### 5.12 Missing data — the `not_exists` branch and `isAvailable()`

A condition needs data on both sides. When the record does not carry it, "the condition is false" is
the wrong answer — the truthful one is *"the condition could not be decided"*. A rule may say what to
produce in that case:

```
rule "order-tier" {
  description "An order of at least 1000 is priority, a smaller one standard, an order with no amount neither."

  when
    amount >= 1000

  then
    label "priority"

  else
    label "standard"

  not_exists
    label "unknown"
    flag "no-amount"
}
```

For a record with `amount: 5000` the rule produces `priority`, for `amount: 10` it produces `standard`,
and for a record with no `amount` at all — absent, or `null` — it produces `unknown` and `no-amount`.

The `not_exists` block takes **exactly what a `then` block takes**: actions, `extract` clauses (§5.5),
`set` and `add` clauses (§5.6), and `stop` (§5.11).

#### What makes a condition undecided

Two things, and only two:

| Source | Undecided? |
|---|---|
| A field the record does not carry, or carries as `null` | ✅ |
| A field whose value cannot be read as its declared type | ✅ |
| A variable no earlier rule published | ✅ |
| `avg` / `median` / `min` / `max` over a collection that is missing or empty | ✅ — they produce no value |
| `count(path)` / `sum(path)` over a missing collection | ⬜ — they produce `0`, a real number |
| `every(...)` over a missing collection | ⬜ — vacuously `true` |
| `any(...)` over a missing collection | ⬜ — `false`, no element satisfied it |
| A field that is present but simply does not match | ⬜ — that is an ordinary `false` |

#### How it combines

`and`, `or` and `not` answer "undecided" only when they have to. A condition reaches `not_exists` only
when its **own** answer is undecided:

```
amount >= 1000 or country == "de"      # country is "de"     -> then      (one true side is enough)
amount >= 1000 and country == "fr"     # country is "de"     -> else      (one false side settles it)
amount >= 1000 and country == "de"     # country is "de"     -> not_exists
not isAvailable(amount)                # amount absent       -> then
```

| Combination | Answer |
|---|---|
| `false and <undecided>` | `false` — nothing the missing side could say would change it |
| `true and <undecided>` | undecided |
| `true or <undecided>` | `true` — for the same reason |
| `false or <undecided>` | undecided |
| `not <undecided>` | undecided **in a rule that declares `not_exists`** |

#### `isAvailable()`

`isAvailable(<value>)` asks whether the record carries something, and answers a plain `true` or
`false` — never undecided. That is what makes it usable as a guard, on its own or negated with `not`:

```
when
  isAvailable(amount)
  and amount >= 1000
```

That rule takes `else` for a record with no balance, because the guard answered `false` and settled the
`and`. Use `isAvailable` when the *rule* should treat missing data as a plain no; use `not_exists` when
the **outcome** should say so.

It accepts anything a value expression may hold — a field, a nested path, a whole `object` or
`collection`, an aggregate, a variable:

```
isAvailable(transactions)
isAvailable($turnover)
not isAvailable(counterparty)
```

> **An empty collection is not "available".** An absent collection and an empty one are the same answer
> to *does the record carry this at all*, so `isAvailable(transactions)` is `false` for `transactions: []`. Use
> `count(transactions) == 0` to ask whether a collection is empty.

#### Rules

| Aspect | Behaviour |
|---|---|
| Optional | A rule without `not_exists` behaves exactly as it always did: undecided data reads as `false`, so the rule takes `else`, or produces nothing when it has no `else` either. |
| Position | After `then`, and after `else` when the rule declares one. Writing `else` after `not_exists` is an error. |
| At most once | A second `not_exists` on the same rule is an error. |
| Never empty | `not_exists` with no action, no `set` and no `stop` is an error — drop the keyword instead. |
| Exclusive | Exactly one branch produces output per record. Never two, never none. |
| Not a match | The rule's condition was neither true nor false. `not_exists` says what to output, not that the rule matched. |
| Variables | A `set` or `add` in `not_exists` publishes to the following rules exactly as one in `then` does (§5.6). |
| Trace | The rule's trace node records the verdict (`UNKNOWN`) and the branch it selected, so a run can be explained. |

> **`not_exists` is a keyword.** An action may not be named `not_exists`. If an action schema declares
> one, the engine reports it as an error and the action has to be renamed.

#### One interaction to know about

Declaring `not_exists` changes what `not` means for missing data **inside that rule**. The guarded
accumulator of §5.6.1 relies on `not $topics contains "billing"` being *true* while the list is still
empty; in a rule that declares `not_exists`, the same condition is undecided and the rule takes that
branch instead. Keep the two apart: a guard rule that fills a list should not declare `not_exists`.

#### When to use it

Use `not_exists` when "we could not tell" is a business outcome in its own right — an assessment that
must report `UNKNOWN` rather than `RED`, a check that has to be skipped and recorded as skipped, a
missing mandatory field that should raise a different flag from a wrong one. When missing data simply
means the rule does not apply, leave the branch out; the default already does that.

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
| `scope` | optional | Name of a declared `collection`. The rules run **once per member** of it instead of once for the whole document. |

> **No other keys are valid** at the entry level. Do not add keys like `version`, `description`, `priority`, or `enabled`.

#### Evaluating once per collection member — `scope`

Without `scope` a rule set is evaluated once against the whole document, which is the default and
what every manifest written before this key means.

```yaml
entries:
  - id: account-review
    scope: accounts
    schema: schema.yaml
    rules:
      - rules/exposure.rule
```

A scoped entry's rules are written from **one member's point of view**: they name the member's own
fields directly (`balance`, not `accounts.balance`). Fields the member does not carry resolve
against the document, so a rule can still read a shared threshold or watch list.

The result reports every match with the member it came from, plus one entry per member carrying that
member's own variables and `stoppedBy` — a `stop` ends one member's run, not the whole fan-out.

The scope is rejected at load time when it names no field, or names a field that is not a collection.

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

No `alias:` appears here — every field already has a business-readable name. Declare an alias only for a
technical or deeply nested field, see [3.6](#36-field-aliases).

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
| Duplicate alias | two fields both declaring `alias: amount`, at any depth |
| Blank alias | `alias: ""` — omit the key instead |

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
>
> **An alias must not collide with a declared field name.** The declared name wins and the alias becomes
> unreachable; the engine warns rather than rejecting. **An alias inside a `collection` cannot be used on
> its own** — see [3.6](#36-field-aliases).

### Action schema constraints

| Constraint | Example of invalid usage |
|---|---|
| Unknown argument type | `argTypes: [bool]` (use `string`, `integer`, `decimal`, `variable_string` or `variable_list`) |
| A literal where a variable argument is declared | `reason "x"` when `reason` is declared `argTypes: [variable_string]` |
| The wrong clause behind a declared variable argument | `topics $why` when `$why` is written with `set` and `topics` is declared `argTypes: [variable_list]` |
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
| `between` or a text-matching operator inside a filter | `transactions[amount between 1 5]`, `transactions[label startsWith "r"]` |
| `ignoreCase` inside a filter | `transactions[label == "risk" ignoreCase]` — normalize the member in the schema instead |
| Unknown member inside a filter | `transactions[bogus > 1]` when the collection declares no `bogus` |
| `sortBy` over something that has no elements | `sortBy(customer, "name", asc)` — `customer` is an `object` |
| `sortBy` given a member for a collection of plain values | `sortBy(tags, "total", asc)` when `tags` is a `string_set` |
| `sortBy` given no member for a collection of objects | `sortBy(orders, desc)` when `orders` declares members |
| `sortBy` ordering by an unknown or structural member | `sortBy(orders, "totl", desc)`, `sortBy(orders, "lines", asc)` |
| A `sortBy` direction other than `asc` / `desc`, or an unquoted member | `sortBy(orders, "total", upwards)`, `sortBy(orders, total, desc)` — both are parse errors |
| `ignoreCase` after a symbolic operator | `name == "Acme" ignoreCase` (use `equals`) |
| Reading a variable no earlier rule assigns | `$turnover >= 100` with no preceding `set turnover = …` (typo, or the setter is listed later) |
| Naming a variable like a schema field | `set amount = 1` when `amount` is declared in the field schema |
| Writing `$` on the left of `set` | `set $turnover = …` (the name is written bare after `set`) |
| An empty `else` block | `else` followed straight by `}` — drop the keyword instead |
| A second `else` on one rule | two `else` blocks in the same rule |
| `else` before `then` | the false branch is written after the true one |
| An empty `not_exists` block | `not_exists` followed straight by `}` — drop the keyword instead |
| A second `not_exists` on one rule | two `not_exists` blocks in the same rule |
| `else` after `not_exists` | the blocks are written `then`, `else`, `not_exists`, in that order |
| An action named `else`, `not_exists` or `stop` in the action schema | `else:`, `not_exists:` or `stop:` declared under `actions:` — all three are rule keywords |
| Anything written after `stop` in the same block | `stop` followed by another action or a `set` clause |

> **One warning, not an error:** a multi-segment path whose **root** is not declared in the schema
> produces a warning and the rule still loads, because the root may be an undeclared structure read
> straight from the input. `sum(unknownThing.amount) > 1` is therefore accepted with a warning, while a
> single-segment `unknownThing > 1` is an error. Declare the structure to get real checking.

### Manifest constraints

- `scope` naming a field the schema does not declare → rejected.
- `scope` naming a field that is not a `collection` → rejected.

| Constraint | Example of invalid usage |
|---|---|
| Missing `entries` key | A manifest YAML with no `entries` list |
| Missing `id` in an entry | An entry without an `id` field |
| Missing `rules` in an entry | An entry with no `rules` list |
| Unknown key in an entry | Adding `priority: 1` or `enabled: true` to an entry |
| Non-existent file path | A `schema:` path that does not resolve to an actual file |

---

## 10. Validating What You Generated

**Do not hand over rule artifacts you have not validated.** The engine checks everything at load time,
and you can run those same checks yourself from the command line. A generated project that has never
been loaded is a guess; one the validator accepts is not.

### Getting the engine

Add `ruleengine-core` as a dependency —
[mvnrepository.com/artifact/io.github.ragin-lundf/ruleengine-core](https://mvnrepository.com/artifact/io.github.ragin-lundf/ruleengine-core)
— or use a local build if the repository is checked out.

```kotlin
// Gradle (Kotlin DSL)
dependencies { implementation("io.github.ragin-lundf:ruleengine-core:<version>") }

tasks.register<JavaExec>("validateRules") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "ruleengine.cli.ValidatorCli"
    args("--manifest", "rules/manifest.yaml")
}
```

```xml
<!-- Maven -->
<dependency>
  <groupId>io.github.ragin-lundf</groupId>
  <artifactId>ruleengine-core</artifactId>
  <version><!-- latest --></version>
</dependency>
```

The two CLIs are ordinary `main` classes on that dependency's runtime classpath — there is no separate
launcher and no executable jar. Run them through a build tool (`JavaExec` above, or `mvn exec:java`), or
with `java -cp` if you already have a classpath. The classpath needs Jackson **and `kotlin-reflect`**;
both arrive transitively with the dependency. In a checkout of the engine's own repository the tasks
already exist: `./gradlew :ruleengine-core:validateRules --args="--manifest rules/manifest.yaml"`.

### Validate the four artifacts together

```bash
java -cp "<runtime classpath>" ruleengine.cli.ValidatorCli \
  --manifest rules/manifest.yaml [--entry <entry-id>] [--format json]
```

Use manifest mode, not `--schema` + `--rules`. It is the only mode that checks what a manifest adds: the
action schema, the rule-file order that decides which variables are in scope where, and rule ids repeated
across files. Every diagnostic names the rule file it came from and the line within that file.

| Exit code | Meaning |
|---|---|
| `0` | Valid. Warnings may still be reported — read them. |
| `1` | Invalid. Fix every `ERROR` and run it again. |
| `2` | Wrong arguments, or a path that is not usable. |
| `3` | Something was thrown — usually a file that does not exist, or a rule file that does not parse. |

`--format json` prints `{"diagnostics": [...], "ok": <bool>, "exitCode": <int>}`, where each diagnostic
carries `severity`, `message`, and — where the engine knows them — `file`, `line`, `column` and
`suggestion`. A `suggestion` is usually the exact fix: `Unknown field 'purpse'` with
`suggestion: "purpose"` means you mistyped a field name.

### Then check a record actually produces what you intended

Validation says the rules load. It does not say they decide correctly. Write a small input JSON and
evaluate it:

```bash
java -cp "<runtime classpath>" ruleengine.cli.EvaluateCli \
  --manifest rules/manifest.yaml [--entry <entry-id>] \
  --input-file record.json --trace --format pretty-json
```

Each entry of `matches` carries `ruleId`, `actions` and `branch` — `then`, `else` or `not_exists` — so
you can see not only *that* a rule produced something but which branch did. `--trace` adds the decision
tree, where every node reports its `verdict` (`TRUE`, `FALSE`, `UNKNOWN`) and the condition it tested,
which is how you find out *why* a rule did not fire.

### What to do with the results

1. Run the validator. Fix every `ERROR`; read every `WARNING` and decide deliberately whether it is
   intended (a missing `description` and a re-assigned variable are the common ones).
2. Re-run until it is clean. A diagnostic naming a field or action is almost always a typo — take the
   `suggestion`.
3. Evaluate at least one record you know the expected outcome of, and check the branch each rule took.
   Add a record with a field deliberately missing if any rule declares `not_exists` (§5.12).
4. Only then hand the artifacts over, and say which records you evaluated.

---

## 11. Quick-Reference Checklists

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
- [ ] Every alias is unique across the whole schema and collides with no declared field name.
- [ ] No alias is declared for a field whose name is already business-readable.
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
- [ ] Every action in every `then`, `else` and `not_exists` block is defined in the action schema, with a matching argument count.
- [ ] An `else` block is used only where one condition has exactly two outcomes; three or more bands are
      separate rules with no `else`.
- [ ] No `else` block is empty, duplicated, or written before its `then`.
- [ ] A `not_exists` block is used only where "the data was not there" is an outcome of its own; where
      missing data just means the rule does not apply, the block is left out.
- [ ] No `not_exists` block is empty or duplicated, and none is followed by an `else`.
- [ ] No rule that guards on an accumulator with `not … contains` also declares a `not_exists` block.
- [ ] `stop` is the last statement of its block, and is used only for a guard that genuinely makes every
      following rule inapplicable.
- [ ] When any rule uses `stop`, the manifest `rules:` list carries a comment saying the order matters.
- [ ] String action arguments are in double quotes; numeric arguments have no quotes; zero-argument
      actions are written bare.
- [ ] An action declared `variable_string` is given a `$name` written with `set`, and one declared
      `variable_list` a `$name` written with `add`.
- [ ] Parentheses are used wherever AND/OR grouping could be ambiguous.
- [ ] `and` is written explicitly rather than relying on the implicit line-break AND.
- [ ] Related rules are grouped in thematic files.
- [ ] Each file has a comment header explaining its purpose.
- [ ] The whole entry has been run through `ValidatorCli --manifest` and reports no `ERROR` (§10).

### Checklist: Manifest

- [ ] The manifest has a `name:` and an `entries:` list.
- [ ] Each entry has an `id:`, a `schema:` path, an `actions:` path, and a `rules:` list.
- [ ] All paths are relative to the manifest file.
- [ ] All referenced files are listed (no file is missing from the `rules:` list).
- [ ] Entry IDs are descriptive and use lowercase-hyphenated naming.

---

*This specification is complete. Do not add field types, operators, normalizers, action argument types, or manifest keys that are not listed in this document.*

