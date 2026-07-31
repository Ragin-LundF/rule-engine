# Rule Engine Documentation

Welcome to the Rule Engine documentation.

---

## For Rule Authors (Data Scientists, Business Analysts, Project Managers)

These guides explain how to write and manage rules without any programming knowledge.

| Document                          | What you will learn                                               |
|-----------------------------------|-------------------------------------------------------------------|
| [Introduction](./introduction.md) | What the rule engine is, how it works, and the four core concepts |
| [Field Schema](./field-schema.md) | How to define the data fields that rules operate on               |
| [Action Schema](./actions.md)     | How to define the actions rules can produce                       |
| [Rules](./rules.md)               | How to write conditions, combine them, and define outcomes        |
| [Value Expressions](./expressions.md) | Aggregates, arithmetic and filters over nested lists of records |
| [Manifest](./manifest.md)         | How to organise multiple rule files into a project                |

**Recommended reading order:** Introduction → Field Schema → Action Schema → Rules → Value Expressions → Manifest

---

## For Developers

This guide is for engineers integrating the rule engine into an application as a library dependency.

| Document                                    | What you will learn                                                                                     |
|---------------------------------------------|---------------------------------------------------------------------------------------------------------|
| [Integration Guide](./integration-guide.md) | Adding the dependency, loading schemas and rules, evaluating data, error handling, extending the engine |

