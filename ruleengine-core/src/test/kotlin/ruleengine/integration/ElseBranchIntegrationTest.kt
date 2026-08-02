package ruleengine.integration

import ruleengine.compiler.Compiler
import ruleengine.compiler.Validator
import ruleengine.core.domain.dto.EvaluationResult
import ruleengine.core.domain.dto.RuleBranch
import ruleengine.core.domain.dto.action.ActionArgType
import ruleengine.core.domain.dto.action.ActionDefinition
import ruleengine.core.domain.dto.action.ActionSchema
import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.core.errors.Severity
import ruleengine.core.errors.ValidationDiagnostic
import ruleengine.dsl.parser.Parser
import ruleengine.evaluator.RuleEngine
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.RuleContext
import ruleengine.evaluator.trace.dto.DecisionTree
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * End-to-end behaviour of the optional `else` branch: the condition's verdict selects a branch, and
 * exactly one of them ever produces output.
 */
class ElseBranchIntegrationTest {

    private val schema = FieldSchema(
        name = "orders",
        fields = mapOf(
            FieldId(value = "amount") to FieldDefinition(
                id = FieldId(value = "amount"),
                type = FieldType.DECIMAL
            ),
            FieldId(value = "reference") to FieldDefinition(
                id = FieldId(value = "reference"),
                type = FieldType.TEXT
            )
        )
    )

    private val actionSchema = ActionSchema(
        actions = mapOf(
            "label" to ActionDefinition(name = "label", argTypes = listOf(ActionArgType.STRING)),
            "score" to ActionDefinition(name = "score", argTypes = listOf(ActionArgType.INTEGER)),
        )
    )

    // ── evaluation ────────────────────────────────────────────────────────────

    @Test
    fun `the then branch fires when the condition holds`() {
        val result = evaluate(rules = tieredRule, "amount" to 2000)

        val match = result.matches.single()
        assertEquals(expected = "tier", actual = match.ruleId)
        assertEquals(expected = RuleBranch.THEN, actual = match.branch)
        assertEquals(expected = listOf("high"), actual = match.actions.single().arguments)
    }

    @Test
    fun `the else branch fires when the condition does not hold`() {
        val result = evaluate(rules = tieredRule, "amount" to 10)

        val match = result.matches.single()
        assertEquals(expected = "tier", actual = match.ruleId)
        assertEquals(expected = RuleBranch.ELSE, actual = match.branch)
        assertEquals(expected = listOf("low"), actual = match.actions.single().arguments)
    }

    @Test
    fun `a rule without an else branch still produces nothing when it does not match`() {
        val result = evaluate(
            rules = """
                rule "high-only" {
                  description "d"
                  when
                    amount > 1000
                  then
                    label "high"
                }
            """.trimIndent(),
            "amount" to 10
        )

        assertTrue(actual = result.matches.isEmpty(), message = "unexpected matches: ${result.matches}")
    }

    @Test
    fun `each branch publishes its own variables`() {
        val high = evaluate(rules = tieredRule, "amount" to 2000)
        val low = evaluate(rules = tieredRule, "amount" to 10)

        assertEquals(expected = BigDecimal("2"), actual = high.variables["tierLevel"])
        assertEquals(expected = BigDecimal("1"), actual = low.variables["tierLevel"])
        assertEquals(expected = mapOf("tierLevel" to BigDecimal("2")), actual = high.matches.single().assignments)
        assertEquals(expected = mapOf("tierLevel" to BigDecimal("1")), actual = low.matches.single().assignments)
    }

    @Test
    fun `a later rule reads a variable an else branch published`() {
        val result = evaluate(
            rules = """
                $tieredRule
                rule "cheap" {
                  description "d"
                  when
                    ${'$'}tierLevel == 1
                  then
                    label "cheap"
                }
            """.trimIndent(),
            "amount" to 10
        )

        assertEquals(expected = listOf("tier", "cheap"), actual = result.matches.map { it.ruleId })
        assertEquals(
            expected = listOf(RuleBranch.ELSE, RuleBranch.THEN),
            actual = result.matches.map { it.branch }
        )
    }

    @Test
    fun `declaration order is kept across mixed branches`() {
        val result = evaluate(
            rules = """
                rule "first" {
                  description "d"
                  when
                    amount > 1000
                  then
                    label "big"
                  else
                    label "small"
                }
                rule "second" {
                  description "d"
                  when
                    amount > 0
                  then
                    label "positive"
                }
                rule "third" {
                  description "d"
                  when
                    amount > 1000
                  then
                    label "big-again"
                  else
                    label "small-again"
                }
            """.trimIndent(),
            "amount" to 10
        )

        assertEquals(expected = listOf("first", "second", "third"), actual = result.matches.map { it.ruleId })
        assertEquals(
            expected = listOf(RuleBranch.ELSE, RuleBranch.THEN, RuleBranch.ELSE),
            actual = result.matches.map { it.branch }
        )
    }

    @Test
    fun `an extraction in an else branch resolves against the input`() {
        val result = evaluate(
            rules = """
                rule "reference" {
                  description "d"
                  when
                    amount > 1000
                  then
                    label "high"
                  else
                    extract reference regex("ref-([0-9]+)", 1) label ${'$'}1
                }
            """.trimIndent(),
            "amount" to 10,
            "reference" to "ref-42"
        )

        assertEquals(expected = listOf("42"), actual = result.matches.single().actions.single().arguments)
    }

    /**
     * The trace answers "did the condition hold", which an `else` branch producing output does not
     * change — otherwise a decision tree would contradict the `result` flag on its own rule node.
     */
    @Test
    fun `the trace does not count an else-fired rule as matched`() {
        val engine = RuleEngine(compiledRules = compile(rules = tieredRule))
        val result = engine.evaluate(prepared = prepare("amount" to 10), includeTrace = true)

        assertEquals(expected = RuleBranch.ELSE, actual = result.matches.single().branch)
        assertTrue(actual = assertIs<DecisionTree>(value = result.trace).matchedRules.isEmpty())
    }

    // ── validation ────────────────────────────────────────────────────────────

    @Test
    fun `else actions are checked against the action schema`() {
        val errors = errorsOf(
            rules = """
                rule "tier" {
                  description "d"
                  when
                    amount > 1000
                  then
                    label "high"
                  else
                    notify "ops"
                }
            """.trimIndent()
        )

        assertEquals(expected = 1, actual = errors.size)
        assertContains(charSequence = errors.single().message, other = "notify")
    }

    @Test
    fun `an else action argument of the wrong type is an error`() {
        val errors = errorsOf(
            rules = """
                rule "tier" {
                  description "d"
                  when
                    amount > 1000
                  then
                    label "high"
                  else
                    score "not-a-number"
                }
            """.trimIndent()
        )

        assertEquals(expected = 1, actual = errors.size)
        assertContains(charSequence = errors.single().message, other = "score")
    }

    @Test
    fun `an else extraction on an unknown field is an error`() {
        val errors = errorsOf(
            rules = """
                rule "tier" {
                  description "d"
                  when
                    amount > 1000
                  then
                    label "high"
                  else
                    extract nosuchfield regex("(.*)", 1) label ${'$'}1
                }
            """.trimIndent()
        )

        assertEquals(expected = 1, actual = errors.size)
        assertContains(charSequence = errors.single().message, other = "nosuchfield")
    }

    @Test
    fun `a variable read only by an else branch still has to be assigned earlier`() {
        val errors = errorsOf(
            rules = """
                rule "tier" {
                  description "d"
                  when
                    amount > 1000
                  then
                    label "high"
                  else
                    score ${'$'}unknownTotal
                }
            """.trimIndent()
        )

        assertEquals(expected = 1, actual = errors.size)
        assertContains(charSequence = errors.single().message, other = "unknown variable '\$unknownTotal'")
    }

    @Test
    fun `both branches of one rule may assign the same variable without a warning`() {
        val diagnostics = validate(rules = tieredRule)

        assertTrue(
            actual = diagnostics.none { it.message.contains(other = "is assigned by rule") },
            message = "unexpected re-assignment warning: $diagnostics"
        )
    }

    @Test
    fun `an action named else in the action schema is an error`() {
        val schemaWithKeyword = ActionSchema(
            actions = mapOf("else" to ActionDefinition(name = "else", argTypes = listOf(ActionArgType.STRING)))
        )

        val errors = Validator.validate(
            asts = emptyList(),
            schema = schema,
            actions = schemaWithKeyword,
        ).diagnostics.filter { it.severity == Severity.ERROR }

        assertEquals(expected = 1, actual = errors.size)
        assertContains(charSequence = errors.single().message, other = "'else' is a rule keyword")
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private val tieredRule = """
        rule "tier" {
          description "An order above 1000 is high tier, anything else is low tier."
          when
            amount > 1000
          then
            label "high"
            set tierLevel = 2
          else
            label "low"
            set tierLevel = 1
        }
    """.trimIndent()

    private fun compile(rules: String) =
        Compiler.compileRules(asts = Parser(input = rules).parseRules(), schema = schema)

    private fun prepare(vararg fields: Pair<String, Any?>) =
        PreparedRuleContext.prepare(ctx = RuleContext.of(*fields), schema = schema)

    private fun evaluate(rules: String, vararg fields: Pair<String, Any?>): EvaluationResult =
        RuleEngine(compiledRules = compile(rules = rules)).evaluate(prepared = prepare(*fields))

    private fun validate(rules: String): List<ValidationDiagnostic> =
        Validator.validate(asts = Parser(input = rules).parseRules(), schema = schema, actions = actionSchema)
            .diagnostics

    private fun errorsOf(rules: String): List<ValidationDiagnostic> =
        validate(rules = rules).filter { it.severity == Severity.ERROR }
}
