# Value Expressions

Value expressions extend the rule DSL with aggregate functions, arithmetic, and symbolic comparison operators.
They allow rules to reason about **collections of objects** — for example, summing amounts across a list of transactions.

Declare the collections they operate on in the [field schema](./field-schema.md#nested-data): a
`collection` field with its members listed under a nested `fields:` block. Aggregates work without that
declaration, but nothing in the path gets checked.

---

## 1. Overview

A standard condition compares a single field to a literal value:

```
amount >= 500
```

A value expression condition compares two computed values, at least one of which uses an aggregate function or arithmetic:

```
sum(transactions.amount) > 1000
sum(transactions[label == "risk"].amount) > sum(transactions[amount > 0].amount) * 0.03
```

Value expression conditions can be combined with `and`, `or`, and `not` just like regular conditions.

---

## 2. Value Expressions

A value expression is one of:

| Form | Example | Description |
|---|---|---|
| Literal number | `100`, `0.03` | A numeric constant |
| Literal text | `"risk"` | A text constant (inside filter only) |
| Field path | `transactions.amount`, `orders[status == "paid"].items.price` | Navigate into nested fields to any depth, optionally filtering at each level |
| Aggregate function call | `sum(transactions.amount)` | Apply an aggregate function to a collection |
| Arithmetic expression | `sum(...) * 0.03` | Combine value expressions with `+`, `-`, `*`, `/` |
| Variable read | `$orderTotal` | A value published by an earlier rule's `set` clause — see [rules.md](rules.md#variables--the-set-clause) |

A variable is usable wherever any other value expression is: as either side of a comparison, as an
operand of arithmetic, as the argument of an aggregate, inside a filter predicate, and as the
right-hand side of another `set`.

```
$orderTotal * 0.03 > 25
count(orders[amount > $threshold]) >= 2
```

A variable has no declared type — it carries whatever its `set` expression produced — so the operand
type check that applies to fields is skipped for it. A comparison against a variable that no matching
rule assigned is `false`, the same as one against a missing field.

---

## 3. Arithmetic Operators

| Operator | Symbol | Example |
|---|---|---|
| Addition | `+` | `sum(a.amount) + sum(b.amount)` |
| Subtraction | `-` | `sum(a.amount) - sum(b.amount)` |
| Multiplication | `*` | `sum(transactions.amount) * 0.03` |
| Division | `/` | `sum(transactions.amount) / count(transactions)` |

Standard precedence applies: `*` and `/` bind tighter than `+` and `-`.
Use parentheses to override precedence:

```
(sum(a.amount) + sum(b.amount)) * 0.5
```

---

## 4. Comparison Operators

Value expression conditions use symbolic comparison operators:

| Operator | Meaning |
|---|---|
| `==` | Equal |
| `!=` | Not equal |
| `>` | Greater than |
| `>=` | Greater than or equal |
| `<` | Less than |
| `<=` | Less than or equal |
| `contains` | Membership in a list, or a substring of text — see below |

`contains` is the one named operator this path accepts, and it is only meaningful when its left side
is a variable. What it does depends on what that variable holds at evaluation time:

| Left value | `contains` means |
|---|---|
| A list, from an `add` clause | true when some element equals the right side |
| A text value | true when the right side is a substring, **case-sensitively** |
| Missing (never assigned) | false — so `not $name contains "x"` is true |
| A number, a boolean, a structure | false |

Numbers compare by value, so a list holding `1` is found by `contains 1.0`.

A plain `field contains "literal"` is **not** this operator. It stays on the named-operator path,
which enforces the field's declared `operators:` list and applies its normalizers. Only a comparison
with a variable, an aggregate, arithmetic or a filtered path comes here.

`contains` with a left side that is not a variable — `count(orders) contains 5` — is accepted by the
validator but can never match. There is no array type in the operand-kind check to reject it with.

```
count(transactions) > 100
avg(transactions.amount) >= 25
sum(transactions[label == "risk"].amount) != 0
```

> **Note:** The legacy named operators (`equals`, `gt`, `gte`, `lt`, `lte`) remain supported for plain field comparisons.
> Symbolic operators are required when either side of the comparison is an aggregate function or arithmetic expression.

---

## 5. Aggregate Functions

All aggregate functions take exactly **one argument**, which must be a field path that resolves to a collection.

| Function | Description | Empty collection | Missing collection |
|---|---|---|---|
| `count(path)` | Number of elements in the collection | `0` | missing |
| `sum(path)` | Sum of all numeric values | `0` | missing |
| `subtract(path)` | First element minus all subsequent elements | `0` | missing |
| `avg(path)` | Arithmetic mean of all numeric values | missing | missing |
| `median(path)` | Median value | missing | missing |
| `max(path)` | Maximum value | missing | missing |
| `min(path)` | Minimum value | missing | missing |

Non-numeric elements in the collection are silently skipped during numeric aggregation.

### Examples

```
count(transactions) > 2
sum(transactions.amount) > 1000
avg(transactions.amount) > 25
max(transactions[label == "risk"].amount) > 500
min(transactions.amount) >= 0
```

## 5a. Value Functions

These transform values rather than reducing a collection.

| Function | Description | Missing input |
|---|---|---|
| `abs(value)` | Magnitude of a number; zero and positives unchanged | missing |
| `daysBetween(from, to)` | Signed whole calendar days from `from` to `to` | missing |
| `isAvailable(value)` | Whether the record carries the value at all | `false` — never missing |
| `isEmpty(collection)` | Whether the record carries the collection and it holds no elements | `false` — never missing |

`abs` accepts a field, an aggregate, an arithmetic expression or a variable, and preserves integer
and decimal precision:

```
abs(sum(transactions.amount)) > 1000
abs($netBalance) >= 500
```

`daysBetween` accepts `date` and `date_time` fields as well as an ISO-8601 date literal. A
`date_time` is read at calendar-day precision, so the time of day never adds a day. The result is
signed — a second operand that comes first is negative — and is valid in arithmetic and numeric
comparisons.

```
daysBetween(customer.registeredAt, application.submittedAt) >= 90
daysBetween(registeredAt, "2024-04-01") / 30 >= 3
```

`isAvailable` is the one function that consumes a missing value instead of passing it on. Everything
else here answers "missing" for a missing input, which is what leaves a comparison over it undecided;
`isAvailable` answers a plain boolean, so it can guard a condition that would otherwise be undecided:

```
isAvailable(reports.balances)
not isAvailable($turnover)
isAvailable(amount) and amount >= 1000
```

It accepts a field, a nested path, a whole `object` or `collection`, an aggregate or a variable. An
**empty** collection is not available: an absent collection and an empty one are the same answer to
*does the record carry this at all*, so use `count(path) == 0` to test for emptiness. Missing data and the `not_exists` branch are covered in
[rules.md](rules.md#missing-data--the-not_exists-branch).

## 5b. Slicing — `take` and `takeLast`

`take(path, n)` keeps at most the first `n` elements in source order; `takeLast(path, n)` keeps at
most the last `n`. `n` is a non-negative whole number. A shorter or empty collection yields what it
has, without failing.

A slice is part of the path, so projection, filtering and aggregation continue from it:

```
sum(take(orders, 3).total) > 5000
count(takeLast(loginEvents, 10)[successful == false]) >= 3
```

**Order matters.** Slicing happens where it is written:

| Expression | Means |
|---|---|
| `takeLast(events, 10)[failed == true]` | failures **among the last ten events** |
| `takeLast(events[failed == true], 10)` | **the last ten failures** |

## 5c. Ordering — `sortBy`

`sortBy` puts a collection in order, so a rule can ask for *the largest*, *the most recent* or
*the first alphabetically* instead of *whatever arrived first*.

```
sortBy(path, asc|desc)                 # a set of values, or a collection of values
sortBy(path, "member", asc|desc)       # a collection of objects, ordered by one member
```

The direction is required, and a member name is always quoted. Like a slice, an ordering is part of
the path, so projection, filtering and aggregation continue from it:

```
sum(take(sortBy(orders, "total", desc), 3).total) > 5000
count(sortBy(loginEvents, "at", desc)) > 0
sortBy(tags, asc) contains "billing"
```

**What it orders by.** The member named, or the elements themselves in the two-argument form.
Numbers compare numerically, dates chronologically, text alphabetically, and `false` before `true`.
A collection holding more than one kind of value is still ordered predictably: values group by kind —
numbers, then dates, then text, then booleans — rather than coming out in an arbitrary order.

**Ties keep their source order**, which is what makes a `take` after an ordering deterministic.

**Elements with nothing to order by go last, in both directions.** An absent member, a `null`, a
nested object and a nested list all mean "no value" rather than "the smallest value" — so
`take(sortBy(orders, "total", desc), 3)` gives the three largest orders, never three orders that
never carried a total.

**Order matters.** Ordering happens where it is written:

| Expression | Means |
|---|---|
| `take(sortBy(orders, "total", desc), 3)` | **the three largest** orders |
| `sortBy(take(orders, 3), "total", desc)` | **the first three** orders, put in order |
| `sortBy(orders[status == "paid"], "total", desc)` | paid orders, largest first |
| `sortBy(orders, "total", desc)[status == "paid"]` | the same set — a filter after an ordering does not reorder it |

**What it accepts.** A `collection` or a `string_set`. A collection whose elements are objects needs
the member name; a `string_set` and a collection of plain values must not be given one. Ordering a
single value, an `object`, or a member that is itself a collection or object is rejected at
validation.

## 5d. Membership — `in`

`element in source` is true when the source holds a value equal to the element. The source may be a
`string_set` field, a projection across a collection, or a list variable.

```
sum(invoices[customerId in priorityCustomerIds].amount) > 10000
count(events[eventType in $importantEventTypes]) > 0
```

- Both sides are matched under the normalizers declared on the fields.
- An **empty or missing source selects nothing**.
- Membership composes with other filters on the same collection.
- A literal list — `country in ["de", "at"]` — is a plain field comparison, not a value expression,
  and keeps enforcing the field's declared `operators:` list.

## 5e. Collection Predicates — `every` and `any`

`every(collection[condition])` holds when every element satisfies the condition; `any(...)` when at
least one does. Both stop as soon as the answer is decided.

```
every(lineItems[quantity >= 1])
any(alerts[severity == "high"])
```

| Collection | `every` | `any` |
|---|---|---|
| some elements | true when all satisfy | true when one satisfies |
| empty | **true** | **false** |
| missing | **true** | **false** |

Both are boolean conditions, so they combine with `and`, `or` and `not`, and work over raw,
filtered, sliced and joined collections.

## 5f. Keyed Joins — `sumByKey`

`sumByKey(key, source, source, ...)` aligns two or more collections on a shared member and returns
one total per key. The first argument is the key member's name as a string literal; each source is
`<collection>.<numericMember>`.

```
min(sumByKey("month", salesByMonth.amount, refundsByMonth.amount)) >= 0
```

- **Outer join** — every key any source mentions appears; a source not mentioning it contributes `0`.
- **Duplicate keys** within one source are summed, so the overall total is preserved.
- **Key order** is first-seen, reading the sources left to right.
- The result is a list of numbers, so `min`, `max`, `sum`, `count` and `every` apply to it.

---

## 6. Array Projections

A dot-separated path navigates into nested objects and projects a field from each element of a list.

Given input:

```json
{
  "transactions": [
    { "amount": 100.00, "label": "normal" },
    { "amount":  90.00, "label": "risk"   }
  ]
}
```

The path `transactions.amount` resolves to `[100.00, 90.00]`.

```
sum(transactions.amount)        → 190.00
count(transactions)             → 2
avg(transactions.amount)        → 95.00
```

Paths may be **any depth**, following the nested `fields:` declared in the
[field schema](./field-schema.md#nested-data):

```
sum(orders.items.price)
count(orders[customer.country == "DE"])
```

A path that stays inside `object` records has a single value, so it does not need an aggregate at all — it
works as a plain condition with any operator (see
[A path into an `object`](./field-schema.md#a-path-into-an-object-behaves-like-any-other-field)). Aggregates
are for paths that read through a `collection`.

### Projection flattens every level

`sum(orders.items.price)` is the sum across **all items of all orders** — not a total per order. Each
level is flattened into a single list of values before the function runs.

There is no grouping construct in the engine. If a rule needs a per-parent figure, the input data has
to provide it as a field.

---

## 7. Filtered Array Paths

A filter expression inside `[...]` selects only the elements that match a condition before projection or aggregation.

```
transactions[label == "risk"]
transactions[amount > 0]
transactions[label == "risk"].amount
```

Field names inside `[...]` refer to fields of the **array element**, not the top-level object.

**Every segment of a path may carry its own filter**, so a nested collection can be narrowed at each
level:

```
orders[status == "paid"].items[price > 0].price
```

### What a filter may contain

A filter is a single comparison — narrower than a normal condition:

| Allowed | Not allowed |
|---|---|
| `==`, `!=`, `>`, `>=`, `<`, `<=`, `in`, `contains` | `between`, `startsWith`, `endsWith`, `regex`, `containsAny`, `containsAll` |
| named `equals`, `gt`, `gte`, `lt`, `lte` | `ignoreCase` |
| `and`, `or`, `not` between predicates | |

Both sides of a predicate are full value expressions, so a filter may hold whatever a comparison may:

```
orders[count(items) > 2]              # an aggregate over the element's own collection
orders[total * 2 > 100]               # arithmetic
orders[items[price > 0].sku == "x"]   # a path that filters again
orders[total > sum(items.price)]      # a computed right-hand side
orders[origin.hub == "HAM"]           # a member reached through a nested object
orders[status in ["paid", "sent"]]    # a written-out list
orders[customerId in priorityIds]     # a document-level field as the source
```

Two points worth knowing:

- A predicate names members of the **element**, with the document's own fields behind them — so
  `invoices[customerId in priorityCustomerIds]` compares a member against a document field. A member
  shadows a document field of the same name.
- `and`, `or` and `not` combine predicates over the *same* element. Chaining filters —
  `transactions[label == "risk"][amount > 100]` — also works and means the same thing as `and`, but
  only `and`: there is no chained spelling of `or`.

The operators in the right-hand column are rejected at validation, and again when the rules are
compiled, so they surface at load time rather than at runtime.

### Examples

```
count(transactions[label == "risk"]) > 0
sum(transactions[label == "risk"].amount) > 500
max(transactions[amount > 0].amount) > 1000
```

---

## 8. Percentage-Style Examples

A common pattern is to check whether a subset of transactions exceeds a percentage of the total.

**Flag if risk transactions exceed 3 % of positive-amount total:**

```
rule "risk-ratio" {
  when
    sum(transactions[label == "risk"].amount) > sum(transactions[amount > 0].amount) * 0.03

  then
    flag "high_risk_ratio"
}
```

**Flag if more than half of transactions are labelled risk:**

```
rule "majority-risk" {
  when
    count(transactions[label == "risk"]) > count(transactions) * 0.5

  then
    flag "majority_risk"
}
```

---

## 9. Empty and Missing Collections

A collection the record **does not carry** and one it carries **holding no elements** are different
inputs, and the functions answer them differently.

| Function | `x` empty | `x` missing |
|---|---|---|
| `count` | `0` | missing |
| `sum` | `0` | missing |
| `subtract` | `0` | missing |
| `avg`, `median`, `max`, `min` | missing | missing |
| `every` | `true` — no element fails | missing |
| `any` | `false` — no element succeeds | missing |
| `isAvailable` | `false` | `false` |
| `isEmpty` | `true` | `false` |
| `take` / `takeLast` | empty slice | missing |
| `sortBy` | empty result | missing |

The empty column is the operation's identity where it has one. `avg`, `median`, `max` and `min` have
none, so they produce no value.

The missing column is one rule:

> A missing value propagates as undecided through every expression. `isAvailable()` and `isEmpty()`
> are the only two that consume it and answer a plain boolean.

A comparison with a missing operand is undecided, so the rule takes its `not_exists` branch if it
declares one and its `else` branch otherwise — which is what a `false` did, so a rule set that never
used `not_exists` is unaffected.
| `sumByKey` | no keys | `count(...) == 0`; `min`/`max` are missing |

A missing value on either side of a comparison leaves the comparison **undecided**. For a rule with no
`not_exists` branch that reads as `false`, exactly as it always did; a rule that declares the branch
takes it instead. See [Missing data](rules.md#missing-data--the-not_exists-branch).

`every` and `any` are the deliberate exceptions: they answer about the elements, and an empty
collection has none to fail or to satisfy the condition.

---

## 10. Performance Notes

- Aggregate expressions are **cached per evaluation**: if the same expression (e.g. `sum(transactions.amount)`) appears multiple times in a rule, it is computed only once.
- Filtered paths iterate the array once per filter, at each level of the path; combining multiple filtered aggregates in one rule is efficient.
- For very large arrays (tens of thousands of elements), prefer pre-aggregated fields in the input data when latency is critical.

---

## 11. Invalid Examples and Validation Messages

| Invalid rule | Validation message |
|---|---|
| `sum(amount) > 0` where `amount` is a scalar | Argument to `sum` must resolve to an array |
| `avg(transactions.label) > 0` where `label` is text | `avg` requires numeric elements |
| `count(transactions, extra) > 0` | `count` requires exactly 1 argument |
| `unknownFn(transactions) > 0` | Unknown aggregate function `unknownFn` |
| `sum(transactions.amount) contains "x"` | Operator `contains` is not valid for numeric comparison |
| `sum(transactions.amount) > "text"` | Right-hand side must be numeric |
| `sum(orders.totl)` where `orders` declares `total` | `Unknown field 'totl' in 'orders.totl'` |
| `transactions[label between 1 5]` | `Operator 'between' is not supported in filter segments` |
| `transactions[label == "risk" ignoreCase]` | `The 'ignoreCase' modifier is not supported in filter segments` |
| `transactions[bogus > 1]` where the element declares no `bogus` | `Unknown field 'bogus' in filter on 'transactions'` |
| `transactions.amount > 100` as a plain condition | `Field 'transactions.amount' reads through collection 'transactions' …` — wrap it in an aggregate or a filter |

> **One case is a warning, not an error:** if the **root** of a multi-segment path is not declared in
> the schema, the rule still loads and a warning is reported, because the root may be a structure read
> straight from the input data. `sum(unknownThing.amount) > 1` is accepted with a warning, while a
> single-segment `unknownThing > 1` is an error. Declaring the collection in the schema turns this into
> real checking.
