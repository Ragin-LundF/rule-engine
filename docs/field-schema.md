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
    normalizers:
      - <normalizer>
    operators:
      - <operator>
```

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
| `date` | Dates | Dates (future use) |

> **Aliases accepted:** `text` can also be written as `string`. `integer` as `int` or `long`. `decimal` as `number` or `bigdecimal`. `string_set` as `stringset` or `set`. `boolean` as `bool`.

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

> Symbolic operators `>`, `>=`, `<`, `<=`, `==` can be used in rules instead of the word forms.

### String Set Field Operators

| Operator | Meaning | Example rule condition |
|---|---|---|
| `containsAny` | At least one of the listed values is in the set | `tags containsAny ["vip", "premium"]` |
| `containsAll` | All of the listed values are in the set | `tags containsAll ["verified", "active"]` |

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
- **Keep field names consistent** across all your schemas if multiple rule sets share the same data model — this makes rules more portable.

