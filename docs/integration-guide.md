# Integration Guide

This guide is for **developers** who want to embed the Rule Engine into a JVM application (Java, Kotlin, or any JVM language) as a library dependency.

---

## Table of Contents

1. [Adding the Dependency](#1-adding-the-dependency)
2. [Core Concepts for Developers](#2-core-concepts-for-developers)
3. [Quick-Start: Manifest-Based Loading](#3-quick-start-manifest-based-loading)
4. [Step-by-Step: Manual Setup](#4-step-by-step-manual-setup)
   - [Loading a Field Schema](#41-loading-a-field-schema)
   - [Loading an Action Schema](#42-loading-an-action-schema)
   - [Parsing Rules](#43-parsing-rules)
   - [Validating Rules](#44-validating-rules)
   - [Compiling Rules](#45-compiling-rules)
   - [Building the Engine](#46-building-the-engine)
   - [Evaluating Input Data](#47-evaluating-input-data)
   - [Reading the Result](#48-reading-the-result)
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

---

## 3. Quick-Start: Manifest-Based Loading

The fastest way to get started is to use a manifest file.
The manifest points to all required files; the engine resolves paths relative to the manifest.

```kotlin
import ruleengine.manifest.ManifestLoader
import ruleengine.schema.FieldSchemaLoader
import ruleengine.schema.ActionSchemaLoader
import ruleengine.dsl.parser.Parser
import ruleengine.compiler.Validator
import ruleengine.compiler.Compiler
import ruleengine.evaluator.RuleEngine
import ruleengine.evaluator.context.RuleContext
import ruleengine.evaluator.context.PreparedRuleContext
import java.nio.file.Path
import java.nio.file.Files

fun buildEngine(manifestPath: Path): RuleEngine {
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
    return RuleEngine(compiledRules = compiled, schema = schema)
}
```

Evaluating a single record:

```kotlin
val engine = buildEngine(Path.of("rules/manifest.yaml"))

val result = engine.evaluate(
    prepared = PreparedRuleContext.prepare(
        ctx = RuleContext.of(
            "purpose" to "Rent apartment January",
            "amount" to 750.0,
            "tags" to listOf("regular")
        ),
        schema = schema
    )
)

for (match in result.matches) {
    println("Rule matched: ${match.ruleId}")
    for (action in match.actions) {
        println("  Action: ${action.name} ${action.arguments}")
    }
}
```

---

## 4. Step-by-Step: Manual Setup

### 4.1 Loading a Field Schema

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
- `schema.fields` — a `Map<FieldId, FieldDefinition>`, each with `.type`, `.normalizers`, and `.operators`

### 4.2 Loading an Action Schema

```kotlin
import ruleengine.schema.ActionSchemaLoader

val actions = ActionSchemaLoader.load(path = Path.of("schemas/actions.yaml"))
// or:
val actions = ActionSchemaLoader.loadFromString(content = yamlString)
val actions = ActionSchemaLoader.loadFromReader(reader = someReader)
```

The returned `ActionSchema` contains:
- `actions.actions` — a `Map<String, ActionDefinition>`, each with `argTypes: List<ActionArgType>`

### 4.3 Parsing Rules

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

### 4.4 Validating Rules

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
- All operators used in conditions are allowed for the field's type
- Literal types match the field type (e.g. no string literal on a numeric field)
- Valid regular expression patterns for `regex` operator
- All actions are defined in the action schema (if provided)
- Action argument types match the action schema definition
- All rule IDs are unique

### 4.5 Compiling Rules

```kotlin
import ruleengine.compiler.Compiler

val compiledRules = Compiler.compileRules(asts = ruleAsts, schema = schema)
```

Compilation:
- Resolves field types and normalizers from the schema
- Pre-normalises literal comparison values (so they match the normalised input at evaluation time)
- Pre-compiles regular expression patterns
- Sorts `AND` children by evaluation cost (cheapest first) for short-circuit optimisation

### 4.6 Building the Engine

```kotlin
import ruleengine.evaluator.RuleEngine

val engine = RuleEngine(compiledRules = compiledRules, schema = schema)
```

The `RuleEngine` instance is **immutable and thread-safe** after construction.
Create it once and reuse it for all evaluations.

### 4.7 Evaluating Input Data

Input data is provided as key-value pairs via `RuleContext`.
The engine accepts any `Map<String, Any?>` — the keys are field names, the values are the field values.

#### Supported value types

| Field type in schema | Expected JVM type |
|---|---|
| `TEXT` | `String` |
| `INTEGER` | `Long`, `Int`, `Short`, `Byte` |
| `DECIMAL` | `BigDecimal`, `Double`, `Float` |
| `BOOLEAN` | `Boolean` |
| `STRING_SET` | `List<String>`, `Set<String>`, `Collection<String>` |

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

### 4.8 Reading the Result

```kotlin
import ruleengine.core.domain.EvaluationResult
import ruleengine.core.domain.RuleMatch
import ruleengine.core.domain.RuleAction

val result: EvaluationResult = engine.evaluate(prepared = prepared)

// result.matches is a List<RuleMatch>
for (match: RuleMatch in result.matches) {
    println("Rule matched: ${match.ruleId}")

    for (action: RuleAction in match.actions) {
        println("  ${action.name}: ${action.arguments}")
    }
}
```

`EvaluationResult`:
- `matches: List<RuleMatch>` — all rules that matched, in the order they were declared
- `trace: Any?` — a `DecisionTree` if tracing was enabled (see section 5), otherwise `null`

`RuleMatch`:
- `ruleId: String` — the rule's ID
- `actions: List<RuleAction>` — the actions the rule declared

`RuleAction`:
- `name: String` — the action name (e.g. `"label"`)
- `arguments: List<Any?>` — the argument values (e.g. `["rent"]` or `[10]`)

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
import ruleengine.evaluator.trace.DecisionTree
import ruleengine.evaluator.trace.toJson

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
- `matchedRules: List<String>` — IDs of matched rules

`DecisionNode`:
- `id` — unique node identifier within the trace
- `type` — one of `RULE`, `AND`, `OR`, `NOT`, `CONDITION`
- `field` / `operator` / `expected` — present on `CONDITION` nodes
- `result: Boolean` — whether this node evaluated to `true`
- `evaluationTimeMs: Long?` — how long this node took to evaluate
- `ruleId: String?` — present on `RULE` nodes
- `children: List<DecisionNode>` — child nodes

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

---

## 7. Thread Safety and Lifecycle

| Object | Thread-safe? | Recommended lifetime |
|---|---|---|
| `FieldSchema` | ✅ (immutable) | Application lifetime / per rule reload |
| `ActionSchema` | ✅ (immutable) | Application lifetime / per rule reload |
| `List<CompiledRule>` | ✅ (immutable) | Application lifetime / per rule reload |
| `RuleEngine` | ✅ (stateless) | Application lifetime — create once, reuse |
| `RuleContext` | ❌ (per-call) | Per evaluation |
| `PreparedRuleContext` | ❌ (per-call) | Per evaluation |

### Hot Reload Pattern

To support rule updates without restarting the application, use an `AtomicReference`:

```kotlin
import java.util.concurrent.atomic.AtomicReference

class RuleEngineService(private val manifestPath: Path) {
    private val engineRef: AtomicReference<RuleEngine> = AtomicReference(buildEngine())

    private fun buildEngine(): RuleEngine {
        // ... load, validate, compile as shown in section 3
    }

    fun reload() {
        engineRef.set(buildEngine())
    }

    fun evaluate(context: RuleContext): EvaluationResult {
        val prepared = PreparedRuleContext.prepare(ctx = context, schema = currentSchema())
        return engineRef.get().evaluate(prepared = prepared)
    }
}
```

---

## 8. Error Handling

The engine uses typed exceptions for different failure modes:

| Exception | When thrown | Package |
|---|---|---|
| `SchemaLoadException` | A schema YAML file cannot be read or is invalid | `ruleengine.core.errors` |
| `ParseException` | A `.rule` file contains a syntax error | `ruleengine.dsl.diagnostics` |
| `CompilationException` | A rule passes validation but cannot be compiled | `ruleengine.core.errors` |

All exceptions extend `RuleEngineException`.

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
import ruleengine.core.domain.NormalizerId

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
| `ruleengine.core.domain` | Domain model: `FieldSchema`, `FieldDefinition`, `FieldType`, `ActionSchema`, `RuleMatch`, `EvaluationResult`, `RuleAction` |
| `ruleengine.core.normalizer` | `NormalizerRegistry`, `NormalizerProfile`, built-in normalizers |
| `ruleengine.core.errors` | `RuleEngineException`, `SchemaLoadException`, `CompilationException`, `ValidationDiagnostic` |
| `ruleengine.dsl.parser` | `Parser` — parses `.rule` text into `List<RuleAst>` |
| `ruleengine.dsl.ast` | AST node types: `RuleAst`, `ConditionAst`, `AndAst`, `OrAst`, `NotAst`, `ActionAst`, etc. |
| `ruleengine.dsl.diagnostics` | `ParseException` |
| `ruleengine.compiler` | `Validator`, `Compiler` |
| `ruleengine.evaluator` | `RuleEngine`, `CompiledRule` |
| `ruleengine.evaluator.context` | `RuleContext`, `PreparedRuleContext` |
| `ruleengine.evaluator.trace` | `TraceCollector`, `DecisionTree`, `DecisionNode`, `toJson()` |
| `ruleengine.schema` | `FieldSchemaLoader`, `ActionSchemaLoader` |
| `ruleengine.manifest` | `ManifestLoader`, `ProjectManifest`, `ManifestEntry` |
| `ruleengine.jackson` | `JacksonUtil` — shared `ObjectMapper` instance |

---

## Full Example: Spring Boot Integration

```kotlin
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ruleengine.compiler.Compiler
import ruleengine.compiler.Validator
import ruleengine.dsl.parser.Parser
import ruleengine.evaluator.RuleEngine
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.RuleContext
import ruleengine.manifest.ManifestLoader
import ruleengine.schema.ActionSchemaLoader
import ruleengine.schema.FieldSchemaLoader
import java.nio.file.Files
import java.nio.file.Path

@Configuration
class RuleEngineConfig {

    @Bean
    fun ruleEngine(): RuleEngine {
        val manifestPath = Path.of("config/rules/manifest.yaml")
        val manifest = ManifestLoader.load(path = manifestPath)
        val entry = manifest.entries.first()
        val baseDir = manifestPath.parent

        val schema = FieldSchemaLoader.load(path = baseDir.resolve(entry.schema!!))
        val actions = ActionSchemaLoader.load(path = baseDir.resolve(entry.actions!!))

        val ruleAsts = entry.rules.flatMap { rel ->
            Parser(input = Files.readString(baseDir.resolve(rel))).parseRules()
        }

        val validation = Validator.validate(asts = ruleAsts, schema = schema, actions = actions)
        check(validation.isValid) { "Rule validation failed: ${validation.diagnostics}" }

        val compiled = Compiler.compileRules(asts = ruleAsts, schema = schema)
        return RuleEngine(compiledRules = compiled, schema = schema)
    }
}

@Service
class TransactionClassificationService(private val ruleEngine: RuleEngine,
                                       private val schema: FieldSchema) {

    fun classify(transaction: Transaction): List<RuleMatch> {
        val context = RuleContext.of(
            "purpose"  to transaction.purpose,
            "amount"   to transaction.amount,
            "sepaCode" to transaction.sepaCode,
            "iban"     to transaction.iban,
            "tags"     to transaction.tags
        )
        val prepared = PreparedRuleContext.prepare(ctx = context, schema = schema)
        return ruleEngine.evaluate(prepared = prepared).matches
    }
}
```

---

## Checklist for Integration

- [ ] Library added to `dependencies`
- [ ] Manifest (or schema + rule files) placed on the classpath or filesystem
- [ ] Schema and rules loaded once at application startup
- [ ] Validation result checked — fail fast if rules are invalid
- [ ] `RuleEngine` stored as a singleton/bean — not re-created per request
- [ ] `RuleContext` and `PreparedRuleContext` created per request/record
- [ ] Result `EvaluationResult.matches` consumed by the application layer
- [ ] Error handling for `SchemaLoadException` and `ParseException` in place

