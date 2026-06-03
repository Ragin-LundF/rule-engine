Ich lese die Spec und erstelle einen detaillierten, inkrementellen Umsetzungsplan mit DDD‑Architektur, kleinen testbaren Klassen, klaren Fehlerklassen, Trace/Decision‑Tree‑Export sowie konkreten Tests (mit kotlin.test). Unten findest du eine Checkliste und dann die vollständige Planung.

Checkliste (Kurzüberblick)
- [ ] Projektstruktur / Module festlegen (DDD‑Bounded Contexts)
- [ ] Core Domain Model implementieren (FieldId, FieldType, FieldDefinition, OperatorId, NormalizerId, RuleAction, EvaluationResult u.a.)
- [ ] Normalizer‑Bibliothek (inkl. german_umlaut_fold) + Unit‑Tests
- [ ] YAML Field Schema Loader (klaren Fehlerklassen) + Tests
- [ ] DSL: Lexer + Parser (Fehlermeldungen mit Zeile/Spalte) + Tests
- [ ] Validator (gegen FieldSchema) mit hilfreichen Diagnostics + Tests
- [ ] Compiler (AST -> CompiledRule) inkl. Kostenmodell und Normalisierung + Tests
- [ ] Evaluator mit PreparedRuleContext, Short‑circuiting, Cost‑ordering + Tests
- [ ] Decision‑Tree/Trace Export (serialisierbar) + Tests
- [ ] Public API / Builder / Examples + Integration‑Tests
- [ ] CI / Testkonfiguration (kotlin.test + optional JUnit) und README

1) Übergeordnete Architektur (DDD + Module)
- Ziel: klare Bounded Contexts, jede Klasse hat eine eng umrissene Verantwortung, kleine, pure Funktionen, gute Testbarkeit.
- Module / Packages (anfangs Ein-Modul‑Projekt mit Packages; später können das Gradle-Module werden):
  - ruleengine.core
    - domain (ValueObjects, FieldDefinition, Action, RuleMatch, EvaluationResult)
    - normalizer (Normalizers, NormalizerRegistry)
    - operator (OperatorId, Operator implementations skeletons)
    - errors (sealable Fehlerhierarchie)
  - ruleengine.schema
    - loader (YAML loader, Schema DTOs)
    - validator (Schema validation)
  - ruleengine.dsl
    - lexer
    - parser
    - ast (RuleAst, ExpressionAst, ActionAst)
    - diagnostics (ParseError with line/column)
  - ruleengine.compiler
    - validator (AST vs Schema validation)
    - compiler (ast -> CompiledRule)
    - compiled (CompiledExpression, CompiledRule)
  - ruleengine.evaluator
    - context (RuleContext, PreparedRuleContext, PreparedValue)
    - evaluator (CompiledExpression evaluation, cost model)
    - trace (DecisionNode, DecisionTree)
  - ruleengine.api
    - RuleEngineLoader, RuleEngine, Builder API, Entry points
  - ruleengine.testsupport
    - fixtures, helpers to build contexts and rules in tests

2) Prinzipien / Non‑functional Requirements
- Jede Klasse fokussiert eine Verantwortung (Single Responsibility).
- Pure functions, minimale mutable state. PreparedRuleContext immutable.
- Fehlerbehandlung zentral und typisiert (sealed exceptions).
- Named arguments in API examples and recommended usage (enforce in code review).
- Tests: use kotlin.test (kotlin.test.assertTrue, assertEquals, assertFailsWith). Use JUnit only if needed (e.g., parameterized tests).
- Exportierbarer Decision‑Tree: strukturierte, serialisierbare Datenklasse (JSON).
- Logging/tracing optional per-evaluation flag; default off.

3) Core Domain Model (Konkrete Klassen / Signaturen)
- value classes:
  - @JvmInline value class FieldId(val value: String)
  - @JvmInline value class OperatorId(val value: String)
  - @JvmInline value class NormalizerId(val value: String)
- enum class FieldType { TEXT, INTEGER, DECIMAL, BOOLEAN, STRING_SET, DATE }
- data class FieldDefinition(
    val id: FieldId,
    val type: FieldType,
    val normalizers: List<NormalizerId> = emptyList(),
    val operators: Set<OperatorId> = emptySet()
  )
- sealed interface RuleAction { ... } or generic:
  - data class RuleAction(val name: String, val arguments: List<Any?>)
- data class RuleMatch(val ruleId: String, val actions: List<RuleAction>)
- data class EvaluationResult(val matches: List<RuleMatch>, val trace: DecisionTree?)
- Fehlerhierarchie (siehe Abschnitt Errors)

4) Errorhandling (sealable Exceptions & Diagnostics)
- Paket ruleengine.core.errors:
  - sealed class RuleEngineException(message: String) : RuntimeException(message)
  - data class SchemaLoadException(val path: Path, val cause: String) : RuleEngineException(...)
  - data class RuleParseException(val file: Path, val line: Int, val column: Int, val messageDetail: String) : RuleEngineException(...)
  - data class ValidationException(val diagnostics: List<ValidationDiagnostic>) : RuleEngineException(...)
  - data class CompilationException(val ruleId: String?, val details: String) : RuleEngineException(...)
  - data class EvaluationException(val ruleId: String?, val details: String) : RuleEngineException(...)
- ValidationDiagnostic: data class with severity, message, file/line optional, suggestion optional.

5) Normalizer Subsystem
- Interface:
  - fun interface Normalizer { fun normalize(value: String): String }
- NormalizerRegistry: register builtins by NormalizerId; load profiles from YAML.
- Builtins:
  - trim, lowercase, uppercase, german_umlaut_fold, ascii_fold, collapse_whitespace, remove_punctuation
- Implement german_umlaut_fold deterministic and tested.
- Tests: ensure "Müller  GmbH" -> "mueller gmbh" with profile [trim, lowercase, german_umlaut_fold, collapse_whitespace].

6) YAML Field Schema Loader
- Use Jackson with jackson-module-kotlin + jackson-dataformat-yaml (or SnakeYAML if preferred).
- Loader API:
  - fun loadFieldSchema(path: Path): FieldSchema
  - returns domain FieldSchema DTO (not raw YAML DTOs).
- Error handling: throw SchemaLoadException with details on unknown field types, missing required fields.
- Tests: valid YAML loads; malformed YAML yields SchemaLoadException with helpful message.

7) DSL: Lexer & Parser
- Lexer: produces tokens with line/column.
- Parser: Pratt or recursive descent using tokens; produce RuleAst objects.
- AST design per spec (RuleAst, ExpressionAst: AndAst, OrAst, NotAst, ConditionAst).
- Parser errors: RuleParseException with file/line/col and suggestion.
- Tests: many parser unit tests verifying AST shapes and error positions (kotlin.test).

8) Validator (AST vs FieldSchema)
- Responsibilities:
  - Check fields exist
  - Operator allowed for field
  - Value type compatibility
  - Rule id uniqueness across loaded rules
  - Action validity (optionally against schema)
- API:
  - fun validate(astRules: List<RuleAst>, schema: FieldSchema): ValidationResult
  - ValidationResult { val isValid: Boolean; val diagnostics: List<ValidationDiagnostic> }
- Diagnostics contain suggestions (Levenshtein suggestions for unknown field names).
- Tests: invalid operator for numeric field, unknown field with suggestion, duplicate IDs.

9) Compiler (AST -> CompiledRule)
- Convert ConditionAst (strings) into CompiledExpression nodes bound to FieldId, pre-normalized expected values (using NormalizerRegistry).
- CompiledExpression interface:
  - interface CompiledExpression { val cost: EvaluationCost; fun evaluate(context: PreparedRuleContext, traceCollector: TraceCollector?): Boolean }
- CompiledAnd / CompiledOr sort children by cost for AND; OR preserve order (per default).
- Precompile patterns (e.g., regex in future).
- CompiledRule: data class CompiledRule(val id: String, val expression: CompiledExpression, val actions: List<RuleAction>, val meta: RuleMeta)
- Compiler errors: CompilationException with ruleId and details.
- Tests: compiled expression behaves as expected when evaluated against prepared contexts.

10) PreparedRuleContext and PreparedValue
- Interface RuleContext (source input).
- Preparation step:
  - fun prepare(context: RuleContext, schema: FieldSchema, normalizerRegistry: NormalizerRegistry): PreparedRuleContext
  - Preconditions: normalize text fields once per evaluation input; convert numerics; build PreparedStringSet with normalized set entries.
- PreparedValue sealed classes: PreparedText(original, normalized), PreparedInteger(value: Long), PreparedDecimal(value: BigDecimal), PreparedStringSet(original, normalized)
- Tests: ensure normalization applied once and used across multiple rule evaluations.

11) Evaluator
- RuleEngine implementation:
  - class RuleEngine private constructor(private val compiledRules: List<CompiledRule>, private val schema: FieldSchema, private val normalizers: NormalizerRegistry) {
      fun evaluate(context: RuleContext, options: EvaluationOptions = EvaluationOptions()): EvaluationResult
    }
- EvaluationOptions: data class with flags: includeTrace: Boolean, timeouts, maxMatches, parallelEvaluation: Boolean(default=false)
- Evaluation strategy: sequential evaluate compiled rules; optionally implement parallel later but ensure PreparedContext reused safely (immutable).
- Short-circuiting: implemented in CompiledAnd/Or; cost ordering for AND nodes.
- Tests: end-to-end evaluation examples demonstrating matches and non-matches.

12) Decision‑Tree / Trace Export
- DecisionNode:
  - data class DecisionNode(
      val nodeId: String,
      val type: NodeType, // CONDITION, AND, OR, RULE, ACTION
      val fieldId: FieldId?, // for CONDITION
      val operator: OperatorId?,
      val expected: Any?,
      val result: Boolean,
      val children: List<DecisionNode> = emptyList(),
      val evaluationTimeMs: Long? = null
    )
- DecisionTree: data class DecisionTree(val root: DecisionNode, val matchedRules: List<String>)
- During evaluation, if includeTrace==true, evaluator supplies a TraceCollector to each CompiledExpression to record per-node results.
- Serialization: use kotlinx.serialization or Jackson to serialize DecisionTree to JSON for persistence.
- Tests: assert serialized tree structure for given inputs.

13) Public API & Builder
- RuleEngineLoader:
  - fun load(fieldSchemaPath: Path, ruleDirectories: List<Path>, options: LoaderOptions = LoaderOptions()): RuleEngine
- RuleEngineBuilder<T> optional typed builder (for typed API).
- Usage examples (with named args):
  - val engine = RuleEngineLoader.load(fieldSchemaPath = Path.of("schema.yaml"), ruleDirectories = listOf(Path.of("rules")))
  - val result = engine.evaluate(context = myContext, options = EvaluationOptions(includeTrace = true))
- Ensure functions use named parameters in examples and encourage their use.

14) Tests & Test Strategy
- Use kotlin.test for all unit tests:
  - assertions: assertEquals(expected = x, actual = y), assertTrue(condition), assertFailsWith<ExceptionType> { ... }
- Test coverage per step:
  - Core model tests (value classes, equals)
  - Normalizer tests (behavioral)
  - YAML loader tests (valid/invalid)
  - Lexer/parser tests (good inputs, syntax errors)
  - Validator tests (invalid field/operator/type mismatches)
  - Compiler tests (compiled expressions)
  - Evaluator tests (integration-like: schema, compiled rules, evaluate contexts)
  - Decision trace tests (structure & JSON)
- Example unit test skeleton (kotlin.test):
  - @Test fun `german fold normalizer works`() {
      val norm = NormalizerRegistry.default.get(NormalizerId("german_umlaut_fold"))
      val result = norm.normalize(value = "Müller  GmbH")
      assertEquals(expected = "Mueller  GmbH".lowercase(), actual = result) // adjust per profile
    }
- For tests needing more features than kotlin.test, use JUnit annotations sparingly.

15) Incrementelle Entwicklungsschritte (Milestones with Deliverables & Tests)
- Milestone 0 — Projekt Setup (1 day)
  - Gradle Kotlin project skeleton, dependencies:
    - implementation("com.fasterxml.jackson.module:jackson-module-kotlin:..."),
    - implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:..."),
    - implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:...") (optional)
    - testImplementation("org.jetbrains.kotlin:kotlin-test:..."), testImplementation("org.jetbrains.kotlin:kotlin-test-junit:...")
  - Basic package structure and README
  - Deliverable: buildable project, sample test runs
- Milestone 1 — Core Domain + Normalizers (2–3 days)
  - Implement value classes, FieldDefinition, FieldSchema DTO, RuleAction, EvaluationResult
  - Implement Normalizer interface, NormalizerRegistry, builtin normalizers (trim, lowercase, german_umlaut_fold, collapse_whitespace)
  - Unit tests for Normalizers and domain
  - Acceptance: Normalizer tests pass; domain objects immutable and simple
- Milestone 2 — YAML Field Schema Loader (2–3 days)
  - Implement YAML DTOs -> domain FieldSchema mapping, validation of field types/operators
  - Tests: sample YAMLs and negative cases
- Milestone 3 — Compiler core + PreparedContext without DSL (3–4 days)
  - Implement CompiledExpression, simple Condition evaluators (equals numeric/text, gt/gte/lt/lte), CompiledAnd/Or, cost model
  - Implement PreparedRuleContext and preparation logic using FieldSchema and NormalizerRegistry
  - Build programmatic API to create CompiledRules directly (no DSL yet)
  - Tests: programmatically create CompiledRule and evaluate contexts
- Milestone 4 — DSL Lexer & Parser (4–6 days)
  - Implement lexer (tokens with positions), parser producing AST and diagnostics
  - Parser tests for correct AST and for error positions
- Milestone 5 — Validator + Compiler integration (3–4 days)
  - Implement validator that checks AST against FieldSchema and returns diagnostics
  - Implement compiler that converts validated AST -> CompiledRule, including normalizing literal strings at compile time
  - Tests: full compile pipeline tests, invalid rule detection
- Milestone 6 — RuleEngine, Evaluator end‑to‑end + Decision Trace (3–4 days)
  - Implement RuleEngineLoader, RuleEngine.evaluate, EvaluationOptions, DecisionTree tracing and JSON export
  - Tests: end‑to‑end rule evaluation including trace output saved and assertable
- Milestone 7 — Extras & Hardening (2–4 days)
  - Add action parsing/validation, rule metadata, enable/disable rules
  - Add CLI validator tool (optional)
  - Add benchmarks (JMH) later
  - Add more tests and CI config

16) Decision Tree / Trace Design (Details)
- Trace should be optional and cheap when disabled.
- Provide internal TraceCollector interface implemented by a NoOpTraceCollector (default) and JsonTraceCollector when options.includeTrace==true.
- DecisionNode fields (see earlier). Provide summary node for each rule showing whether matched and which actions were applied.
- Add API: fun EvaluationResult.toJson(): String
- Store decision trees as compact JSON to persist in application DB or logs.

17) Performance considerations (for later)
- PreparedContext per input to avoid repeated normalization.
- CompiledExpressions avoid allocations in hot path (reuse precompiled patterns).
- Cost model (VERY_CHEAP..EXPENSIVE) used to reorder AND children.
- Future: indexing and rule preselection (phase 3 in spec).

18) Coding Style & Conventions
- Use named args in public API samples and recommend them.
- Keep small classes (<300 LOC ideally).
- Document public API with KDoc.
- Use Kotlin immutable collections by default.

19) Example API Signatures (concrete)
- RuleEngineLoader:
  - object RuleEngineLoader {
      fun load(
        fieldSchemaPath: Path,
        rulePaths: List<Path> = listOf(),
        normalizerRegistry: NormalizerRegistry = NormalizerRegistry.default
      ): RuleEngine
    }
- RuleEngine:
  - class RuleEngine(private val compiledRules: List<CompiledRule>, ...) {
      fun evaluate(
        context: RuleContext,
        options: EvaluationOptions = EvaluationOptions(includeTrace = false)
      ): EvaluationResult
    }
- RuleContext factory helpers:
  - object RuleContext { fun of(vararg entries: Pair<String, Any?>): RuleContext }

20) Tests: concrete examples to include
- Parser: "purpose contains \"miete\" and amount >= 500" => AST And(Condition, Condition)
- Validator: "amount contains \"abc\"" => ValidationDiagnostic operator-not-allowed for field amount
- Normalizer: see earlier
- Evaluator: rent-payment example => RuleMatch present with actions [label "rent"]
- Trace: structure with root AND/OR nodes and final RULE nodes with actions applied

21) Deliverables per Milestone (what to check in PR)
- Code compiles, tests pass (kotlin.test)
- Public API documented and examples added to README
- Error messages include file/line/col where applicable
- Decision trace serializable to JSON
- Small set of sample rules and schema in /src/test/resources for regression tests

22) Implementation notes & Libraries
- Jackson Kotlin + jackson-dataformat-yaml for YAML parsing and mapping.
- kotlinx.serialization or Jackson for DecisionTree serialization (choose one for consistency; prefer Jackson if using it for YAML).
- Keep dependencies minimal.

23) Next concrete step I will take (if du mir die Erlaubnis gibst)
- Erstelle initialen Projekt skeleton (Gradle build) + core domain model + normalizers + unit tests für normalizer.
- Wenn du möchtest, kann ich sofort die ersten Dateien anlegen und Tests ausführen.

Wenn du OK gibst, beginne ich mit Milestone 0–1: Projekt skeleton + Core Domain + Normalizers und erstelle die ersten Implementierungs‑ und Testdateien. Möchtest du, dass ich sofort mit dem Code loslege?
