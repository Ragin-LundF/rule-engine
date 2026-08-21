# Action Schema

The **Action Schema** defines the set of actions that rules are allowed to produce.
An action is the *output* of a rule — it describes what should happen when a rule's conditions are met.

Actions do **not** execute anything directly.
The engine simply returns a list of matched rules and their actions; it is up to the consuming application to decide what to do with them (e.g. store a label, trigger a notification, reject a request).

---

## What Is an Action?

An action has:
- a **name** — a short identifier such as `label`, `flag`, `score`, or `alert`
- at most one **argument** — a fixed value provided in the rule (e.g. the string `"rent"` or the number `100`). An action can also take no argument at all, when the name alone carries the meaning.

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
    argTypes: [<type>]      # one type, or [] for an action that takes no argument
```

Some names are not available: `else`, `stop` and `add` are rule keywords, and the engine rejects an
action schema that declares one rather than letting the rule file fail to parse. Rename it — `add`
becomes `append`, for instance.

Each action accepts **at most one argument** of a declared type. Use an empty list (`argTypes: []`) for
an action that is just a signal.

### Argument Types

| Type              | Accepted values                                        |
|-------------------|--------------------------------------------------------|
| `string`          | Any text value in double quotes                        |
| `integer`         | A whole number                                         |
| `decimal`         | A number with decimal places                           |
| `variable_string` | A `$name` reference to a variable published with `set`  |
| `variable_list`   | A `$name` reference to a list built with `add`          |
| *(none)*          | `argTypes: []` — no argument                           |

### Variable arguments

Passing a variable to an action needs no declaration and never has: `label $why` is accepted for an
action declared `argTypes: [string]`, and the engine does not look at what the variable holds.

Declaring `variable_string` or `variable_list` states that the argument **is** a variable reference. That
is worth doing when it is one by design, because it turns an unchecked value into a checked contract and
gives the editor something to offer:

```yaml
actions:
  reason:
    argTypes: [variable_string]
  topics:
    argTypes: [variable_list]
```

```
then
  set why = "amount-too-low"
  add "billing" to topics
  reason $why       # ok
  topics $topics    # ok
  reason $topics    # error — $topics is written with `add`, not `set`
  reason "text"     # error — a literal where a reference is declared
```

Nothing about how a rule is written changes. What changes is what the engine reports and what the
visual editor offers: an argument declared `variable_list` is edited with a dropdown of the list
variables in scope rather than a free-text box, and the code editor completes only the variables of the
declared kind.

A `variable_list` argument arrives at the consuming application as a list. A variable that no rule which
ran published arrives as `null`, the same as any other unset variable argument.

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

or, for an action declared with `argTypes: []`, the bare name on its own:

```
suppress
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
- **Wrong number of arguments** is rejected. An action declared with one `argTypes` entry must be given exactly one argument; an action declared `argTypes: []` must be given none.
- **A declared variable argument is checked both ways.** A literal where `variable_string` or `variable_list` is declared is rejected, and so is a variable written with the other clause — `add` where `variable_string` was declared, or `set` where `variable_list` was.

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
| `suppress` | *(none)* | Drop the record from downstream processing |

---

## Tips and Best Practices

- **Define all actions explicitly** — even if a rule does not validate actions by default, having a schema makes intent clear and enables validation.
- **Use consistent naming conventions** — for example, always use lowercase hyphenated identifiers for action argument values: `"high-risk"`, `"direct-debit"`, `"aml-block"`.
- **Keep action arguments as identifiers, not sentences** — the consuming application will look up or map these values; keep them short and stable.
- **Separate concerns** — use different actions for different purposes: `label` for classification, `flag` for alerts, `score` for numeric risk, rather than overloading one action for everything.

