# Rules

Rules are the core of the rule engine.
A rule says: **"When these conditions are true, produce these actions."**

Rules are written in plain text files with the `.rule` extension.
The syntax is intentionally simple and close to natural language, so that business analysts and domain experts can write and review rules without developer support.

---

## Rule File Basics

- A `.rule` file can contain **one or more rules**.
- Each rule has a unique **ID** (a string in double quotes).
- Every rule is checked against the input and *all* that match are returned — one rule matching never suppresses another by itself. There is no implicit priority.
- Rules are evaluated in a **fixed, guaranteed order**: the [manifest](./manifest.md) `rules:` list order first, then the order the rules are declared within each file. Matches are returned in that same order.
- That order is **part of the meaning**, not only of the output. Two constructs depend on it: a `set` clause publishes a value only the rules *after* it can read, and a branch ending in `stop` ends the run at its own position, so the rules after it are not evaluated. Reordering the manifest can therefore change the result, not just its sequence.

---

## Rule Structure

```
rule "rule-id" {
  description "<what this rule is for, in one sentence>"

  when
    <condition>

  then
    <action>
    <action>
    ...

  else
    <action>
    ...
}
```

| Part | Required | Description |
|---|---|---|
| `rule "id"` | ✅ | Unique identifier for this rule |
| `description "..."` | ⬜ | One sentence explaining what the rule is for. Must come first, before `when` |
| `when` | ✅ | One or more conditions that must be true |
| `then` | ✅ | One or more actions to return when the rule matches |
| `else` | ⬜ | What to return when the condition is **false**. Same contents as `then` |

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

## Descriptions

A rule may open with a `description` clause: one sentence, in the reader's language, saying **what the rule is for** — not what it technically compares.

```
rule "rent-payment" {
  description "A recurring payment of at least 500 whose purpose mentions rent."

  when
    purpose contains "rent"
    and amount >= 500

  then
    label "rent"
}
```

- The clause is **optional**, and must appear **directly after the opening `{`**, before `when`.
- It takes exactly one double-quoted string. Writing it twice is an error.
- It has **no effect on evaluation** — the engine ignores it when matching rules.
- Leaving it out produces a **warning** from the validator, never an error. Existing rule files keep working unchanged.

Why it matters: the description is the only part of a rule written for a human rather than for the engine. It is what appears in an exported rule overview handed to a customer or published to a wiki, where the reader has never seen the DSL. Without it, such a reader gets only the rule id and the raw condition.

Write it as a statement about the business, not about the syntax:

| ✅ Good | ❌ Avoid |
|---|---|
| `"A valuable shipment needs a cover note."` | `"Checks declaredValue between 1000 and 25000."` |
| `"Gold-tier customers on an express service get the premium assessment."` | `"tier equals gold AND service contains express"` |
| `"Two or more parcels from the same hub travel together."` | `"A filter may read a nested member of the element it filters."` |

> **Comments are not descriptions.** A `#` comment is stripped when the file is read and never reaches the engine, so it cannot appear in an export. Use `#` for notes to other rule authors, and `description` for the sentence the business reader needs.

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

When the schema declares a field as an `object`, a condition navigates into it with a dotted path and
compares it like any other field:

```
shipment.customer.tier equals "gold"
shipment.transitDays <= 2
```

A `collection` holds many records, so a path through it has one value per element and needs a
[value expression](./expressions.md) — a total, an average, or a count of matching elements:

```
sum(orders[status == "paid"].total) > 1000
count(orders[customer.country == "DE"]) > 0
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
The schema may declare the path either as a single dotted field id or as nested `fields:` blocks — both
spellings work with every operator (see [Nested Data](./field-schema.md#nested-data)). A path that reads
through a `collection` is the one exception: aggregate or filter it instead.

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

## The `else` Branch

A rule can also say what to produce when its condition is **false**.
Write it as an `else` block after the `then` block:

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

The `else` block is **optional**. A rule without one behaves exactly as it always has: a false
condition produces nothing at all.

### Why

Without `else`, a business statement with two outcomes over one threshold needs two rules, and the
threshold gets written twice:

```
rule "order-priority" {
  when
    amount >= 1000
  then
    label "priority"
}

rule "order-standard" {
  when
    amount < 1000
  then
    label "standard"
}
```

That works, but the boundary now lives in two places. The first time someone moves one of them and not
the other, orders of exactly 1000 either get both labels or neither. One rule with an `else` has one
boundary.

### What an `else` block can contain

Exactly what a `then` block can: actions, `extract` clauses and `set` clauses.

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

A `set` in the `else` block publishes to the rules after it exactly as one in `then` does — the
variable carries whatever the branch that actually ran assigned.

### Rules

| Aspect | Behaviour |
|---|---|
| Optional | Omit it and a false condition produces nothing, as before. |
| Position | After the `then` block, before the closing `}`. |
| At most once | A second `else` on the same rule is an error. |
| Never empty | `else` with nothing in it is an error. Drop the keyword instead. |
| Exclusive | Exactly one branch runs per record. Never both, never neither. |
| Not a match | An `else` result means the condition was **false**. It says what to output, not that the rule matched. |

`else` is a keyword, so an action cannot be named `else`. If your action schema declares one, the engine
reports an error and the action has to be renamed.

### Reading the result

An `else` result is returned alongside the ordinary matches, tagged with the branch that produced it,
so nothing has to guess which half of the rule ran. See
[integration-guide.md](integration-guide.md).

### Use separate rules for three or more bands

`else` fits **one** condition with **two** outcomes. An `else` fires whenever its own condition is
false — including for records another rule already handled — so a chain of rules with `else` blocks
makes a record collect every band it is *not* in.

For three or more bands, give each its own rule and no `else`:

```
rule "tier-high" {
  when
    amount >= 1000
  then
    label "high"
}

rule "tier-mid" {
  when
    amount >= 100
    and amount < 1000
  then
    label "mid"
}

rule "tier-low" {
  when
    amount < 100
  then
    label "low"
}
```

---

---

## Ending the Run — the `stop` Keyword

A branch can end the run. Write `stop` as the **last** statement of a `then` or `else` block:

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
evaluated** for that record. This is what makes a guard rule a guard: everything below it is not merely
overridden, it never runs.

### Which branch stops

`stop` belongs to a branch, not to the rule, so a rule can halt on one verdict and carry on with the
other:

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

Here a known country continues through the rest of the rule set; an unknown one ends the run. An
`else` block containing nothing but `stop` is valid and means exactly that.

### Rules

| Aspect | Behaviour |
|---|---|
| Position | The **last** statement in its block. Anything after it is an error. |
| Scope | The remaining rules of the same manifest entry, across file boundaries. |
| Own output | Collected first. `stop` halts what comes *after* the rule, not the branch it sits in. |
| Per branch | Valid in `then`, in `else`, or both. |
| Variables | Compatible. A variable published before the `stop` is in the result; the rules that would have read it are simply not reached. |

`stop` is a keyword, so an action cannot be named `stop`.

### Why `stop` must be last

The lines below a `stop` would still run — a branch's output resolves before the halt takes effect — so a
block with `stop` in the middle would read as if half of it were dead. Requiring it last removes the
question. The visual Builder holds it as a badge pinned to the end of the branch, so it cannot get out of
place there.

### Reading the result

`EvaluationResult.stoppedBy` names the rule that halted the run, or is `null` when every rule was
evaluated. Without it, a consumer cannot tell *"no further rule matched"* from *"no further rule ran"*.
See [integration-guide.md](integration-guide.md).

The Test panel shows the difference directly: rules after the halt are reported as **not evaluated**
rather than as *no match*.

### Order becomes load-bearing

A rule set using `stop` depends on its manifest order. Moving a guard rule below the rules it was meant
to guard silently stops guarding them. Say so in a comment next to the `rules:` list when you write one —
see [manifest.md](manifest.md).

---


## Variables — the `set` Clause

A rule can publish a named value that the rules **after** it can read.
Use it when several rules need the same computed value: work it out once, then refer to it by name.

### Syntax

Write the assignment in the `then` block:

```
then
  set <name> = <expression>
```

Read it anywhere a value can stand, with a `$` in front of the name:

```
when
  $<name> >= 1000
```

The right-hand side of `set` is a full [value expression](expressions.md) — a field, a literal, an
aggregate, arithmetic, or another variable.

### Example

```
# totals.rule — listed first in the manifest
rule "account-totals" {
  description "Computes the account turnover once for the rules that follow."
  when
    count(transactions) > 0

  then
    set turnover = sum(transactions.amount)
}

# tiers.rule — listed after totals.rule
rule "active-account" {
  description "An account with meaningful turnover is treated as active."
  when
    $turnover >= 100

  then
    label "active"
}

rule "high-turnover-account" {
  description "A very high turnover is reviewed by hand."
  when
    $turnover >= 100000

  then
    label "manual-review"
    flag "high-turnover"
}
```

A variable can also be an action argument:

```
rule "turnover-score" {
  description "Reports the account turnover as a score."
  when
    count(transactions) > 0

  then
    set turnover = sum(transactions.amount)
    score $turnover
}
```

### Scope and Ordering

| Question | Answer |
|---|---|
| Who can read `$name`? | Only rules that come **after** the rule that sets it, within the same manifest entry. |
| What is "after"? | Manifest rule-file order first, then declaration order inside each file. See [manifest.md](manifest.md). |
| When does the assignment run? | Only if the rule **matched** — a `set` sits in `then`, like an action. |
| Can the same rule's actions read it? | Yes. Assignments are applied before the rule's own actions resolve. |
| What if nothing set it? | The read yields a missing value, so the condition is simply **false**. Evaluation never fails. |
| Can two rules set the same name? | Yes; the last matching rule wins. The validator warns, because it is usually unintended. |
| Does a variable change the input? | No. The engine never modifies input data; a variable lives only for the duration of one evaluation. |

> **This makes rule order significant.**
> Without variables or a `stop`, order only affects the order of the results. A rule that
> reads `$name` depends on an earlier rule having run and matched, so moving rule files around in the
> manifest can change the outcome.

### Naming

A variable name is written without the `$` in the `set` clause and with it everywhere else.
It follows the same spelling rules as a field name — letters, digits, `_` and `-`, starting with a
letter or `_`.

`$1`, `$2`, … are **not** variables; an all-digit name is a capture group of an
[`extract` clause](#extracting-values-into-actions--the-extract-clause).

### Validation Rules

| Rule | Severity |
|---|---|
| `$name` must be assigned by an earlier rule of the entry | ❌ error (with a "did you mean" suggestion) |
| A variable must not be named like a schema field | ❌ error |
| A `set` or `add` name must be written without the `$` prefix | ❌ parse error |
| The value expression is checked like any other (unknown fields, bad aggregates) | ❌ error |
| Two rules `set`ting the same name | ⚠️ warning |
| Two rules `add`ing to the same name | ✅ nothing — that is the point of `add` |
| One name written by both `set` and `add` | ❌ error |

The "assigned by an earlier rule" check is deliberately generous: it only asks that *some* earlier rule
assigns the name, not that the rule will actually match at runtime. Catching typos and forward
references is the point; proving a variable is always populated is not possible before the data arrives.

## Collecting Values — the `add` Clause

A `set` publishes one value. To collect **several** values across rules, use `add`:

```
then
  add <value expression> to <name>
```

and read the list back with `contains`:

```
when
  $<name> contains "something"
```

### Why it exists

Labelling. Many rules produce the same label from different evidence, and the expensive part of each
rule is the text matching. Guarding on the list lets a rule skip that work once the label is recorded:

```
# topics.rule
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

# routing.rule — listed after topics.rule, so it reads a finished list
rule "route-billing" {
  description "Routing reads the accumulated topics instead of the ticket text again."
  when
    $topics contains "billing"

  then
    category "finance-team"
}
```

For a ticket mentioning both, only the first rule fires. The second stops at its guard — it never
searches the text — and `label "billing"` is produced once rather than twice. `route-billing` then
works from the finished list rather than going back to the input.

The guard is cheap on purpose. `and` stops at its first false condition, and the engine evaluates the
cheapest condition of an `and` first, so the list lookup runs before the text search whichever order
you write them in. An `or` is unaffected: a false first operand still lets the second decide.

### Behaviour

| Question | Answer |
|---|---|
| What if the value is already in the list? | Nothing is added. The list behaves as a set, so two rules reaching the same conclusion produce one entry. |
| What order do the values come out in? | The order they were added. |
| Who can read `$name`? | The rules after the `add` — **and the condition of the rule that writes it**. That exception is what lets a rule guard on the list it fills in. |
| Why is that safe for `add` but not `set`? | An unread list is missing, `contains` on a missing value is false, and `not` of that is true — so the guard correctly passes the first time. `set total = $total + amount` has no such starting point, so reading a `set` variable before it is assigned stays an error. |
| Can two rules add to the same name? | Yes, and it is not a warning. That is the whole point. |
| Can a name be both? | No. A name written by both a `set` and an `add` is an error. |
| What can be added? | Any value expression, as with `set`. A missing value adds nothing, but the list is still created. |
| How does it arrive in the result? | As a list in `EvaluationResult.variables`. |

### `contains` on a variable

`contains` is the one named operator a variable accepts. What it means depends on what the variable
holds: membership for a list, a substring for a text value.

It does **not** apply normalizers — a variable has no schema entry to take them from — so add values
in the form you will test for. `add "Billing" to topics` is not found by `$topics contains "billing"`.

> **`add` is a keyword.** An action may not be named `add`. If an action schema declares one, the
> engine reports it as an error and the action has to be renamed.

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
| Comparing a path that reads through a collection | `Field 'orders.total' reads through collection 'orders' …` | Aggregate it (`sum(orders.total) > 100`) or filter it (`count(orders[total > 100]) > 0`) |
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

