# Rules

Rules are the core of the rule engine.
A rule says: **"When these conditions are true, produce these actions."**

Rules are written in plain text files with the `.rule` extension.
The syntax is intentionally simple and close to natural language, so that business analysts and domain experts can write and review rules without developer support.

---

## Rule File Basics

- A `.rule` file can contain **one or more rules**.
- Each rule has a unique **ID** (a string in double quotes).
- Rules are evaluated **independently** — the engine checks every rule against the input and returns all that match.
- The order of rules in a file does not affect results.

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

---

## Combining Conditions

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

A rule can have **any number of actions**.
All of them are returned when the rule matches.

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
| `between` used on a text field | `Operator 'between' is not applicable to text field` | Use `between` only on `integer` or `decimal` fields |
| Wrong argument type for an action | `Action 'score' argument 0 expects INTEGER` | Use a number, not a quoted string: `score 10` not `score "10"` |

---

## Tips and Best Practices

- **One file per topic** — group rules by domain (fraud rules, classification rules, etc.)
- **Write comments** — especially for complex regex patterns or multi-step conditions
- **Start simple** — begin with `equals` and `contains`, add `regex` only when simpler operators are insufficient
- **Test incrementally** — validate rules against sample data before deploying
- **Use the `ignoreCase` modifier** instead of duplicating rules for different capitalizations
- **Prefer `in` over many `or equals`** — `sepaCode in ["CCRD", "DCRD", "PMNT"]` is cleaner than three separate conditions

