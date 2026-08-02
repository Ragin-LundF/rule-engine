# Integration Guide

This guide is for **developers** who want to embed the Rule Engine into a JVM application (Java, Kotlin, or any JVM language) as a library dependency.

---

## Table of Contents

1. [Adding the Dependency](#1-adding-the-dependency)
2. [Core Concepts for Developers](#2-core-concepts-for-developers)
3. [Quick Start: RuleEngineBuilder](#3-quick-start-ruleenginebuilder)
4. [Advanced Rule Engine Preparation](#4-advanced-rule-engine-preparation)
   - [Manifest-Based Loading by Hand](#41-manifest-based-loading-by-hand)
   - [Loading a Field Schema](#42-loading-a-field-schema)
   - [Loading an Action Schema](#43-loading-an-action-schema)
   - [Parsing Rules](#44-parsing-rules)
   - [Validating Rules](#45-validating-rules)
   - [Compiling Rules](#46-compiling-rules)
   - [Building the Engine](#47-building-the-engine)
   - [Evaluating Input Data](#48-evaluating-input-data)
   - [Reading the Result](#49-reading-the-result)
5. [Tracing — Decision Tree Output](#5-tracing--decision-tree-output)
6. [Loading from Strings and Readers](#6-loading-from-strings-and-readers)
7. [Thread Safety and Lifecycle](#7-thread-safety-and-lifecycle)
8. [Error Handling](#8-error-handling)
9. [Extending the Engine](#9-extending-the-engine)
   - [Custom Normalizers](#91-custom-normalizers)
10. [Package Overview](#10-package-overview)

---

## 1. Adding the Dependency

The rule engine is published as a standard JVM library.
Add it to your build file:

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("com.example:ruleengine-core:1.0-SNAPSHOT")
}
```

### Gradle (Groovy DSL)

```groovy
dependencies {
    implementation 'com.example:ruleengine-core:1.0-SNAPSHOT'
}
```

### Maven

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>ruleengine-core</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

> **Note:** Replace the group ID and version with the values published to your organisation's artifact repository.

### Transitive Dependencies

The library requires the following at runtime (they are declared as `implementation` dependencies and will be included transitively):

| Dependency | Purpose |
|---|---|
| `tools.jackson.core:jackson-databind` | JSON / YAML parsing |
| `tools.jackson.dataformat:jackson-dataformat-yaml` | YAML support |
| `tools.jackson.module:jackson-module-kotlin` | Kotlin data class support |

---

## 2. Core Concepts for Developers

The engine lifecycle has two clearly separated phases:

### Load Phase (happens once at startup or reload)

```
FieldSchemaLoader  ──►  FieldSchema
ActionSchemaLoader ──►  ActionSchema
Parser             ──►  List<RuleAst>
Validator          ──►  ValidationResult   (check for errors before proceeding)
Compiler           ──►  List<CompiledRule>
RuleEngine         ──►  ready to evaluate
```

### Evaluation Phase (happens per input record)

```
RuleContext.of(...)           ──►  RuleContext
PreparedRuleContext.prepare() ──►  PreparedRuleContext   (normalisation applied here)
RuleEngine.evaluate()         ──►  EvaluationResult
```

**Key principle:** parsing, validation, and compilation happen once.
The `RuleEngine` instance is reused for every evaluation — it is stateless and thread-safe after construction.

`RuleEngineBuilder` (see section 3) runs the entire load phase for you; section 4 shows the same
phases driven one component at a time.

---

## 3. Quick Start: RuleEngineBuilder

`RuleEngineBuilder` performs the **whole load phase in one call**: it reads the manifest, resolves
every referenced file relative to the manifest, loads the field and action schema, parses the rule
files in manifest order, validates them and compiles them into a ready engine.

```kotlin
import ruleengine.builder.RuleEngineBuilder
import java.nio.file.Path

// Loads every entry of the manifest, keyed by entry id
val engines = RuleEngineBuilder.fromManifest(manifestPath = Path.of("rules/manifest.yaml"))

val loaded = engines.getValue("transactions")

val result = loaded.evaluate(
    input = mapOf(
        "purpose" to "Rent apartment January",
        "amount" to 750.0,
        "tags" to listOf("regular")
    )
)

for (match in result.matches) {
    println("Rule matched: ${match.ruleId}")
    for (action in match.actions) {
        println("  Action: ${action.name} ${action.arguments}")
    }
}
```

That is the complete integration — no separate loader calls, no manual validation check, and no
second variable holding the schema.

### What you get back

`fromManifest` returns a `Map<String, LoadedRuleEngine>` keyed by manifest entry id. Each
`LoadedRuleEngine` bundles everything belonging to one entry:

- `entryId: String` — the manifest entry it was built from
- `engine: RuleEngine` — the compiled engine
- `schema: FieldSchema` — the schema the rules were compiled against
- `actions: ActionSchema?` — the action schema, or `null` when the entry declares none
- `warnings: List<ValidationDiagnostic>` — non-fatal diagnostics (errors would have failed the build)
- `evaluate(input, includeTrace)` — normalises the input against `schema` and evaluates it

Because the schema travels with the engine, a single object can be passed around, stored as a bean,
or swapped atomically on reload.

### Loading a single entry

Pass `entryId` to build only one entry — the result is then a single-element map, so sibling entries
are never read:

```kotlin
val engines = RuleEngineBuilder.fromManifest(
    manifestPath = Path.of("rules/manifest.yaml"),
    entryId = "transactions"
)
```

`fromManifestEntry` does the same but returns the `LoadedRuleEngine` directly:

```kotlin
val loaded = RuleEngineBuilder.fromManifestEntry(
    manifestPath = Path.of("rules/manifest.yaml"),
    entryId = "transactions"
)
```

### Parameters

| Parameter | Default | Purpose |
|---|---|---|
| `manifestPath` | — | Path to the manifest YAML (or JSON) file |
| `entryId` | `null` | Build only this entry instead of all of them |
| `normalizerRegistry` | `NormalizerRegistry.default` | Normalizer registry used for compilation |

### What is validated

The builder fails fast instead of handing out a half-initialised engine. It raises
`RuleEngineBuildException` (from `ruleengine.core.errors`) when:

- the manifest is missing, unreadable, empty, or declares duplicate entry ids
- `entryId` names an entry that does not exist — the message lists the available ids
- an entry declares no `schema` or no rule files
- a referenced schema, action or rule file does not exist — the message names both the relative path
  from the manifest and the resolved absolute path
- a referenced path escapes the manifest directory (for example `../../etc/passwd`)
- a schema or rule file cannot be loaded or parsed
- rule validation reports an `ERROR`

The exception message states the manifest, the affected entry and the concrete problem, and appends
one line per validation diagnostic, so the full reason is available without a logging framework. The
structured diagnostics remain accessible via `RuleEngineBuildException.diagnostics`:

```kotlin
import ruleengine.core.errors.RuleEngineBuildException

try {
    val engines = RuleEngineBuilder.fromManifest(manifestPath = Path.of("rules/manifest.yaml"))
} catch (e: RuleEngineBuildException) {
    logger.error("Rule engine startup failed: ${e.message}")
    e.diagnostics.forEach { diagnostic -> logger.error("  ${diagnostic.severity}: ${diagnostic.message}") }
    throw e
}
```

Warnings never fail the build; inspect `loaded.warnings` if you want to surface them.

> **Note:** The builder loads from the filesystem. For custom sources (strings, readers, classpath
> resources) or partial pipelines, use the individual components described in section 4.

---

## 4. Advanced Rule Engine Preparation

Use the individual components when `RuleEngineBuilder` does not fit: rules that come from a database
or a classpath resource instead of files, a validation-only tool that never compiles, a custom
assembly of schemas and rule sets, or full control over each phase.

### 4.1 Manifest-Based Loading by Hand

This is what `RuleEngineBuilder.fromManifest` does internally, written out:

```kotlin
import ruleengine.manifest.ManifestLoader
import ruleengine.schema.FieldSchemaLoader
import ruleengine.schema.ActionSchemaLoader
import ruleengine.dsl.parser.Parser
import ruleengine.compiler.Validator
import ruleengine.compiler.Compiler
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.evaluator.RuleEngine
import ruleengine.evaluator.context.RuleContext
import ruleengine.evaluator.context.PreparedRuleContext
import java.nio.file.Path
import java.nio.file.Files

data class ManualEngine(val engine: RuleEngine, val schema: FieldSchema)

fun buildEngine(manifestPath: Path): ManualEngine {
    val manifest = ManifestLoader.load(path = manifestPath)
    val entry = manifest.entries.first()
    val baseDir = manifestPath.parent

    val schema = FieldSchemaLoader.load(path = baseDir.resolve(entry.schema!!))
    val actions = ActionSchemaLoader.load(path = baseDir.resolve(entry.actions!!))

    val ruleAsts = entry.rules.flatMap { relativePath ->
        val rulePath = baseDir.resolve(relativePath)
        Parser(input = Files.readString(rulePath)).parseRules()
    }

    val validation = Validator.validate(asts = ruleAsts, schema = schema, actions = actions)
    check(validation.isValid) {
        "Rule validation failed: ${validation.diagnostics}"
    }

    val compiled = Compiler.compileRules(asts = ruleAsts, schema = schema)
    return ManualEngine(engine = RuleEngine(compiledRules = compiled), schema = schema)
}
```

Note that the schema has to be carried alongside the engine: `RuleEngine` does not hold it, but
`PreparedRuleContext.prepare` needs it for normalisation. Evaluating a single record:

```kotlin
val manual = buildEngine(Path.of("rules/manifest.yaml"))

val result = manual.engine.evaluate(
    prepared = PreparedRuleContext.prepare(
        ctx = RuleContext.of(
            "purpose" to "Rent apartment January",
            "amount" to 750.0,
            "tags" to listOf("regular")
        ),
        schema = manual.schema
    )
)

for (match in result.matches) {
    println("Rule matched: ${match.ruleId}")
    for (action in match.actions) {
        println("  Action: ${action.name} ${action.arguments}")
    }
}
```

> **Note:** Unlike the builder, this hand-written version does not check that referenced paths stay
> inside the manifest directory. Use `ManifestPathResolver.resolveWithinBase` from
> `ruleengine.manifest` when the manifest is not fully under your control.

### 4.2 Loading a Field Schema

Load from a file:

```kotlin
import ruleengine.schema.FieldSchemaLoader
import java.nio.file.Path

val schema = FieldSchemaLoader.load(path = Path.of("schemas/transaction-schema.yaml"))
```

Load from a string (useful in web contexts or tests):

```kotlin
val yamlContent = """
schema: my-schema
fields:
  purpose:
    type: text
    operators:
      - contains
  amount:
    type: decimal
    operators:
      - gte
      - lte
""".trimIndent()

val schema = FieldSchemaLoader.loadFromString(content = yamlContent, nameHint = "my-schema")
```

Load from a `Reader`:

```kotlin
val reader = someInputStream.bufferedReader()
val schema = FieldSchemaLoader.loadFromReader(reader = reader, nameHint = "my-schema")
```

The returned `FieldSchema` contains:
- `schema.name` — the schema name
- `schema.fields` — a `Map<FieldId, FieldDefinition>`, each with `.type`, `.alias`, `.normalizers`, `.operators`, and `.fields`
- `.fields` on a definition holds the nested members of a `COLLECTION` or `OBJECT` field, recursively.
  It is empty for scalar fields, and also empty for a structure whose members were not declared. The
  `FieldType.isStructure` extension property tells the two structure types apart from the rest.

### 4.3 Loading an Action Schema

```kotlin
import ruleengine.schema.ActionSchemaLoader

val actions = ActionSchemaLoader.load(path = Path.of("schemas/actions.yaml"))
// or:
val actions = ActionSchemaLoader.loadFromString(content = yamlString)
val actions = ActionSchemaLoader.loadFromReader(reader = someReader)
```

The returned `ActionSchema` contains:
- `actions.actions` — a `Map<String, ActionDefinition>`, each with `argTypes: List<ActionArgType>`

`argTypes` holds one entry for an action that takes a value, and is **empty** for an action that takes
none (declared as `argTypes: []` and written in a rule as the bare action name).

### 4.4 Parsing Rules

Parse one or more rule files into ASTs:

```kotlin
import ruleengine.dsl.parser.Parser
import java.nio.file.Files
import java.nio.file.Path

val ruleText = Files.readString(Path.of("rules/classification.rule"))
val ruleAsts = Parser(input = ruleText).parseRules()
```

Parse multiple files and combine:

```kotlin
val ruleAsts = listOf(
    Path.of("rules/classification.rule"),
    Path.of("rules/fraud.rule")
).flatMap { path ->
    Parser(input = Files.readString(path)).parseRules()
}
```

Parse a directory recursively:

```kotlin
val ruleAsts = Files.walk(Path.of("rules"))
    .filter { Files.isRegularFile(it) && it.toString().endsWith(".rule") }
    .flatMap { Parser(input = Files.readString(it)).parseRules().stream() }
    .toList()
```

If parsing fails, a `ParseException` is thrown with the line and column of the error.

### 4.5 Validating Rules

```kotlin
import ruleengine.compiler.Validator

val result = Validator.validate(
    asts = ruleAsts,
    schema = schema,
    actions = actions   // optional — omit to skip action validation
)

if (!result.isValid) {
    result.diagnostics.forEach { diagnostic ->
        println("[${diagnostic.severity}] ${diagnostic.message}")
        diagnostic.suggestion?.let { println("  Did you mean: $it") }
    }
    throw IllegalStateException("Rule validation failed")
}
```

The `ValidationResult` contains:
- `isValid: Boolean` — `true` only if there are no `ERROR`-severity diagnostics
- `diagnostics: List<ValidationDiagnostic>` — each with `severity`, `message`, and optional `suggestion`

Severity levels: `ERROR` (blocks loading), `WARNING` (informational).

The validator checks:
- All field names referenced in rules exist in the schema
- Nested paths resolve against the declared members of a `COLLECTION` / `OBJECT` field, one segment at a
  time, to any depth
- Field names inside a path filter (`orders[status == "paid"]`) resolve against the members of the
  element being filtered, not the top-level schema
- All operators used in conditions are allowed for the field's type
- Literal types match the field type (e.g. no string literal on a numeric field, `true` / `false` for
  booleans, and for a date field either ISO or the pattern the field declares in its `format`)
- Valid regular expression patterns for `regex` operator
- All actions are defined in the action schema (if provided)
- Action argument counts and types match the action schema definition
- All rule IDs are unique

Two deliberate asymmetries are worth knowing when you interpret diagnostics:

- An unknown member of a **declared** structure is an `ERROR`; a path below an **undeclared** structure
  is not checked at all.
- An undeclared **root** on a multi-segment path is a `WARNING`, not an error, because the root may be a
  structure read straight from the input data. `sum(unknownThing.amount) > 1` therefore loads, while a
  single-segment `unknownThing > 1` fails.

### 4.6 Compiling Rules

```kotlin
import ruleengine.compiler.Compiler

val compiledRules = Compiler.compileRules(asts = ruleAsts, schema = schema)
```

Compilation:
- Resolves field types and normalizers from the schema
- Pre-normalises literal comparison values (so they match the normalised input at evaluation time)
- Pre-compiles regular expression patterns
- Sorts `AND` children by evaluation cost (cheapest first) for short-circuit optimisation

### 4.7 Building the Engine

```kotlin
import ruleengine.evaluator.RuleEngine

val engine = RuleEngine(compiledRules = compiledRules)
```

The `RuleEngine` instance is **immutable and thread-safe** after construction.
Create it once and reuse it for all evaluations.

#### Evaluation Order

The engine evaluates **every** rule against every input, in the order of the `compiledRules` list —
manifest `rules:` file order, then the order the rules appear inside each file — and `result.matches`
is returned in that same order.

That ordering is a **guarantee**, and two constructs depend on it: a `set` clause publishes a value only
the rules after it can read, and a branch ending in `stop` ends the run at its own position. Build the
list through `RuleEngineBuilder` (or preserve manifest order yourself) and the order is correct by
construction.

### 4.8 Evaluating Input Data

Input data is provided as key-value pairs via `RuleContext`.
The engine accepts any `Map<String, Any?>` — the keys are field names, the values are the field values.

#### Supported value types

| Field type in schema | Expected JVM type |
|---|---|
| `TEXT` | `String` |
| `INTEGER` | `Long`, `Int`, `Short`, `Byte` |
| `DECIMAL` | `BigDecimal`, `Double`, `Float` |
| `BOOLEAN` | `Boolean`, or the `String` `"true"` / `"false"` |
| `STRING_SET` | `List<String>`, `Set<String>`, `Collection<String>` |
| `DATE` | `LocalDate`, `LocalDateTime`, `Instant`, or a `String` in the field's format |
| `DATE_TIME` | `LocalDateTime`, `LocalDate` (starts at midnight), `Instant`, or a `String` in the field's format |
| `COLLECTION` | `List<Map<String, Any?>>` — a list of records |
| `OBJECT` | `Map<String, Any?>` — a single record |

A value that cannot be read as its declared type is treated as absent, which makes conditions on it
`false` rather than raising an error. A `DATE` carrying a time is reduced to its calendar date; a
`DATE_TIME` keeps it. An `Instant` is resolved at UTC, because the engine has no timezone concept.

A `String` date is read with the pattern the field declares in its
[`format`](field-schema.md#date-formats), or as ISO-8601 when it declares none. A value that is already
a `LocalDate`, `LocalDateTime` or `Instant` carries no text, so no pattern applies to it — those types
are always accepted as they are.

```kotlin
import ruleengine.evaluator.context.RuleContext
import ruleengine.evaluator.context.PreparedRuleContext

val context = RuleContext.of(
    "purpose" to "Rent apartment January",
    "amount" to 750.0,
    "sepaCode" to "PMNT",
    "tags" to listOf("regular", "verified")
)

val prepared = PreparedRuleContext.prepare(ctx = context, schema = schema)

val result = engine.evaluate(prepared = prepared)
```

`PreparedRuleContext.prepare()` applies all normalizers from the schema to the input values.
This is the only point where normalisation happens — not once per rule, making evaluation very efficient.

#### Loading input from JSON

```kotlin
import ruleengine.jackson.JacksonUtil

val json = """
{
  "purpose": "Rent apartment January",
  "amount": 750,
  "tags": ["regular"]
}
""".trimIndent()

@Suppress("UNCHECKED_CAST")
val inputMap = JacksonUtil.jsonMapper.readValue(json, Map::class.java) as Map<String, Any?>

val context = RuleContext.of(
    entries = inputMap.entries.map { it.key to it.value }.toTypedArray()
)
val prepared = PreparedRuleContext.prepare(ctx = context, schema = schema)
val result = engine.evaluate(prepared = prepared)
```

### 4.9 Reading the Result

```kotlin
import ruleengine.core.domain.dto.EvaluationResult
import ruleengine.core.domain.dto.RuleBranch
import ruleengine.core.domain.dto.RuleMatch
import ruleengine.core.domain.dto.RuleAction

val result: EvaluationResult = engine.evaluate(prepared = prepared)

// result.matches is a List<RuleMatch> — every rule that produced output, either branch
for (match: RuleMatch in result.matches) {
    val branch = if (match.branch == RuleBranch.THEN) "matched" else "did not match (else)"
    println("Rule ${match.ruleId}: $branch")

    for (action: RuleAction in match.actions) {
        println("  ${action.name}: ${action.arguments}")
    }
}
```

`EvaluationResult`:
- `matches: List<RuleMatch>` — every rule that produced output, in the order they were declared. A rule appears here when
  its condition held, **or** when the condition was false and the rule declares an `else` branch;
  `RuleMatch.branch` says which. For the rules whose condition actually held, filter on
  `RuleBranch.THEN`:
  ```kotlin
  val conditionHeld = result.matches.filter { match -> match.branch == RuleBranch.THEN }
  ```
  A rule without an `else` block can only ever report `RuleBranch.THEN`, so this list means exactly what
  it did before for a rule set that uses no branches.
- `trace: Any?` — a `DecisionTree` if tracing was enabled (see section 5), otherwise `null`
- `variables: Map<String, Any?>` — the final value of every variable a matching rule published with a
  `set` clause, keyed by name without the `$`. Empty for a rule set that uses none.

`RuleMatch`:
- `ruleId: String` — the rule's ID
- `actions: List<RuleAction>` — the actions the branch that ran declared
- `assignments: Map<String, Any?>` — the variables **this** rule published, in assignment order
- `branch: RuleBranch` — `THEN` when the rule's condition held, `ELSE` when it did not and the rule
  declares an `else` block (see [rules.md](rules.md)). Defaults to `THEN`, which is the only value a
  rule without an `else` block can report — so existing code that ignores this field keeps its meaning.
  **A match is not by itself proof that the condition was true.**

`RuleAction`:
- `name: String` — the action name (e.g. `"label"`)
- `arguments: List<Any?>` — the argument values (e.g. `["rent"]` or `[10]`)

#### Reading Variables

Use `result.variables` for the state at the end of the run, and `RuleMatch.assignments` when you need
to know **which** rule produced a value — `variables` only carries the last write when several rules
assign the same name.

```kotlin
val result = engine.evaluate(prepared = prepared)

println("orderTotal = ${result.variables["orderTotal"]}")

for (match in result.matches) {
    for ((name, value) in match.assignments) {
        println("${match.ruleId} set $name = $value")
    }
}
```

Values are plain Kotlin types: `BigDecimal` for numbers, `String` for text, `Boolean` for booleans and
`List<Any?>` for a projected array. A variable no matching rule assigned is simply absent from the map.

See [rules.md](rules.md#variables--the-set-clause) for the DSL side and the ordering rules.

---

## 5. Tracing — Decision Tree Output

The engine can produce a **decision trace** — a tree showing exactly which conditions were evaluated, what the input values were, and whether each condition passed or failed.
This is useful for debugging, auditing, or explaining why a rule matched.

Enable tracing by passing `includeTrace = true`:

```kotlin
val result = engine.evaluate(prepared = prepared, includeTrace = true)
```

The trace is available as `result.trace`, which is a `DecisionTree` object.
You can serialise it to JSON:

```kotlin
import ruleengine.evaluator.trace.dto.DecisionTree
import ruleengine.evaluator.trace.dto.toJson

val tree = result.trace as? DecisionTree
if (tree != null) {
    println(tree.toJson())
}
```

Example JSON output:

```json
{
  "root": {
    "id": "n1",
    "type": "RULE",
    "ruleId": "rent-payment",
    "result": true,
    "evaluationTimeMs": 0,
    "children": [
      {
        "id": "n2",
        "type": "AND",
        "result": true,
        "children": [
          {
            "id": "n3",
            "type": "CONDITION",
            "field": "purpose",
            "operator": "contains",
            "expected": "rent",
            "result": true
          },
          {
            "id": "n4",
            "type": "CONDITION",
            "field": "amount",
            "operator": "gte",
            "expected": 500,
            "result": true
          }
        ]
      }
    ]
  },
  "matchedRules": ["rent-payment"]
}
```

`DecisionTree`:
- `root: DecisionNode?` — the root node of the evaluation tree
- `matchedRules: List<String>` — IDs of the rules whose condition held. A rule whose `else` branch fired
  is **not** listed here: the trace answers "did the condition hold", which the `result` flag on that
  rule's own node also reports. Read `EvaluationResult.matches` for what the run produced.

`DecisionNode`:
- `id` — unique node identifier within the trace
- `type` — one of `EVALUATION` (the synthetic root), `RULE`, `AND`, `OR`, `NOT`, `CONDITION`
- `field` / `operator` / `expected` — present on `CONDITION` nodes. For a condition whose operand is
  an expression (an aggregate, arithmetic, or another field), `field` is that operand rendered back
  to DSL text — e.g. `count(orders[status equals "paid"])` — and `expected` is the *evaluated* right
  operand, so a comparison against another field shows the concrete value it was measured against
- `actual: Any?` — the value actually found. Present on aggregate, arithmetic and field-to-field
  conditions; omitted from the JSON on nodes that do not report one
- `result: Boolean` — whether this node evaluated to `true`
- `evaluationTimeMs: Long?` — how long this node took to evaluate
- `ruleId: String?` — present on `RULE` nodes
- `children: List<DecisionNode>` — child nodes

A filter predicate inside a path (`orders[status equals "paid"]`) is evaluated once per element and
is deliberately **not** traced; the enclosing comparison contributes a single node regardless of how
many elements the collection holds.

---

## 6. Loading from Strings and Readers

All loader classes (`FieldSchemaLoader`, `ActionSchemaLoader`, `ManifestLoader`) support loading from `String`, `Reader`, or file `Path`:

```kotlin
// FieldSchemaLoader
FieldSchemaLoader.load(path = Path.of("schema.yaml"))
FieldSchemaLoader.loadFromString(content = yamlString, nameHint = "my-schema")
FieldSchemaLoader.loadFromReader(reader = reader, nameHint = "my-schema")

// ActionSchemaLoader
ActionSchemaLoader.load(path = Path.of("actions.yaml"))
ActionSchemaLoader.loadFromString(content = yamlString)
ActionSchemaLoader.loadFromReader(reader = reader)

// ManifestLoader
ManifestLoader.load(path = Path.of("manifest.yaml"))
ManifestLoader.loadFromString(content = yamlString)
```

All loaders accept both YAML and JSON content.

> **Note:** `RuleEngineBuilder` reads from the filesystem only. Content that lives in a database, a
> classpath resource or memory has to go through these loaders — see section 4.

---

## 7. Thread Safety and Lifecycle

| Object | Thread-safe? | Recommended lifetime |
|---|---|---|
| `RuleEngineBuilder` | ✅ (stateless `object`) | Call it from anywhere, including concurrently |
| `FieldSchema` | ✅ (immutable) | Application lifetime / per rule reload |
| `ActionSchema` | ✅ (immutable) | Application lifetime / per rule reload |
| `List<CompiledRule>` | ✅ (immutable) | Application lifetime / per rule reload |
| `RuleEngine` | ✅ (stateless) | Application lifetime — create once, reuse |
| `LoadedRuleEngine` | ✅ (immutable) | Application lifetime / per rule reload |
| `RuleContext` | ❌ (per-call) | Per evaluation |
| `PreparedRuleContext` | ❌ (per-call) | Per evaluation |

> **`set` variables are safe under concurrency.** They look like shared state, but every
> `LoadedRuleEngine.evaluate` call builds its own `PreparedRuleContext` and with it its own variable
> map, so two threads evaluating the same engine cannot see each other's variables. The unsafe
> pattern is hoisting a `PreparedRuleContext` and sharing that — its variable map and aggregate cache
> are written on every evaluation. See [Performance](./performance.md#5-thread-safety).

### Hot Reload Pattern

To support rule updates without restarting the application, keep the `LoadedRuleEngine` in an
`AtomicReference` and swap it after a successful rebuild. Because the builder throws on any problem,
a failed reload leaves the previous engine in place:

```kotlin
import ruleengine.builder.LoadedRuleEngine
import ruleengine.builder.RuleEngineBuilder
import ruleengine.core.domain.dto.EvaluationResult
import ruleengine.core.errors.RuleEngineBuildException
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

class RuleEngineService(private val manifestPath: Path, private val entryId: String) {
    private val engineRef: AtomicReference<LoadedRuleEngine> = AtomicReference(build())

    private fun build(): LoadedRuleEngine =
        RuleEngineBuilder.fromManifestEntry(manifestPath = manifestPath, entryId = entryId)

    /** Returns true when the new rules were applied; the old engine stays active otherwise. */
    fun reload(): Boolean =
        runCatching { build() }.fold(
            onSuccess = { reloaded -> engineRef.set(reloaded); true },
            onFailure = { failure ->
                if (failure !is RuleEngineBuildException) throw failure
                logger.error("Rule reload rejected, keeping the current rules: ${failure.message}")
                false
            }
        )

    fun evaluate(input: Map<String, Any?>): EvaluationResult =
        engineRef.get().evaluate(input = input)
}
```

---

## 8. Error Handling

The engine uses typed exceptions for different failure modes:

| Exception | When thrown | Package |
|---|---|---|
| `RuleEngineBuildException` | `RuleEngineBuilder` cannot build an engine from a manifest | `ruleengine.core.errors` |
| `SchemaLoadException` | A schema YAML file cannot be read or is invalid | `ruleengine.core.errors` |
| `ParseException` | A `.rule` file contains a syntax error | `ruleengine.dsl.diagnostics` |
| `CompilationException` | A rule passes validation but cannot be compiled | `ruleengine.core.errors` |
| `InputTooLargeException` | A manifest, schema, rule or input file exceeds the 25 MB read limit | `ruleengine.core.errors` |

All exceptions extend `RuleEngineException`. When `RuleEngineBuilder` is used, every load-phase
failure surfaces as a `RuleEngineBuildException` that keeps the original failure as its `cause` and
exposes rule diagnostics via `diagnostics`, so a single catch block covers the whole load phase.

```kotlin
import ruleengine.core.errors.SchemaLoadException
import ruleengine.dsl.diagnostics.ParseException
import ruleengine.core.errors.RuleEngineException

try {
    val schema = FieldSchemaLoader.load(path = Path.of("schema.yaml"))
} catch (e: SchemaLoadException) {
    logger.error("Failed to load schema: ${e.message}")
}

try {
    val asts = Parser(input = Files.readString(rulePath)).parseRules()
} catch (e: ParseException) {
    logger.error("Syntax error in rule file at line ${e.line}, column ${e.column}: ${e.message}")
}
```

`ParseException` provides:
- `line: Int` — line number of the error
- `column: Int` — column number of the error

Validation errors are returned as a `ValidationResult` (not thrown):

```kotlin
val result = Validator.validate(asts = ruleAsts, schema = schema, actions = actions)
if (!result.isValid) {
    result.diagnostics
        .filter { it.severity == Severity.ERROR }
        .forEach { println("[ERROR] ${it.message} (suggestion: ${it.suggestion})") }
}
```

---

## 9. Extending the Engine

### 9.1 Custom Normalizers

Register a custom normalizer in the `NormalizerRegistry` so that schemas can reference it by name:

```kotlin
import ruleengine.core.normalizer.NormalizerRegistry
import ruleengine.core.domain.dto.NormalizerId

// Register before loading any schema
NormalizerRegistry.register(
    id = NormalizerId("remove_spaces"),
    normalizer = { value -> value.replace(" ", "") }
)
```

> **Note:** The built-in normalizers (`trim`, `lowercase`, `uppercase`, `collapse_whitespace`, `remove_punctuation`, `german_umlaut_fold`) are always available without registration.

After registration, use the normalizer in a field schema YAML:

```yaml
fields:
  accountNumber:
    type: text
    normalizers:
      - trim
      - remove_spaces
    operators:
      - equals
```

---

## 10. Package Overview

| Package | Contents |
|---|---|
| `ruleengine.builder` | `RuleEngineBuilder`, `LoadedRuleEngine` — one-call manifest loading |
| `ruleengine.core.domain.dto` | Evaluation results: `RuleMatch`, `EvaluationResult`, `RuleAction`, `RuleBranch`, plus `OperatorId`, `NormalizerId` |
| `ruleengine.core.domain.dto.field` | Field model: `FieldSchema`, `FieldDefinition`, `FieldType`, `FieldId` |
| `ruleengine.core.domain.dto.action` | Action model: `ActionSchema`, `ActionDefinition`, `ActionArgType` |
| `ruleengine.core.domain` | Logic over that model: `FieldPathResolver` / `FieldPathResolution` (dotted-path resolution), `TemporalFormat` (date pattern parsing), `DefaultActionSchema` |
| `ruleengine.core.normalizer` | `NormalizerRegistry`, `NormalizerProfile`, built-in normalizers |
| `ruleengine.core.errors` | `RuleEngineException`, `RuleEngineBuildException`, `SchemaLoadException`, `CompilationException`, `ValidationDiagnostic` |
| `ruleengine.dsl.parser` | `Parser` — parses `.rule` text into `List<RuleAst>` |
| `ruleengine.dsl.ast` | AST node types: `RuleAst`, `ConditionAst`, `AndAst`, `OrAst`, `NotAst`, `ActionAst`, etc. |
| `ruleengine.dsl.diagnostics` | `ParseException` |
| `ruleengine.compiler` | `Validator`, `Compiler` |
| `ruleengine.evaluator` | `RuleEngine`, `CompiledRule` |
| `ruleengine.evaluator.context` | `RuleContext`, `PreparedRuleContext` |
| `ruleengine.evaluator.trace` | `TraceCollector` |
| `ruleengine.evaluator.trace.dto` | `DecisionTree`, `DecisionNode`, `toJson()` |
| `ruleengine.schema` | `FieldSchemaLoader`, `ActionSchemaLoader` |
| `ruleengine.manifest` | `ManifestLoader`, `ProjectManifest`, `ManifestEntry`, `ManifestPathResolver` |
| `ruleengine.jackson` | `JacksonUtil` — shared `ObjectMapper` instance |

---

## Full Example: Spring Boot Integration

```kotlin
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ruleengine.builder.LoadedRuleEngine
import ruleengine.builder.RuleEngineBuilder
import ruleengine.core.domain.dto.RuleMatch
import java.nio.file.Path

@Configuration
class RuleEngineConfig {

    @Bean
    fun transactionRules(): LoadedRuleEngine =
        RuleEngineBuilder.fromManifestEntry(
            manifestPath = Path.of("config/rules/manifest.yaml"),
            entryId = "transactions"
        )
}

@Service
class TransactionClassificationService(private val transactionRules: LoadedRuleEngine) {

    fun classify(transaction: Transaction): List<RuleMatch> =
        transactionRules.evaluate(
            input = mapOf(
                "purpose" to transaction.purpose,
                "amount" to transaction.amount,
                "sepaCode" to transaction.sepaCode,
                "iban" to transaction.iban,
                "tags" to transaction.tags
            )
        ).matches
}
```

A single `LoadedRuleEngine` bean carries the engine and its schema together, so no second bean is
needed. A `RuleEngineBuildException` during bean creation fails application startup, which is the
intended behaviour: the application never serves traffic with rules that did not validate.

---

## Checklist for Integration

- [ ] Library added to `dependencies`
- [ ] Manifest (or schema + rule files) placed on the classpath or filesystem
- [ ] Engine built once at application startup — `RuleEngineBuilder.fromManifest` unless a manual
      pipeline is required
- [ ] Validation result checked — fail fast if rules are invalid (the builder does this for you)
- [ ] `LoadedRuleEngine` / `RuleEngine` stored as a singleton/bean — not re-created per request
- [ ] Input passed per request/record via `LoadedRuleEngine.evaluate` (or `RuleContext` +
      `PreparedRuleContext` in the manual setup)
- [ ] Result `EvaluationResult.matches` consumed by the application layer
- [ ] Error handling for `RuleEngineBuildException` (or `SchemaLoadException` and `ParseException` in
      the manual setup) in place

