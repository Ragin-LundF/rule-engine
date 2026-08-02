# Field Schema

The **Field Schema** tells the rule engine which data fields exist, what type of values they hold, how they should be cleaned up before comparison (normalizers), and which comparison operations are allowed on them.

Every rule references fields from the schema.
The engine validates all rules against the schema at load time, so typos or wrong operators are caught immediately.

---

## File Format

The schema is a YAML file.
The top-level `schema` key gives the schema a name (useful for versioning), and `fields` lists every field.

```yaml
schema: my-schema-v1

fields:
  fieldName:
    type: <type>
    alias: <optional_alias> # Must be unique across all fields. Used to simplify rule readability.

    normalizers:
      - <normalizer>
    operators:
      - <operator>
```

---

## Aliases

Field aliases allow you to define a user-friendly name for a field that is different from its technical implementation name. This is particularly useful for improving the readability of rules written by non-technical analysts.

### Usage and Constraints

- **Uniqueness:** Every `alias` must be unique across the entire schema. The engine will fail to load if two different fields share the same alias.
- **Readability:** Use aliases to map complex or technical field names (e.g., `sepa_transaction_amount_decimal`) to something simple and intuitive (e.g., `amount`).

Example:

```yaml
fields:
  sepa_transaction_amount_decimal:
    type: decimal
    alias: amount
    operators:
      - gt
```

In a rule, you would use the alias: `amount gt 100`.

---

## Field Types

Each field has exactly one type. The type determines which operators can be used and how values are compared.

| Type | Accepted values | Use for |
|---|---|---|
| `text` | Strings | Free-text fields like descriptions, names, codes |
| `integer` | Whole numbers | Counts, years, scores |
| `decimal` | Numbers with decimals | Amounts, prices, percentages |
| `boolean` | `true` / `false` | Flags, yes/no fields |
| `string_set` | A set of strings | Tags, labels, categories — multiple values per field |
| `date` | ISO dates (`2024-06-15`), or the field's own [format](#date-formats) | Booking dates, due dates, timestamps compared by day |
| `date_time` | ISO date-times (`2024-06-15T09:30:00`), or the field's own [format](#date-formats) | Timestamps where the time of day matters |
| `collection` | A list of records | Transactions, order items, positions — see [Nested Data](#nested-data) |
| `object` | A nested record | Customer, address, counterparty — see [Nested Data](#nested-data) |

> **Aliases accepted:** `text` can also be written as `string`. `integer` can be written as `int` or `long`. `decimal` can be written as `number` or `bigdecimal`. `string_set` can also be written as `stringset` or `set`. `boolean` can be written as `bool`. `collection` can be written as `list` or `array`, and `object` as `map`.
`date_time` can be written as `datetime` or `timestamp`.

> **Note:** A `date` value is compared as a calendar date. Input may be a string, a `LocalDate`, a
> `LocalDateTime`, or an `Instant` — anything carrying a time is reduced to its date. Use `date_time`
> when the time of day must take part in the comparison: there, a `LocalDate` input starts at midnight
> and an `Instant` is resolved at UTC. A string input is read with the field's declared
> [format](#date-formats), or as ISO-8601 when it declares none.

---

## Normalizers

Normalizers are **pre-processing steps** applied to text field values before any comparison happens.
They run once when the input data is prepared — not once per rule — so there is no performance penalty for adding them.

The normalizers run **in the order they are listed**.

### Available Normalizers

| Name | What it does | Example |
|---|---|---|
| `trim` | Removes leading and trailing whitespace | `"  hello  "` → `"hello"` |
| `lowercase` | Converts all letters to lowercase | `"HELLO"` → `"hello"` |
| `uppercase` | Converts all letters to uppercase | `"hello"` → `"HELLO"` |
| `collapse_whitespace` | Replaces multiple consecutive spaces with a single space | `"a  b"` → `"a b"` |
| `remove_punctuation` | Removes all punctuation characters | `"hello, world!"` → `"hello world"` |
| `german_umlaut_fold` | Replaces German umlauts with ASCII equivalents | `"Müller"` → `"Mueller"`, `"ß"` → `"ss"` |

### Normalizers and Rule Comparisons

When you use a normalizer, the **comparison value in the rule is also normalised automatically**.

For example, if a field has `lowercase` as a normalizer and a rule says:
```
purpose contains "RENT"
```
The engine normalises `"RENT"` to `"rent"` during compilation, and the input value is also lowercased at evaluation time.
This means rule authors do not need to think about case — they can write rules naturally.

### Common Normalizer Combinations

**German text (recommended):**
```yaml
normalizers:
  - trim
  - lowercase
  - german_umlaut_fold
```
Input `"Miete für Wohnung"` becomes `"miete fuer wohnung"`.

**Identifier / code fields:**
```yaml
normalizers:
  - trim
  - uppercase
```
Input `" pmnt "` becomes `"PMNT"`.

**Plain text with whitespace cleanup:**
```yaml
normalizers:
  - trim
  - lowercase
  - collapse_whitespace
```

---

## Operators

The `operators` list controls which comparison operations are allowed in rules for that field.
If you omit the `operators` list entirely, all operators that match the field type are allowed.
If you list specific operators, only those are permitted — the validator will reject any rule that uses another operator on that field.

Use the exact names from the tables below. An operator name the engine does not implement — `greaterThan`,
`isEmpty` — is rejected when the schema loads. (`starts_with`, `startswith` and `matches` are accepted as
aliases for `startsWith` and `regex` so older schemas keep working, but write the canonical name.)

In the [visual editor](../README_UI.md) the operator chips are filtered to the field's type, so only the
operators listed for that type can be ticked.

### Text Field Operators

| Operator | Meaning | Example rule condition |
|---|---|---|
| `equals` | Exact match (after normalisation) | `country equals "de"` |
| `contains` | The field value contains the given text | `purpose contains "rent"` |
| `startsWith` | The field value starts with the given text | `iban startsWith "DE"` |
| `endsWith` | The field value ends with the given text | `purpose endsWith "GmbH"` |
| `in` | The field value matches one of a list of values | `sepaCode in ["CCRD", "DCRD"]` |
| `regex` | The field value matches a regular expression | `iban regex "^DE[0-9]{20}$"` |

### Numeric Field Operators (Integer and Decimal)

| Operator | Meaning | Example rule condition |
|---|---|---|
| `equals` | Exact numeric equality | `amount equals 0` |
| `gt` | Greater than | `amount gt 1000` |
| `gte` | Greater than or equal | `amount >= 500` |
| `lt` | Less than | `amount lt 0` |
| `lte` | Less than or equal | `amount <= 9999` |
| `between` | Inclusive range check | `amount between 100 5000` |

> Symbolic operators `>`, `>=`, `<`, `<=` can be used in rules instead of the word forms.
> For equality, prefer the word form `equals`: `==` is routed to the value-expression engine, which
> skips the field's declared operator list. Both forms apply the field's normalizers to the literal.
> See [Rules](./rules.md#named-operators-vs-symbolic-operators).

### String Set Field Operators

| Operator | Meaning | Example rule condition |
|---|---|---|
| `containsAny` | At least one of the listed values is in the set | `tags containsAny ["vip", "premium"]` |
| `containsAll` | All of the listed values are in the set | `tags containsAll ["verified", "active"]` |

### Boolean Field Operators

| Operator | Meaning | Example rule condition |
|---|---|---|
| `equals` | The flag has this value | `isActive equals true` |

`equals` is the only operator for a boolean field, and the value is the bare word `true` or `false` — never quoted.

### Date and Date-Time Field Operators

`date` and `date_time` share the same six operators. There is no separate "before" or "after" operator:
`lt` is before, `gt` is after.

| Operator | Meaning | Example rule condition |
|---|---|---|
| `equals` | Same day / same instant | `bookingDate equals "2024-06-15"` |
| `gt` | After | `bookingDate gt "2024-01-01"` |
| `gte` | On or after | `bookingDate >= "2024-01-01"` |
| `lt` | Before | `bookingDate lt "2020-01-01"` |
| `lte` | On or before | `bookingDate <= "2024-12-31"` |
| `between` | Inclusive range | `bookingDate between "2024-01-01" "2024-12-31"` |

Date literals are always quoted. By default they are ISO — `YYYY-MM-DD` for a `date`,
`YYYY-MM-DDTHH:MM:SS` for a `date_time` — and any other spelling is rejected when the rules load. A
field that declares a [format](#date-formats) uses that pattern instead.

Only a `date_time` comparison can see the time of day. `bookedAt gt "2024-06-15T09:00:00"` is false for
a value at exactly 09:00:00 and true one second later; on a `date` field the same input would have been
truncated to `2024-06-15` first.

### Date Formats

Real-world data does not always arrive as ISO-8601. A `date` or `date_time` field can declare the
pattern its values use, with `format:`:

```yaml
schema: invoices-v1

fields:
  issuedOn:
    type: date
  dueDate:
    type: date
    format: "dd.MM.yyyy"
  paidAt:
    type: date_time
    format: "dd.MM.yyyy HH:mm"
```

The value is a [`java.time.format.DateTimeFormatter`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/time/format/DateTimeFormatter.html)
pattern. It is the field's **only** date format, and it applies in both directions:

- the incoming data value is read with it — `dueDate` accepts `"31.01.2024"`,
- every literal written in a rule for that field must use it — `dueDate lt "31.01.2024"`.

A declared format **replaces** ISO rather than extending it, so with the schema above
`dueDate equals "2024-01-31"` is a validation error and the input `"2024-01-31"` does not match. Leave
`format` out whenever the data is ISO — that is the default, and `issuedOn` above needs no declaration.

The schema is rejected at load time when a `format`:

| Problem | Example |
|---|---|
| sits on a field that is not a date | `format` on a `text` field |
| is not a valid pattern | `format: "QQQQQQ"` |
| cannot represent a complete value | `format: "MM-dd"` on a `date` (no year), `format: "yyyy-MM-dd"` on a `date_time` (no time) |
| is empty | `format: ""` — omit the key instead |

A few limits worth knowing:

- **A format only applies to text input.** When the host application hands the engine a value that is
  already a `LocalDate`, `LocalDateTime` or `Instant`, there is no text to parse and the pattern is not
  consulted.
- **A format on a member of a `collection` has no runtime effect.** It is accepted and validated, but a path
  that reads through a list is projected and compared as raw text. A member of an `object` is a normal
  typed field, so its `format` applies — see [Nested Data](#nested-data).
- **Prefer a pattern with separators.** An all-digit pattern such as `yyyyMMdd` works in a hand-written
  rule, but the visual Builder cannot tell such a value from a number and will emit it unquoted.

### Collection and Object Fields

These two types hold structure rather than a value, so they have **no operators of their own**. Rules
navigate into them or aggregate over them instead — see [Nested Data](#nested-data).

---

## Nested Data

Real input data is rarely flat. A record may carry a list of transactions, or a nested customer object.
Declare those with `collection` (a list of records) or `object` (a single record), and list their
members under a nested `fields:` block.

```yaml
schema: orders-v1

fields:

  orders:
    type: collection            # a list of order records
    fields:
      status:
        type: text
      total:
        type: decimal
      customer:
        type: object            # an object inside a collection
        fields:
          country:
            type: text
      items:
        type: collection        # a collection inside a collection
        fields:
          sku:
            type: text
          price:
            type: decimal
```

`fields:` is recursive, so nesting can go as deep as your data does. With the schema above, rules can
navigate into a record and aggregate over a list:

```
count(orders) > 3
sum(orders.total) > 1000
sum(orders[status == "paid"].items.price) > 500
count(orders[customer.country == "DE"]) > 0
```

See [Value Expressions](./expressions.md) for the full set of aggregate functions and filters.

### A path into an `object` behaves like any other field

A member of an `object` is reached with a dotted path and works with **every** operator, its declared
normalizers, and its `format`:

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
            normalizers: [trim, lowercase]
  parcels:
    type: collection
    fields:
      weightKg:
        type: decimal
      damaged:
        type: boolean
```

```
shipment.transitDays <= 2
shipment.pickedUpAt >= "01.03.2026"
shipment.customer.tier equals "gold"
```

A path that reads through a **`collection`** is different: it yields one value per element, so it cannot be
compared to a single literal. Use an aggregate or a filter instead — the engine says so when a rule tries:

```
count(parcels[damaged == true]) > 0      # not: parcels.damaged equals true
sum(parcels.weightKg) > 100              # not: parcels.weightKg > 100
```

### Worked example

[`warehouse-shipments`](../ruleengine-core/src/test/resources/warehouse-shipments) is a complete bundle
using both shapes: a `shipment` object read by plain conditions, plus `parcels` and `checkpoints`
collections read by aggregates and filters. It ships with two input files and is executed by
`WarehouseShipmentsIntegrationTest`, so every path in it is known to work. The same bundle is available in
the UI sample gallery as **Warehouse Shipments**.

### `string_set` or `collection`?

| Your data | Type | Why |
|---|---|---|
| `["vip", "premium"]` | `string_set` | A set of plain strings; rules test membership |
| `[{"amount": 100, "label": "risk"}]` | `collection` | A list of records; rules aggregate over their fields |

### Declaring the members is optional

| Nested `fields:` declared | What you get |
|---|---|
| Yes | Member names are validated, leaf types are known, mistakes are caught when the rules load, and the visual editor offers the members in its dropdowns |
| No | Paths still work at runtime, but nothing below the declared field is checked — a typo like `orders.totl` silently never matches |

Declaring the members is always worth it unless the data shape is genuinely unknown.

---

## Complete Schema Example

```yaml
schema: transaction-full-v1

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

  # IBAN – kept uppercase for format checks
  iban:
    type: text
    alias: iban_code
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
    type: number
    operators:
      - equals
      - gt
      - gte
      - lt
      - lte
      - between

  # Number of transactions in a window
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

## Tips and Best Practices

- **Add normalizers to every text field** — even a simple `trim` avoids unexpected failures caused by stray whitespace in data.
- **Restrict operators explicitly** when you want to prevent analysts from accidentally using an expensive operator (like `regex`) on a high-traffic field.
- **Version your schema name** (e.g. `transaction-v1`, `transaction-v2`) so you can identify which schema version a rule set was written against.
- **Use `string_set` for multi-value fields** like tags, labels, or categories — never store multiple values in a single text field separated by commas.
- **Declare the members of every `collection` and `object`** — it is what turns a silent typo deep in a path into a load-time error, and it is what fills the dropdowns in the visual editor.
- **Keep field names consistent** across all your schemas if multiple rule sets share the same data model — this makes rules more portable.
