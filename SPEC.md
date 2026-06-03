## Plan für Variante C: Hybrid aus YAML Field Schema + eigener Rule DSL

Ziel: Eine kleine, einfache, performante und domänenunabhängige Rule Engine in Kotlin.

```text
Field Schema: YAML
Rules: eigene, einfache DSL
Runtime: vorkompilierte Regeln
```

Die zentrale Idee:

```text
YAML beschreibt, welche Felder es gibt.
DSL beschreibt, welche Regeln gelten.
Kotlin Engine wertet nur vorkompilierte Regeln aus.
```

---

# 1. Zielarchitektur

```text
                 ┌────────────────────┐
                 │ field-schema.yaml   │
                 └─────────┬──────────┘
                           │
                           ▼
                 ┌────────────────────┐
                 │ Field Registry      │
                 └─────────┬──────────┘

                 ┌────────────────────┐
                 │ rules/*.rule        │
                 └─────────┬──────────┘
                           │
                           ▼
                 ┌────────────────────┐
                 │ Rule Parser         │
                 └─────────┬──────────┘
                           ▼
                 ┌────────────────────┐
                 │ Rule Validator      │
                 └─────────┬──────────┘
                           ▼
                 ┌────────────────────┐
                 │ Rule Compiler       │
                 └─────────┬──────────┘
                           ▼
                 ┌────────────────────┐
                 │ CompiledRuleEngine  │
                 └─────────┬──────────┘
                           ▼
                 ┌────────────────────┐
                 │ Evaluation          │
                 └────────────────────┘
```

Wichtig: **Parsing, Validierung und Kompilierung passieren nur beim Start oder Reload.**
Die eigentliche Evaluation ist dann sehr klein und schnell.

---

# 2. Projektstruktur

Eine mögliche Modulstruktur:

```text
rule-engine-core
  ├── field
  ├── normalization
  ├── operator
  ├── ast
  ├── compiler
  ├── evaluator
  └── result

rule-engine-yaml
  └── field schema loader

rule-engine-dsl
  ├── lexer
  ├── parser
  └── diagnostics

rule-engine-test-support
  └── fixtures, benchmarks, sample contexts

rule-engine-examples
  ├── transaction-rules
  └── report-rules
```

Für den Anfang könnte auch alles in einem Modul liegen. Die logische Trennung sollte aber von Beginn an sauber sein.

---

# 3. YAML Field Schema

Das YAML Schema definiert:

```text
- verfügbare Felder
- Feldtypen
- erlaubte Operatoren
- Normalisierung
- optionale Performance-Hinweise
```

## Beispiel: Transaktionen

```yaml
schema: transaction-v1

fields:
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
      - regex

  sepaCode:
    type: text
    normalizers:
      - trim
      - uppercase
    operators:
      - equals
      - in

  counterpartyName:
    type: text
    normalizers:
      - trim
      - lowercase
      - german_umlaut_fold
    operators:
      - equals
      - contains
      - startsWith

  amount:
    type: decimal
    operators:
      - equals
      - gt
      - gte
      - lt
      - lte
      - between

  labels:
    type: stringSet
    normalizers:
      - trim
      - lowercase
      - german_umlaut_fold
    operators:
      - contains
      - containsAny
      - containsAll
```

## Beispiel: Report-Regeln

```yaml
schema: customer-report-v1

fields:
  incomeTransactionCount:
    type: integer
    operators:
      - equals
      - gt
      - gte
      - lt
      - lte

  expenseTransactionCount:
    type: integer
    operators:
      - equals
      - gt
      - gte
      - lt
      - lte

  recurringRiskTransactionCount:
    type: integer
    operators:
      - equals
      - gt
      - gte
      - lt
      - lte

  totalRiskAmount:
    type: decimal
    operators:
      - equals
      - gt
      - gte
      - lt
      - lte
      - between

  customerLabels:
    type: stringSet
    normalizers:
      - lowercase
      - german_umlaut_fold
    operators:
      - contains
      - containsAny
      - containsAll
```

---

# 4. Field Model in Kotlin

```kotlin
@JvmInline
value class FieldId(val value: String)

enum class FieldType {
    TEXT,
    INTEGER,
    DECIMAL,
    BOOLEAN,
    STRING_SET,
    DATE
}

data class FieldDefinition(
    val id: FieldId,
    val type: FieldType,
    val normalizers: List<NormalizerId>,
    val operators: Set<OperatorId>
)

data class FieldSchema(
    val name: String,
    val fields: Map<FieldId, FieldDefinition>
)
```

Operatoren und Normalizer ebenfalls als IDs:

```kotlin
@JvmInline
value class OperatorId(val value: String)

@JvmInline
value class NormalizerId(val value: String)
```

Das hält die Engine erweiterbar, ohne überall Enums ändern zu müssen.

---

# 5. DSL-Design

Die DSL sollte absichtlich klein bleiben.

## Grundform

```text
rule "rent-payment" {
  when
    purpose contains "miete"
    and amount >= 500
    and sepaCode == "PMNT"

  then
    label "rent"
    category "housing"
}
```

## OR-Beispiel

```text
rule "salary" {
  when
    purpose contains "gehalt"
    or purpose contains "lohn"
    or counterpartyName contains "arbeitgeber"

  then
    label "income"
    category "salary"
}
```

## Gruppierung

```text
rule "rent-payment" {
  when
    (
      purpose contains "miete"
      and amount >= 500
    )
    or
    (
      counterpartyName contains "vermieter"
      and sepaCode == "PMNT"
    )

  then
    label "rent"
}
```

## Report-Regel

```text
rule "risky-customer-report" {
  when
    incomeTransactionCount == 2
    and expenseTransactionCount == 10
    and recurringRiskTransactionCount >= 5

  then
    flag "risky-customer"
    score +25
}
```

---

# 6. Operator-Syntax in der DSL

Ich würde sowohl lesbare Operatoren als auch kurze Symbole unterstützen.

```text
equals       oder ==
contains
startsWith
endsWith
regex
in

>           oder gt
>=          oder gte
<           oder lt
<=          oder lte
between
```

Beispiele:

```text
amount >= 500
amount gte 500
purpose contains "miete"
sepaCode in ["PMNT", "CCRD"]
labels contains "risk-recurring"
```

Intern wird alles normalisiert:

```text
>=  → gte
==  → equals
```

---

# 7. Actions

Die Engine sollte nur Ergebnisse liefern, nicht selbst fachlich handeln.

DSL:

```text
then
  label "rent"
  category "housing"
  flag "recurring-risk"
  score +25
```

Kotlin:

```kotlin
sealed interface RuleAction

data class LabelAction(val value: String) : RuleAction
data class CategoryAction(val value: String) : RuleAction
data class FlagAction(val value: String) : RuleAction
data class ScoreAction(val delta: Int) : RuleAction
```

Oder generischer:

```kotlin
data class RuleAction(
    val type: String,
    val value: Any?
)
```

Für Einfachheit und Flexibilität würde ich mit der generischen Variante starten:

```kotlin
data class RuleAction(
    val name: String,
    val arguments: List<ActionValue>
)
```

Beispiel:

```text
label "rent"
```

wird zu:

```kotlin
RuleAction(
    name = "label",
    arguments = listOf(ActionValue.StringValue("rent"))
)
```

---

# 8. AST der DSL

Der Parser erzeugt zunächst einen neutralen AST.

```kotlin
data class RuleAst(
    val id: String,
    val description: String?,
    val condition: ExpressionAst,
    val actions: List<ActionAst>
)

sealed interface ExpressionAst

data class AndAst(
    val children: List<ExpressionAst>
) : ExpressionAst

data class OrAst(
    val children: List<ExpressionAst>
) : ExpressionAst

data class NotAst(
    val child: ExpressionAst
) : ExpressionAst

data class ConditionAst(
    val field: String,
    val operator: String,
    val value: LiteralAst
) : ExpressionAst
```

Wichtig: Der AST enthält noch Strings aus der DSL.
Erst der Validator prüft sie gegen das YAML Schema.

---

# 9. Validierung

Die Validierung ist ein zentraler Bestandteil.

Sie prüft:

```text
- Existiert das Feld?
- Ist der Operator für dieses Feld erlaubt?
- Passt der Werttyp zum Feldtyp?
- Ist Regex für dieses Feld erlaubt?
- Ist die Action erlaubt?
- Ist die Regel-ID eindeutig?
```

Beispiel Fehler:

```text
Unknown field "purpse" in rule "rent-payment".
Did you mean "purpose"?
```

Oder:

```text
Operator "contains" is not allowed for field "amount".
Allowed operators: equals, gt, gte, lt, lte, between.
```

Gute Fehlermeldungen sind bei einer DSL extrem wichtig.

---

# 10. Kompilierung

Nach der Validierung wird aus dem AST eine performante interne Struktur.

```kotlin
data class CompiledRule(
    val id: String,
    val expression: CompiledExpression,
    val actions: List<RuleAction>
)

sealed interface CompiledExpression {
    fun evaluate(context: PreparedRuleContext): Boolean
}
```

## AND

```kotlin
class CompiledAnd(
    children: List<CompiledExpression>
) : CompiledExpression {

    private val orderedChildren =
        children.sortedBy { it.cost }

    override fun evaluate(context: PreparedRuleContext): Boolean {
        for (child in orderedChildren) {
            if (!child.evaluate(context)) return false
        }
        return true
    }
}
```

## OR

```kotlin
class CompiledOr(
    children: List<CompiledExpression>
) : CompiledExpression {

    private val orderedChildren =
        children.sortedBy { it.cost }

    override fun evaluate(context: PreparedRuleContext): Boolean {
        for (child in orderedChildren) {
            if (child.evaluate(context)) return true
        }
        return false
    }
}
```

Bei `AND` und `OR` wird also automatisch short-circuiting genutzt.

---

# 11. Runtime Context

Für Flexibilität:

```kotlin
interface RuleContext {
    fun get(field: FieldId): Any?
}
```

Für Performance wird daraus vor der Evaluation ein vorbereiteter Context:

```kotlin
class PreparedRuleContext(
    private val values: Map<FieldId, PreparedValue>
) {
    fun get(field: FieldId): PreparedValue? = values[field]
}
```

Prepared Values:

```kotlin
sealed interface PreparedValue

data class PreparedText(
    val original: String,
    val normalized: String
) : PreparedValue

data class PreparedInteger(
    val value: Long
) : PreparedValue

data class PreparedDecimal(
    val value: BigDecimal
) : PreparedValue

data class PreparedStringSet(
    val original: Set<String>,
    val normalized: Set<String>
) : PreparedValue
```

Damit werden Normalisierungen nicht pro Regel wiederholt.

---

# 12. Normalisierung

Der alte Ansatz mit duplizierten Regeln für Umlaute sollte ersetzt werden.

## Normalizer Interface

```kotlin
fun interface Normalizer {
    fun normalize(value: String): String
}
```

## Standard-Normalizer

```text
trim
lowercase
uppercase
german_umlaut_fold
ascii_fold
collapse_whitespace
remove_punctuation
```

## German Umlaut Fold

```text
ä → ae
ö → oe
ü → ue
Ä → ae oder AE, abhängig davon ob lowercase vorher läuft
Ö → oe
Ü → ue
ß → ss
```

Typischer Ablauf:

```yaml
normalizers:
  - trim
  - lowercase
  - german_umlaut_fold
  - collapse_whitespace
```

Dann wird:

```text
"Müller & Söhne GmbH"
```

zu:

```text
"mueller & soehne gmbh"
```

Die Regel:

```text
counterpartyName contains "Müller"
```

wird beim Kompilieren ebenfalls normalisiert zu:

```text
mueller
```

---

# 13. Text Operatoren

Jeder Operator sollte beim Kompilieren sein Pattern vorbereiten.

```kotlin
class TextContainsEvaluator(
    private val field: FieldId,
    private val expected: String
) : CompiledExpression {

    override val cost = EvaluationCost.MEDIUM

    override fun evaluate(context: PreparedRuleContext): Boolean {
        val value = context.get(field) as? PreparedText ?: return false
        return value.normalized.contains(expected)
    }
}
```

Regex wird vorkompiliert:

```kotlin
class RegexEvaluator(
    private val field: FieldId,
    pattern: String
) : CompiledExpression {

    private val regex = Regex(pattern)

    override val cost = EvaluationCost.EXPENSIVE

    override fun evaluate(context: PreparedRuleContext): Boolean {
        val value = context.get(field) as? PreparedText ?: return false
        return regex.containsMatchIn(value.normalized)
    }
}
```

Wichtig: Regex-Pattern nicht zur Laufzeit neu bauen.

---

# 14. Kostenmodell

```kotlin
enum class EvaluationCost {
    VERY_CHEAP,
    CHEAP,
    MEDIUM,
    EXPENSIVE
}
```

Beispiel:

```text
equals numeric      VERY_CHEAP
equals text         CHEAP
startsWith          CHEAP
endsWith            CHEAP
contains            MEDIUM
containsAny         MEDIUM
regex               EXPENSIVE
```

Bei `AND` kann immer nach Kosten sortiert werden.

Bei `OR` kann ebenfalls nach Kosten sortiert werden, aber fachlich kann es manchmal sinnvoll sein, Trefferprioritäten beizubehalten. Deshalb:

```yaml
engine:
  reorderOrConditions: true
```

oder pro Regel:

```text
rule "some-rule" {
  options {
    preserveOrder true
  }

  when
    ...
}
```

Für V1 würde ich sagen:

```text
AND wird sortiert.
OR bleibt in Schreibreihenfolge.
```

Das ist einfach und vorhersehbar.

---

# 15. Rule Engine API

## Variante für maximale Einfachheit

```kotlin
val engine = RuleEngineLoader.load(
    fieldSchemaPath = Path.of("field-schema.yaml"),
    ruleDirectory = Path.of("rules")
)

val result = engine.evaluate(
    RuleContext.of(
        "purpose" to "Miete Januar",
        "amount" to BigDecimal("850.00"),
        "sepaCode" to "PMNT"
    )
)
```

## Ergebnis

```kotlin
data class EvaluationResult(
    val matches: List<RuleMatch>
)

data class RuleMatch(
    val ruleId: String,
    val actions: List<RuleAction>
)
```

Beispiel:

```kotlin
EvaluationResult(
    matches = listOf(
        RuleMatch(
            ruleId = "rent-payment",
            actions = listOf(
                RuleAction("label", listOf("rent")),
                RuleAction("category", listOf("housing"))
            )
        )
    )
)
```

---

# 16. Typisierte Kotlin API für produktive Nutzung

Für Performance und Komfort kann man zusätzlich eine typisierte API anbieten:

```kotlin
val transactionEngine = RuleEngineBuilder<Transaction>()
    .field("purpose", FieldType.TEXT) { it.purpose }
    .field("sepaCode", FieldType.TEXT) { it.sepaCode }
    .field("counterpartyName", FieldType.TEXT) { it.counterpartyName }
    .field("amount", FieldType.DECIMAL) { it.amount }
    .field("labels", FieldType.STRING_SET) { it.labels }
    .schema(Path.of("transaction-fields.yaml"))
    .rules(Path.of("transaction-rules"))
    .build()
```

Evaluation:

```kotlin
val result = transactionEngine.evaluate(transaction)
```

Intern kann diese API den `PreparedRuleContext` ohne Reflection erstellen.

---

# 17. Parser-Strategie

Für V1 würde ich keinen schweren Parser-Generator verwenden.

Empfehlung:

```text
kleiner handgeschriebener Lexer + Pratt Parser oder rekursiver Parser
```

Die Grammatik ist klein:

```text
ruleDecl      := "rule" STRING "{" ruleBody "}"
ruleBody      := description? whenBlock thenBlock
whenBlock     := "when" expression
thenBlock     := "then" action*
expression    := orExpr
orExpr        := andExpr ("or" andExpr)*
andExpr       := unaryExpr ("and" unaryExpr)*
unaryExpr     := "not" unaryExpr | primary
primary       := condition | "(" expression ")"
condition     := IDENT OP literal
literal       := STRING | NUMBER | BOOLEAN | list
```

Das ergibt klare Prioritäten:

```text
not > and > or
```

Beispiel:

```text
A or B and C
```

bedeutet:

```text
A or (B and C)
```

---

# 18. Phase 1: Minimal Viable Engine

Ziel: schnell nutzbare, kleine Engine.

## Enthalten

```text
- YAML Field Schema
- DSL Parser
- AND / OR / Klammern
- Text, Integer, Decimal, StringSet
- equals, contains, startsWith, endsWith
- gt, gte, lt, lte
- contains für Sets
- Normalisierung für Textfelder
- Rule Compilation
- Short-circuit Evaluation
- einfache Actions
```

## Nicht enthalten

```text
- Regex
- Indexing
- NOT
- Datumslogik
- Rule Priorities
- Rule Dependencies
- UI/Editor
- Hot Reload
```

Warum Regex nicht in V1?

Weil Regex sofort Sonderfälle erzeugt:

```text
- Regex Performance
- Pattern-Sicherheit
- Fehlermeldungen
- Normalisierung vs Originaltext
```

Man kann Regex in V2 ergänzen.

---

# 19. Phase 2: Erweiterung

```text
- Regex mit vorkompilierten Patterns
- NOT
- between
- containsAny / containsAll
- bessere Diagnostics
- Rule option: enabled / disabled
- Rule priority
- Rule groups
- Rule metadata
```

Beispiel:

```text
rule "high-risk-recurring" {
  enabled true
  priority 100

  when
    labels containsAll ["risk", "recurring"]
    and amount >= 100

  then
    flag "high-risk"
}
```

---

# 20. Phase 3: Performance-Optimierung

Erst nach Messung.

```text
- Indexing für häufige equals-Bedingungen
- Preselection von Regeln
- Field dependency analysis
- Batch Evaluation
- Metrics pro Operator
- Benchmark Suite
```

## Beispiel Indexing

Wenn viele Regeln Folgendes enthalten:

```text
sepaCode == "PMNT"
```

kann man Regeln gruppieren:

```text
sepaCode=PMNT → [rule1, rule7, rule20]
sepaCode=CCRD → [rule2, rule5]
```

Dann muss bei einer Transaktion mit `sepaCode=PMNT` nicht jede Regel geprüft werden.

Aber: Indexing ist komplexer und sollte nicht in V1 landen.

---

# 21. Phase 4: Usability

```text
- CLI Validator
- Test-Dateien für Regeln
- Golden Master Tests
- verständliche Fehlermeldungen
- Beispielregeln
- Dokumentation für Fachanwender
```

CLI-Beispiel:

```bash
rule-engine validate \
  --schema transaction-fields.yaml \
  --rules rules/
```

Output:

```text
OK: 42 rules loaded
WARN: rule "rent-payment" uses contains on field "purpose"
ERROR: rule "salary" references unknown field "counterparty"
```

---

# 22. Teststrategie

## Unit Tests

```text
- Normalizer
- Operatoren
- Parser
- Validator
- Compiler
- Evaluator
```

## DSL Parser Tests

Input:

```text
purpose contains "miete" and amount >= 500
```

Erwarteter AST:

```text
And(
  Condition(purpose, contains, "miete"),
  Condition(amount, gte, 500)
)
```

## Rule Tests

Man sollte Regeln gegen Beispielinputs testen können:

```yaml
rule: rent-payment

cases:
  - name: matches rent
    input:
      purpose: "Miete Januar"
      amount: 850
      sepaCode: "PMNT"
    matches: true

  - name: does not match low amount
    input:
      purpose: "Miete Januar"
      amount: 100
      sepaCode: "PMNT"
    matches: false
```

Das wäre extrem wertvoll für Wartbarkeit.

---

# 23. Benchmarks

Von Anfang an kleine JMH Benchmarks einbauen.

Szenarien:

```text
- 10 Regeln, 1 Input
- 100 Regeln, 1 Input
- 1.000 Regeln, 1 Input
- 100 Regeln, 10.000 Inputs
- Text contains vs equals
- mit/ohne Normalisierung
- mit/ohne Regex
```

Metriken:

```text
- Evaluation time per input
- Allocations per evaluation
- Startup compile time
- Regex impact
```

Wichtig: Bei Performance nicht raten, sondern messen.

---

# 24. Konkreter Implementierungsplan

## Schritt 1: Core Domain Model

Implementieren:

```text
FieldId
OperatorId
FieldType
FieldDefinition
FieldSchema
RuleAction
RuleMatch
EvaluationResult
```

Akzeptanzkriterium:

```text
Die Domänenmodelle sind unabhängig von YAML und DSL verwendbar.
```

---

## Schritt 2: Normalizer

Implementieren:

```text
trim
lowercase
uppercase
german_umlaut_fold
collapse_whitespace
```

Akzeptanzkriterium:

```text
"Müller  GmbH" wird zu "mueller gmbh".
```

---

## Schritt 3: YAML Field Schema Loader

Implementieren mit z.B. Jackson YAML oder SnakeYAML.

```kotlin
val schema = FieldSchemaLoader.load(path)
```

Akzeptanzkriterium:

```text
YAML-Datei wird geladen und in FieldSchema übersetzt.
Unbekannte Field Types erzeugen klare Fehler.
```

---

## Schritt 4: DSL Lexer

Token erkennen:

```text
rule
description
when
then
and
or
not
identifier
string
number
boolean
operators
braces
parentheses
brackets
comma
```

Akzeptanzkriterium:

```text
Eine DSL-Datei wird in eine Token-Liste zerlegt.
Fehler enthalten Zeile und Spalte.
```

---

## Schritt 5: DSL Parser

Implementieren:

```text
rule declarations
when expressions
then actions
operator precedence
parentheses
lists
```

Akzeptanzkriterium:

```text
Aus einer .rule-Datei entsteht ein RuleAst.
```

---

## Schritt 6: Validator

Validieren gegen Field Schema.

Akzeptanzkriterium:

```text
Unbekannte Felder, falsche Operatoren und falsche Werttypen werden erkannt.
```

Beispiel:

```text
amount contains "abc"
```

muss fehlschlagen, weil `amount` numerisch ist.

---

## Schritt 7: Compiler

AST wird zu CompiledRule.

Akzeptanzkriterium:

```text
Stringwerte sind normalisiert.
Regex wäre später vorkompiliert.
AND-Knoten sortieren günstige Conditions zuerst.
```

---

## Schritt 8: Evaluation

Implementieren:

```kotlin
engine.evaluate(context)
```

Akzeptanzkriterium:

```text
Passende Regeln liefern RuleMatch mit Actions.
Nicht passende Regeln liefern nichts.
```

---

## Schritt 9: Kotlin Builder API

Einfacher Einstieg:

```kotlin
val engine = RuleEngine.load(schemaPath, rulesPath)
```

Optional typisiert:

```kotlin
val engine = RuleEngineBuilder<Transaction>()
    .field("purpose") { it.purpose }
    .field("amount") { it.amount }
    .build()
```

Akzeptanzkriterium:

```text
Eine Anwendung kann die Engine mit wenigen Zeilen einbinden.
```

---

## Schritt 10: Tests und Benchmarks

Mindestens:

```text
- Parser Tests
- Validator Tests
- Normalizer Tests
- Evaluator Tests
- JMH Benchmark
```

Akzeptanzkriterium:

```text
Performance-Baseline ist bekannt.
```

---

# 25. Empfohlene V1-Syntax

Ich würde für V1 diese Syntax festlegen:

```text
rule "rule-id" {
  description "Optional text"

  when
    expression

  then
    action value
    action value
}
```

Beispiel:

```text
rule "rent-payment" {
  description "Detects rent payments"

  when
    purpose contains "miete"
    and amount >= 500
    and sepaCode == "PMNT"

  then
    label "rent"
    category "housing"
}
```

Report:

```text
rule "risky-customer-report" {
  when
    incomeTransactionCount == 2
    and expenseTransactionCount == 10
    and recurringRiskTransactionCount >= 5

  then
    flag "risky-customer"
    score +25
}
```

---

# 26. Empfohlene V1-YAML-Struktur

```yaml
schema: transaction-v1

normalizers:
  germanText:
    - trim
    - lowercase
    - german_umlaut_fold
    - collapse_whitespace

fields:
  purpose:
    type: text
    normalizers: germanText
    operators:
      - equals
      - contains
      - startsWith
      - endsWith

  counterpartyName:
    type: text
    normalizers: germanText
    operators:
      - equals
      - contains
      - startsWith

  sepaCode:
    type: text
    normalizers:
      - trim
      - uppercase
    operators:
      - equals
      - in

  amount:
    type: decimal
    operators:
      - equals
      - gt
      - gte
      - lt
      - lte

  labels:
    type: stringSet
    normalizers: germanText
    operators:
      - contains
      - containsAny
      - containsAll
```

Das vermeidet Wiederholung durch benannte Normalizer-Profile.

---

# 27. Entscheidungen, die früh getroffen werden sollten

## 1. Muss die DSL mehrere Regeln pro Datei unterstützen?

Empfehlung: Ja.

```text
Eine Datei kann mehrere rules enthalten.
```

## 2. Sind Rule IDs global eindeutig?

Empfehlung: Ja.

```text
Doppelte IDs sind Fehler.
```

## 3. Sind Actions frei oder vordefiniert?

Empfehlung für V1:

```text
Actions sind syntaktisch frei, aber optional validierbar.
```

Beispiel:

```yaml
actions:
  label:
    argumentType: string
  flag:
    argumentType: string
  score:
    argumentType: integer
```

Das könnte später ins Schema aufgenommen werden.

## 4. Wird `OR` intern sortiert?

Empfehlung für V1:

```text
AND sortieren, OR nicht sortieren.
```

## 5. Wird Regex in V1 unterstützt?

Empfehlung:

```text
Nein, Regex erst in V2.
```

Falls ihr Regex zwingend braucht:

```text
Ja, aber nur vorkompiliert und immer als expensive operator.
```

---

# 28. Risiken und Gegenmaßnahmen

## Risiko: Eigene DSL wird zu groß

Gegenmaßnahme:

```text
Keine Funktionen
Keine Variablen
Keine Imports
Keine Arithmetik
Keine verschachtelten Actions
Keine dynamische Feldnavigation
```

Die DSL bleibt nur:

```text
rule + condition + action
```

## Risiko: Performance leidet durch Flexibilität

Gegenmaßnahme:

```text
Keine Reflection
Vorkompilierte Conditions
Prepared Context
Short-circuiting
Normalisierung einmal pro Input
```

## Risiko: Fachanwender machen Syntaxfehler

Gegenmaßnahme:

```text
CLI Validator
Zeile/Spalte in Fehlermeldungen
Beispielregeln
Rule Tests
```

## Risiko: Normalisierung erzeugt False Positives

Gegenmaßnahme:

```text
Normalisierung pro Feld konfigurierbar machen.
Nicht global erzwingen.
```

Beispiel: `sepaCode` sollte nicht german-folded werden.

---

# 29. Konkrete Roadmap

## Milestone 1: Core Engine

```text
Dauerziel: funktionsfähiger Kern
```

Lieferumfang:

```text
- Field Schema Model
- Normalizer
- Operatoren
- Rule AST
- Compiler
- Evaluator
- manuell erstellte AST-Tests ohne Parser
```

Ziel:

```text
Regeln können programmatisch erstellt und ausgewertet werden.
```

---

## Milestone 2: YAML Field Schema

Lieferumfang:

```text
- YAML Loader
- Schema Validation
- Normalizer Profiles
```

Ziel:

```text
Felder sind konfigurierbar.
```

---

## Milestone 3: DSL Parser

Lieferumfang:

```text
- Lexer
- Parser
- AST
- Syntaxfehler mit Zeile/Spalte
```

Ziel:

```text
.rule-Dateien werden gelesen.
```

---

## Milestone 4: Rule Validation + Compilation

Lieferumfang:

```text
- Feldprüfung
- Operatorprüfung
- Typprüfung
- Kompilierung zu CompiledRule
```

Ziel:

```text
Ungültige Regeln werden vor Runtime erkannt.
```

---

## Milestone 5: Public API + Examples

Lieferumfang:

```text
- RuleEngine.load(...)
- RuleEngine.evaluate(...)
- Transaction Beispiel
- Report Beispiel
```

Ziel:

```text
Engine ist einfach integrierbar.
```

---

## Milestone 6: Benchmark + Optimierung

Lieferumfang:

```text
- JMH Benchmarks
- Cost-based AND ordering
- PreparedRuleContext
- Allocation-Optimierungen
```

Ziel:

```text
Performance ist messbar und stabil.
```

---

# 30. Meine finale Empfehlung

Ich würde die Engine so bauen:

```text
1. YAML für Field Schema
2. Eigene kleine DSL für Rules
3. Interner AST unabhängig vom Format
4. Validierung gegen Field Schema
5. Kompilierung in performante Evaluatoren
6. Prepared Context pro Input
7. Normalisierung statt Regelduplikation
8. Regex erst in V2 oder kontrolliert als teurer Operator
9. AND short-circuit + cost sorting
10. OR short-circuit, aber V1 ohne Reordering
```

Der wichtigste technische Leitsatz:

```text
Die DSL ist für Menschen.
Der AST ist für Struktur.
Die Compiled Rules sind für Performance.
```

Und der wichtigste Produkt-Leitsatz:

```text
Regeln müssen einfacher zu lesen sein als Code,
aber schneller laufen als eine interpretierte Konfiguration.
```
