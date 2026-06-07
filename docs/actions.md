# Action Schema

The **Action Schema** defines the set of actions that rules are allowed to produce.
An action is the *output* of a rule — it describes what should happen when a rule's conditions are met.

Actions do **not** execute anything directly.
The engine simply returns a list of matched rules and their actions; it is up to the consuming application to decide what to do with them (e.g. store a label, trigger a notification, reject a request).

---

## What Is an Action?

An action has:
- a **name** — a short identifier such as `label`, `flag`, `score`, or `alert`
- one **argument** — a fixed value provided in the rule (e.g. the string `"rent"` or the number `100`)

When a rule matches, all actions declared in its `then` block are included in the result.

### Example

Rule:
```
rule "vip-customer" {
  when
    tags containsAny ["vip", "premium"]

  then
    label "vip"
    score 10
}
```

Result when the rule matches:
```json
{
  "ruleId": "vip-customer",
  "actions": [
    { "name": "label", "arguments": ["vip"] },
    { "name": "score", "arguments": [10] }
  ]
}
```

---

## File Format

The action schema is a YAML file.
The top-level key is `actions`, which contains one entry per action.

```yaml
actions:
  actionName:
    argTypes: [<type>]
```

Each action can accept **exactly one argument** of a declared type.

### Argument Types

| Type | Accepted values |
|---|---|
| `string` | Any text value in double quotes |
| `integer` | A whole number |
| `decimal` | A number with decimal places |

---

## Full Example

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

---

## How Actions Are Used in Rules

Actions are placed in the `then` block of a rule.
Each action is written as:

```
actionName "argument value"
```

or for numeric actions:

```
actionName 42
```

A rule can have **multiple actions**:

```
rule "fraud-detected" {
  when
    tags containsAny ["blocked", "sanctioned"]

  then
    flag "compliance"
    alert "flagged-customer-transaction"
    reject "aml-block"
}
```

This produces three actions when the rule fires:
1. `flag "compliance"`
2. `alert "flagged-customer-transaction"`
3. `reject "aml-block"`

---

## Validation

The engine validates actions at load time:
- **Unknown actions** are rejected. If a rule uses `notify` but the action schema does not define `notify`, loading fails with a clear error.
- **Wrong argument type** is rejected. If `score` expects an `integer` but a rule says `score "high"`, that is a validation error.
- **Wrong number of arguments** is rejected. Each action takes exactly one argument.

This means mistakes are caught before any rule runs — not silently at runtime.

---

## Commonly Used Actions

The following actions are used by convention across most projects.
You can define any actions your project needs, but these names are widely recognised:

| Action | Argument type | Purpose |
|---|---|---|
| `label` | `string` | Assign a classification label (e.g. `"rent"`, `"salary"`) |
| `category` | `string` | Assign a broader category (e.g. `"housing"`, `"income"`) |
| `flag` | `string` | Mark for review or attention (e.g. `"review"`, `"compliance"`) |
| `score` | `integer` | Contribute a numeric score (e.g. risk score) |
| `alert` | `string` | Trigger an alert with a named reason |
| `reject` | `string` | Signal that the item should be rejected, with a reason |
| `notify` | `string` | Trigger a notification |

---

## Tips and Best Practices

- **Define all actions explicitly** — even if a rule does not validate actions by default, having a schema makes intent clear and enables validation.
- **Use consistent naming conventions** — for example, always use lowercase hyphenated identifiers for action argument values: `"high-risk"`, `"direct-debit"`, `"aml-block"`.
- **Keep action arguments as identifiers, not sentences** — the consuming application will look up or map these values; keep them short and stable.
- **Separate concerns** — use different actions for different purposes: `label` for classification, `flag` for alerts, `score` for numeric risk, rather than overloading one action for everything.

