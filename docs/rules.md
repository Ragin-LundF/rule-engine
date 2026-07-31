# Rules

Rules are the core of the rule engine.
A rule says: **"When these conditions are true, produce these actions."**

Rules are written in plain text files with the `.rule` extension.
The syntax is intentionally simple and close to natural language, so that business analysts and domain experts can write and review rules without developer support.

---

## Rule File Basics

- A `.rule` file can contain **one or more rules**.
- Each rule has a unique **ID** (a string in double quotes).
- Rules are evaluated **independently** — the engine checks every rule against the input and returns *all* that match. There is no priority or stop-first logic; one rule matching never suppresses another.
- Evaluation order **is deterministic**: rules run in the order they are declared in the file, and matches are returned in that same order. Across multiple files, the [manifest](./manifest.md) `rules:` list order applies first, then declaration order within each file. This does not change *which* rules match — only the order in which matches are returned.

---

## Rule Structure

```
rule "rule-id" {
  when
    <condition>

  then
    <action>
    <action>
    ...
}
```

| Part | Required | Description |
|---|---|---|
| `rule "id"` | ✅ | Unique identifier for this rule |
| `when` | ✅ | One or more conditions that must be true |
| `then` | ✅ | One or more actions to return when the rule matches |

### Minimal Example

```
rule "rent-payment" {
  when
    purpose contains "rent"
    and amount >= 500

  then
    label "rent"
}
```

---

## Conditions

A condition compares a **field** to a **value** using an **operator**.

```
fieldName operator value
```

The fields and allowed operators come from the [Field Schema](./field-schema.md).

### Text Conditions

| Operator | Example | Meaning |
|---|---|---|
| `equals` | `country equals "de"` | Exact match |
| `contains` | `purpose contains "rent"` | The field contains this text |
| `startsWith` | `iban startsWith "DE"` | The field begins with this text |
| `endsWith` | `purpose endsWith "GmbH"` | The field ends with this text |
| `in` | `sepaCode in ["CCRD", "DCRD"]` | The field matches one value from a list |
| `regex` | `iban regex "^DE[0-9]{20}$"` | The field matches a regular expression |

### Numeric Conditions (Integer and Decimal)

| Operator | Symbolic form | Example | Meaning |
|---|---|---|---|
| `equals` | `==` or `=` | `amount equals 0` | Exact numeric equality |
| `gt` | `>` | `amount > 1000` | Greater than |
| `gte` | `>=` | `amount >= 500` | Greater than or equal |
| `lt` | `<` | `amount < 0` | Less than |
| `lte` | `<=` | `amount <= 9999` | Less than or equal |
| `between` | — | `amount between 100 5000` | Inclusive range (both bounds included) |

### String Set Conditions

| Operator | Example | Meaning |
|---|---|---|
| `containsAny` | `tags containsAny ["vip", "premium"]` | At least one listed value is in the set |
| `containsAll` | `tags containsAll ["verified", "active"]` | All listed values are in the set |

### Boolean Conditions

| Operator | Example | Meaning |
|---|---|---|
| `equals` | `isActive equals true` | The flag has this value |

`equals` is the only operator, and the value is the bare word `true` or `false` — never quoted.

```
isActive equals true
isActive equals false
```

> **Note:** `not isActive equals true` also matches records where the field is missing, because a
> missing value makes the inner condition false. Write `isActive equals false` when the flag must be
> present and false.

### Date Conditions

There is no `before` or `after` operator: **`lt` means before and `gt` means after.**

| Operator | Symbolic form | Example | Meaning |
|---|---|---|---|
| `equals` | `==` | `bookingDate equals "2024-06-15"` | Same day |
| `gt` | `>` | `bookingDate > "2024-01-01"` | After |
| `gte` | `>=` | `bookingDate >= "2024-01-01"` | On or after |
| `lt` | `<` | `bookingDate < "2020-01-01"` | Before |
| `lte` | `<=` | `bookingDate <= "2024-12-31"` | On or before |
| `between` | — | `bookingDate between "2024-01-01" "2024-12-31"` | Inclusive date range |

A `date_time` field uses the same operators and compares the time of day too:

```
bookedAt gt "2024-06-15T09:00:00"
bookedAt between "2024-06-15T09:00:00" "2024-06-15T17:00:00"
```

Dates are always quoted. Without a declared format they are ISO — `YYYY-MM-DD` for a `date`,
`YYYY-MM-DDTHH:MM:SS` for a `date_time` — so `bookingDate > 20240101` and
`bookingDate equals "15.06.2024"` are both rejected when the rules load.

When the schema declares a [`format`](field-schema.md#date-formats) for the field, that pattern is what
the rule must use instead:

```
dueDate lt "31.01.2024"
```

### Conditions on Nested Data

When the schema declares a field as a `collection` or `object`, a condition can navigate into it:

```
orders.customer.country == "DE"
```

To reason about a whole list — a total, an average, a count of matching elements — use a
[value expression](./expressions.md):

```
sum(orders[status == "paid"].total) > 1000
```

### Named Operators vs Symbolic Operators

Most operators can be written as a word (`equals`, `gt`) or a symbol (`==`, `>`). They are not
interchangeable in one respect:

| Comparison | Write | Why |
|---|---|---|
| A field against a value | **Named** — `equals`, `gt`, `contains`, … | Fully checked: the field's declared operator list applies, the value type is verified, and text normalizers are applied to the value as well as the field |
| An aggregate or calculation | **Symbolic** — `==`, `!=`, `>`, `>=`, `<`, `<=` | Required; see [Value Expressions](./expressions.md) |

> **Important:** `==` and `!=` always go through the value-expression engine, which does not apply
> normalizers to the value. On a field with a `lowercase` normalizer, `counterparty equals "ACME"`
> matches the stored value `acme`, but `counterparty == "ACME"` does not. Use the word form for plain
> field comparisons. `>`, `>=`, `<` and `<=` behave the same in both spellings.

### Field Notation and Aliases

Rules can use either the **full dot-notation path** or a **field alias** to refer to data fields. 

#### Full Dot-Notation
Use the complete path to a field as defined in the schema. This is highly explicit and avoids ambiguity.

To understand how dot-notation works, consider the following input JSON:

```json
{
  "user": {
    "profile": {
      "email": "user@example.com",
      "age": 30
    },
    "account_info": {
      "status": "active"
    }
  },
  "transaction": {
    "metadata": {
      "vendor_id": "VEND-123",
      "location": {
        "city": "Berlin",
        "country": "DE"
      }
    }
  }
}
```

The paths to specific fields would be:
- `user.profile.email`
- `user.account_info.status`
- `transaction.metadata.vendor_id`
- `transaction.metadata.location.city`

#### Field Aliases
To make rules more readable and maintainable, you can define short aliases for complex paths in your schema configuration.

```
# If 'user.profile.email' is aliased to 'email'
email contains "@gmail.com"

# If 'transaction.metadata.vendor_id' is aliased to 'vendor'
vendor equals "VEND-123"
```

### AND — All conditions must be true

```
rule "high-risk-country" {
  when
    country equals "ng"
    and amount >= 10000

  then
    flag "review"
    score 100
}
```

Both `country equals "ng"` **and** `amount >= 10000` must be true for the rule to match.

Conditions on consecutive lines are joined with AND automatically, so the `and` keyword can be left
out. These two blocks mean the same thing:

```
when
  country equals "ng"
  and amount >= 10000
```

```
when
  country equals "ng"
  amount >= 10000
```

Writing `and` explicitly is clearer and does not depend on the line break, so prefer it.

### OR — At least one condition must be true

```
rule "vip-customer" {
  when
    tags containsAny ["vip"]
    or tags containsAny ["premium"]

  then
    label "vip"
}
```

The rule matches if the customer has **either** tag.

### NOT — Negates a condition

```
rule "non-dach-iban" {
  when
    not iban regex "^(DE|AT|CH)"

  then
    flag "foreign-iban"
}
```

The rule matches when the IBAN does **not** start with DE, AT, or CH.

### Operator Precedence

When mixing `and`, `or`, and `not` without parentheses, the precedence is:

```
not  (highest)
and
or   (lowest)
```

This means:
```
A or B and C
```
is interpreted as:
```
A or (B and C)
```

Use parentheses to make grouping explicit.

---

## Grouping with Parentheses

Parentheses let you create complex conditions that are easy to read and unambiguous.

```
rule "chargeback-small" {
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

The rule matches when the purpose mentions any of the three words **and** the amount is negative.

---

## The `ignoreCase` Modifier

For text operators (`equals`, `contains`, `startsWith`, `endsWith`, `regex`), you can add `ignoreCase` after the comparison value to make the match case-insensitive — regardless of whether the field has a `lowercase` normalizer.

```
rule "streaming-subscription" {
  when
    counterparty equals "Netflix" ignoreCase
    or counterparty equals "Spotify AB" ignoreCase

  then
    label "streaming"
    category "entertainment"
}
```

This matches `NETFLIX`, `netflix`, `Netflix`, etc.

> If the field already uses a `lowercase` normalizer, `ignoreCase` is redundant but harmless.

---

## Actions in Rules

Actions appear in the `then` block.
Each line is one action: the action name followed by its argument.

```
then
  label "rent"
  category "housing"
  score 10
```

String arguments are always in double quotes.
Numeric arguments are written as plain numbers.

An action declared in the schema with `argTypes: []` takes no argument and is written as the bare name:

```
then
  suppress
  tag "noise"
```

A rule can have **any number of actions**.
All of them are returned when the rule matches.

---

## Extracting Values into Actions — the `extract` Clause

Sometimes the argument you want to pass to an action is not a fixed string but a **value computed from the input data at the time the rule fires**.
The `extract` clause lets you apply a regular-expression capture group to a text field and use the result as the action argument.

### Syntax

```
extract <fieldName> regex("<pattern>", <groupIndex>) <actionName> $1
```

| Part | Description |
|---|---|
| `fieldName` | The name of a **text** field in the field schema |
| `"<pattern>"` | A regular expression with one or more capture groups |
| `<groupIndex>` | The capture group to extract: `1` for the first group, `2` for the second, `0` for the whole match |
| `<actionName>` | The action that will receive the extracted value |
| `$1` | A placeholder that refers to the extracted value |

### Example — Extract a transaction ID from a reference field

```
rule "tag-transaction-id" {
  when
    reference regex "TXN-[0-9]+"

  then
    extract reference regex("TXN-([0-9]+)", 1) label $1
}
```

When a transaction has `reference = "TXN-98765"`, the rule matches and the result contains:

```json
{ "name": "label", "arguments": ["98765"] }
```

### Example — Extract an email username

```
rule "label-by-username" {
  when
    user_email regex ".+@.+"

  then
    extract user_email regex("([a-z0-9._%+\\-]+)@.*", 1) label $1
}
```

### Combining `extract` with static actions

An `extract` clause produces exactly one action.
You can freely mix `extract` actions and regular static actions in the same `then` block:

```
rule "classify-with-id" {
  when
    reference regex "TXN-[0-9]+"

  then
    extract reference regex("TXN-([0-9]+)", 1) label $1
    category "transactions"
}
```

This emits two actions: a `label` with the extracted ID, and a static `category "transactions"`.

### What happens when the pattern does not match

If the extraction regex does not find a match in the field value at evaluation time, the action argument is **`null`** (empty).
The action is still emitted — the consuming application should handle `null` arguments defensively.

This is intentional: the rule's `when` condition governs whether the rule fires; a failing extraction at the action level does not suppress the action.

> **Tip:** Align the `when` condition and the extraction pattern so the extraction can only fail in practice if the input data is malformed. For example, use the same regex in both:
>
> ```
> when
>   reference regex "TXN-[0-9]+"
> then
>   extract reference regex("TXN-([0-9]+)", 1) label $1
> ```

### Extraction — Validation Rules

| Constraint | Example of invalid usage |
|---|---|
| Source field must exist in the field schema | `extract unknownField regex("(.*)", 1) label $1` |
| Source field must be of type `text` | Using `extract` on a `decimal` or `integer` field |
| Regex pattern must be valid | `extract ref regex("[invalid", 1) label $1` |
| Group index must be ≥ 0 | `extract ref regex("(.*)", -1) label $1` |
| `$1` requires an `extract` clause | Using `$1` in a plain action without `extract` |
| Action argument type must be `string` | Using `$1` with an `integer` action like `score` |


---

## Comments

Lines starting with `#` are comments and are ignored by the engine:

```
# This rule detects rent payments
rule "rent-payment" {
  when
    purpose contains "rent"
    and amount >= 300

  then
    label "rent"
}
```

Use comments to document the intent of a rule, especially for complex conditions.

---

## Multiple Rules in One File

A single `.rule` file may contain multiple rules.
It is good practice to group related rules in one file:

```
# fraud-detection.rule — rules for fraud and AML detection

rule "non-dach-iban" {
  when
    not iban regex "^(DE|AT|CH)"
  then
    flag "foreign-iban"
}

rule "high-value-outgoing" {
  when
    amount < -10000
  then
    flag "high-value"
    alert "payment-above-threshold"
}

rule "zero-amount-probe" {
  when
    amount equals 0
  then
    flag "probe"
    alert "zero-amount-detected"
}
```

---

## Rule IDs

Rule IDs must be **globally unique** across all loaded rule files.
If two rules have the same ID, the engine reports a validation error and refuses to load.

**Good ID conventions:**
- Use lowercase, hyphenated identifiers: `rent-payment`, `fraud-keyword-purpose`
- Or uppercase with underscores for formal codes: `LEGAL_1`, `AML_HIGH_RISK`
- Be descriptive — the ID appears in results and logs

---

## Complete Real-World Examples

### Transaction Classification

```
# sepa-classification.rule

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
    (purposeNorm contains "miete"
    or purposeNorm contains "rent"
    or purposeNorm contains "pacht")
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

### Fraud and AML Detection

```
# fraud-detection.rule

rule "structuring-suspicion" {
  when
    count between 5 20
    and amount between 8000 9999
  then
    flag "structuring"
    alert "aml-structuring-suspicion"
}

rule "foreign-high-value" {
  when
    (not iban regex "^(DE|AT|CH)")
    and amount < -5000
  then
    label "fraud"
    alert "foreign-high-value-outgoing"
    reject "compliance-block"
}

rule "flagged-customer" {
  when
    tags containsAny ["blocked", "sanctioned", "aml-watch"]
  then
    flag "compliance"
    alert "flagged-customer-transaction"
    reject "aml-block"
}
```

---

## Common Mistakes and How to Fix Them

| Mistake | Error message | Fix |
|---|---|---|
| Using a field name that is not in the schema | `Unknown field 'purpse' in condition` | Check spelling; the engine may suggest the closest match |
| Using an operator not allowed for a field | `Operator 'contains' is not allowed for field 'amount'` | Use a numeric operator like `gt`, `gte`, `lt`, `lte`, `between` |
| Using an action not defined in the action schema | `Unknown action 'notify'` | Add `notify` to the action schema YAML |
| Two rules with the same ID | `Duplicate rule id: rent-payment` | Give each rule a unique ID |
| `between` used on a text field | `Operator 'between' is not applicable to text field` | Use `between` only on `integer`, `decimal`, `date` or `date_time` fields |
| Wrong argument type for an action | `Action 'score' argument 0 expects INTEGER` | Use a number, not a quoted string: `score 10` not `score "10"` |
| Quoting a boolean value | `Field 'isActive' expects 'true' or 'false'` | Write `isActive equals true`, without quotes |
| A date in the wrong format | `Invalid date '15.06.2024' … expected ISO format YYYY-MM-DD` | Use `"2024-06-15"` |
| An ISO date on a field that declares a format | `Invalid date '2024-01-31' … expected format 'dd.MM.yyyy' (e.g. "31.01.2024")` | Write the value in the field's own format |
| Comparing a collection or object directly | `Field 'orders' is a collection and cannot be compared directly` | Navigate into it (`orders.total`) or aggregate over it (`sum(orders.total)`) |
| Misspelling a member of a declared collection | `Unknown field 'totl' in 'orders.totl'` | Check the nested `fields:` block in the schema |
| `and` inside a path filter | `Only comparison expressions are supported in filter segments` | Chain filters: `orders[status == "paid"][total > 100]` |
| `equals` inside a path filter | `Operator 'equals' is not supported in filter segments` | Use `==` inside `[...]` |
| `ignoreCase` after a symbolic operator | `Expected 'then' block` | Use the word form: `name equals "Acme" ignoreCase` |
| An action given the wrong number of arguments | `Action 'suppress' expects 0 arguments but got 1` | Match the action's `argTypes` — a bare name when it is `[]` |

---

## Tips and Best Practices

- **One file per topic** — group rules by domain (fraud rules, classification rules, etc.)
- **Write comments** — especially for complex regex patterns or multi-step conditions
- **Start simple** — begin with `equals` and `contains`, add `regex` only when simpler operators are insufficient
- **Test incrementally** — validate rules against sample data before deploying
- **Use the `ignoreCase` modifier** instead of duplicating rules for different capitalizations
- **Prefer `in` over many `or equals`** — `sepaCode in ["CCRD", "DCRD", "PMNT"]` is cleaner than three separate conditions
- **Write `and` explicitly** — consecutive lines are joined with AND automatically, but the keyword makes the intent obvious and survives reformatting
- **Use the word form for field comparisons** — `equals` rather than `==`, so normalizers and the schema's operator list both apply
- **Mirror the extraction pattern in the `when` condition** — if you use `extract ref regex("TXN-([0-9]+)", 1)` in the `then` block, guard the rule with `reference regex "TXN-[0-9]+"` so the extraction only runs when a match is guaranteed
- **Extraction always produces a string** — only use `$1` as the argument for `string`-typed actions; it cannot be used with `integer` or `decimal` action arguments

