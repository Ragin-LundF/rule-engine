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

| Function | Description | Empty array result |
|---|---|---|
| `count(path)` | Number of elements in the collection | `0` (comparison evaluates to `false` unless `== 0`) |
| `sum(path)` | Sum of all numeric values | `0` |
| `subtract(path)` | First element minus all subsequent elements | `0` |
| `avg(path)` | Arithmetic mean of all numeric values | missing (comparison evaluates to `false`) |
| `median(path)` | Median value | missing (comparison evaluates to `false`) |
| `max(path)` | Maximum value | missing (comparison evaluates to `false`) |
| `min(path)` | Minimum value | missing (comparison evaluates to `false`) |

Non-numeric elements in the collection are silently skipped during numeric aggregation.

### Examples

```
count(transactions) > 2
sum(transactions.amount) > 1000
avg(transactions.amount) > 25
max(transactions[label == "risk"].amount) > 500
min(transactions.amount) >= 0
```

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
orders.customer.country
```

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
| `==`, `!=`, `>`, `>=`, `<`, `<=` | `and`, `or`, `not` |
| named `gt`, `gte`, `lt`, `lte` | `equals`, `contains`, `in`, `between`, `regex`, `containsAny`, `containsAll` |

Two points that catch people out:

- Write equality as `==`, never as `equals`. `transactions[label equals "risk"]` is rejected.
- To require two conditions on the same element, chain filters rather than using `and`:
  `transactions[label == "risk"][amount > 100]`.

These are reported when the rules are compiled, so they surface at load time rather than at runtime.

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

## 9. Empty Array Behavior

| Function | Empty array result | Comparison result |
|---|---|---|
| `count` | `0` | Compares normally (`count(...) > 0` → `false`) |
| `sum` | `0` | Compares normally |
| `subtract` | `0` | Compares normally |
| `avg` | missing | Always `false` |
| `median` | missing | Always `false` |
| `max` | missing | Always `false` |
| `min` | missing | Always `false` |

A missing value on either side of a comparison always produces `false`.

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
| `transactions[label == "risk" and amount > 0]` | `Only comparison expressions are supported in filter segments` |
| `transactions[label equals "risk"]` | `Operator 'equals' is not supported in filter segments` |

> **One case is a warning, not an error:** if the **root** of a multi-segment path is not declared in
> the schema, the rule still loads and a warning is reported, because the root may be a structure read
> straight from the input data. `sum(unknownThing.amount) > 1` is accepted with a warning, while a
> single-segment `unknownThing > 1` is an error. Declaring the collection in the schema turns this into
> real checking.
