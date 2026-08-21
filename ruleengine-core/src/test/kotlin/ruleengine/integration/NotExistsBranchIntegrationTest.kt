package ruleengine.integration

import ruleengine.compiler.Compiler
import ruleengine.compiler.Validator
import ruleengine.core.domain.dto.ConditionVerdict
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
import ruleengine.dsl.diagnostics.ParseException
import ruleengine.dsl.parser.Parser
import ruleengine.evaluator.RuleEngine
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.RuleContext
import ruleengine.evaluator.trace.dto.DecisionTree
import ruleengine.evaluator.trace.dto.NodeType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * End-to-end behaviour of the `not_exists` branch: a condition the record's data cannot decide.
 *
 * The other half of this feature is what it must **not** change, which is why the "a rule with no
 * not_exists block" cases below matter as much as the rest — every rule set written before the branch
 * existed has to keep its results.
 */
class NotExistsBranchIntegrationTest {

    private val schema = FieldSchema(
        name = "reports",
        fields = mapOf(
            FieldId(value = "amount") to FieldDefinition(
                id = FieldId(value = "amount"),
                type = FieldType.DECIMAL,
            ),
            FieldId(value = "country") to FieldDefinition(
                id = FieldId(value = "country"),
                type = FieldType.TEXT,
            ),
            FieldId(value = "balances") to FieldDefinition(
                id = FieldId(value = "balances"),
                type = FieldType.COLLECTION,
                fields = mapOf(
                    FieldId(value = "day") to FieldDefinition(
                        id = FieldId(value = "day"),
                        type = FieldType.DECIMAL,
                    ),
                ),
            ),
        ),
    )

    private val actionSchema = ActionSchema(
        actions = mapOf(
            "outcome" to ActionDefinition(name = "outcome", argTypes = listOf(ActionArgType.STRING)),
            "flag" to ActionDefinition(name = "flag", argTypes = listOf(ActionArgType.STRING)),
        )
    )

    private val threeBranchRule = """
        rule "balance-check" {
          description "A high enough balance passes, a low one fails, no balance at all is neither."
          when
            amount >= 1000

          then
            outcome "GREEN"

          else
            outcome "RED"

          not_exists
            outcome "UNKNOWN"
            flag "no-amount"
        }
    """.trimIndent()

    // ── branch selection ──────────────────────────────────────────────────────

    @Test
    fun `the then branch fires when the condition holds`() {
        val match = evaluate(rules = threeBranchRule, "amount" to 2000).matches.single()

        assertEquals(expected = RuleBranch.THEN, actual = match.branch)
        assertEquals(expected = listOf("GREEN"), actual = match.actions.single().arguments)
    }

    @Test
    fun `the else branch fires when the condition is decided and false`() {
        val match = evaluate(rules = threeBranchRule, "amount" to 10).matches.single()

        assertEquals(expected = RuleBranch.ELSE, actual = match.branch)
        assertEquals(expected = listOf("RED"), actual = match.actions.single().arguments)
    }

    @Test
    fun `the not_exists branch fires when the field the condition reads is absent`() {
        val result = evaluate(rules = threeBranchRule, "country" to "DE")
        val match = result.matches.single()

        assertEquals(expected = RuleBranch.NOT_EXISTS, actual = match.branch)
        assertEquals(
            expected = listOf("UNKNOWN", "no-amount"),
            actual = match.actions.flatMap { action -> action.arguments.map { argument -> argument.toString() } },
        )
    }

    @Test
    fun `a not_exists branch is not a match, so the rule is not reported as matched`() {
        val result = RuleEngine(compiledRules = compile(rules = threeBranchRule))
            .evaluate(prepared = prepare("country" to "DE"), includeTrace = true)

        assertEquals(expected = 1, actual = result.matches.size)
        assertEquals(expected = emptyList(), actual = traceOf(result = result).matchedRules)
    }

    @Test
    fun `a null value counts as absent`() {
        val match = evaluate(rules = threeBranchRule, "amount" to null).matches.single()

        assertEquals(expected = RuleBranch.NOT_EXISTS, actual = match.branch)
    }

    @Test
    fun `the not_exists branch publishes variables like any other branch`() {
        val result = evaluate(
            rules = """
                rule "records-the-gap" {
                  description "Records that the amount was missing."
                  when
                    amount >= 1000
                  then
                    outcome "GREEN"
                  not_exists
                    add "amount" to missingFields
                }
            """.trimIndent(),
            "country" to "DE",
        )

        assertEquals(expected = listOf("amount"), actual = result.variables["missingFields"])
    }

    @Test
    fun `a not_exists branch can end the run`() {
        val result = evaluate(
            rules = """
                rule "guard" {
                  description "Nothing below can be judged without the amount."
                  when
                    amount >= 0
                  then
                    outcome "GREEN"
                  not_exists
                    flag "no-amount"
                    stop
                }
                rule "never-reached" {
                  description "Runs only when the guard let the record through."
                  when
                    country == "DE"
                  then
                    outcome "RED"
                }
            """.trimIndent(),
            "country" to "DE",
        )

        assertEquals(expected = "guard", actual = result.stoppedBy)
        assertEquals(expected = 1, actual = result.matches.size)
    }

    // ── Kleene propagation ────────────────────────────────────────────────────

    @Test
    fun `an or whose other side holds is decided, not undecided`() {
        val match = evaluate(
            rules = branchedRule(condition = "amount >= 1000\n    or country == \"DE\""),
            "country" to "DE",
        ).matches.single()

        assertEquals(expected = RuleBranch.THEN, actual = match.branch)
    }

    @Test
    fun `an and whose other side is false is decided, not undecided`() {
        val match = evaluate(
            rules = branchedRule(condition = "amount >= 1000\n    and country == \"FR\""),
            "country" to "DE",
        ).matches.single()

        assertEquals(expected = RuleBranch.ELSE, actual = match.branch)
    }

    @Test
    fun `an and with nothing false and an undecided side is undecided`() {
        val match = evaluate(
            rules = branchedRule(condition = "amount >= 1000\n    and country == \"DE\""),
            "country" to "DE",
        ).matches.single()

        assertEquals(expected = RuleBranch.NOT_EXISTS, actual = match.branch)
    }

    @Test
    fun `count over a missing collection is still zero, so the condition is decided`() {
        val match = evaluate(
            rules = branchedRule(condition = "count(balances) > 0"),
            "country" to "DE",
        ).matches.single()

        assertEquals(expected = RuleBranch.ELSE, actual = match.branch)
    }

    @Test
    fun `avg over a missing collection is missing, so the condition is undecided`() {
        val match = evaluate(
            rules = branchedRule(condition = "avg(balances.day) > 0"),
            "country" to "DE",
        ).matches.single()

        assertEquals(expected = RuleBranch.NOT_EXISTS, actual = match.branch)
    }

    @Test
    fun `a variable no rule has published leaves the condition undecided`() {
        val match = evaluate(
            rules = branchedRule(condition = "${'$'}turnover >= 1000"),
            "country" to "DE",
        ).matches.single()

        assertEquals(expected = RuleBranch.NOT_EXISTS, actual = match.branch)
    }

    // ── what must not change ──────────────────────────────────────────────────

    @Test
    fun `without a not_exists branch an absent field still takes the else branch`() {
        val match = evaluate(
            rules = """
                rule "balance-check" {
                  description "The behaviour of every rule written before not_exists existed."
                  when
                    amount >= 1000
                  then
                    outcome "GREEN"
                  else
                    outcome "RED"
                }
            """.trimIndent(),
            "country" to "DE",
        ).matches.single()

        assertEquals(expected = RuleBranch.ELSE, actual = match.branch)
        assertEquals(expected = listOf("RED"), actual = match.actions.single().arguments)
    }

    @Test
    fun `without a not_exists branch a guarded accumulator still fires on the first record`() {
        val result = evaluate(
            rules = """
                rule "claims-the-topic" {
                  description "The guarded-accumulator pattern: the list is empty, so the guard passes."
                  when
                    not ${'$'}topics contains "billing"
                    and country == "DE"
                  then
                    outcome "GREEN"
                    add "billing" to topics
                }
            """.trimIndent(),
            "country" to "DE",
        )

        assertEquals(expected = RuleBranch.THEN, actual = result.matches.single().branch)
        assertEquals(expected = listOf("billing"), actual = result.variables["topics"])
    }

    @Test
    fun `with a not_exists branch the same guard is undecided instead`() {
        val match = evaluate(
            rules = """
                rule "claims-the-topic" {
                  description "The same guard, in a rule that asked to hear about missing data."
                  when
                    not ${'$'}topics contains "billing"
                    and country == "DE"
                  then
                    outcome "GREEN"
                  not_exists
                    flag "no-topics"
                }
            """.trimIndent(),
            "country" to "DE",
        ).matches.single()

        assertEquals(expected = RuleBranch.NOT_EXISTS, actual = match.branch)
    }

    // ── isAvailable ───────────────────────────────────────────────────────────

    @Test
    fun `isAvailable answers a real boolean for a present and an absent field`() {
        assertEquals(
            expected = RuleBranch.THEN,
            actual = evaluate(rules = branchedRule(condition = "isAvailable(amount)"), "amount" to 5)
                .matches.single().branch,
        )
        assertEquals(
            expected = RuleBranch.ELSE,
            actual = evaluate(rules = branchedRule(condition = "isAvailable(amount)"), "country" to "DE")
                .matches.single().branch,
        )
    }

    @Test
    fun `isAvailable guards a condition that would otherwise be undecided`() {
        val match = evaluate(
            rules = branchedRule(condition = "isAvailable(amount)\n    and amount >= 1000"),
            "country" to "DE",
        ).matches.single()

        assertEquals(expected = RuleBranch.ELSE, actual = match.branch)
    }

    @Test
    fun `isAvailable reads a structure path and a variable`() {
        assertEquals(
            expected = RuleBranch.ELSE,
            actual = evaluate(rules = branchedRule(condition = "isAvailable(balances)"), "amount" to 1)
                .matches.single().branch,
        )
        assertEquals(
            expected = RuleBranch.ELSE,
            actual = evaluate(rules = branchedRule(condition = "isAvailable(${'$'}turnover)"), "amount" to 1)
                .matches.single().branch,
        )
    }

    @Test
    fun `not isAvailable is the readable form of a missing-data rule`() {
        val match = evaluate(
            rules = branchedRule(condition = "not isAvailable(amount)"),
            "country" to "DE",
        ).matches.single()

        assertEquals(expected = RuleBranch.THEN, actual = match.branch)
    }

    // ── trace ─────────────────────────────────────────────────────────────────

    @Test
    fun `the trace records the undecided verdict and the branch it selected`() {
        val result = RuleEngine(compiledRules = compile(rules = threeBranchRule))
            .evaluate(prepared = prepare("country" to "DE"), includeTrace = true)

        val ruleNode = traceOf(result = result).root?.children?.single { node -> node.type == NodeType.RULE }
        assertEquals(expected = ConditionVerdict.UNKNOWN, actual = ruleNode?.verdict)
        assertEquals(expected = RuleBranch.NOT_EXISTS, actual = ruleNode?.branch)
        assertEquals(expected = false, actual = ruleNode?.result)
    }

    @Test
    fun `a node other than the rule carries no branch`() {
        val result = RuleEngine(compiledRules = compile(rules = threeBranchRule))
            .evaluate(prepared = prepare("amount" to 2000), includeTrace = true)

        val condition = traceOf(result = result).root?.children?.single()?.children?.single()
        assertEquals(expected = ConditionVerdict.TRUE, actual = condition?.verdict)
        assertNull(actual = condition?.branch)
    }

    // ── parsing and validation ────────────────────────────────────────────────

    @Test
    fun `a not_exists block without an else block is accepted`() {
        val rules = """
            rule "only-not-exists" {
              description "No else branch, just a missing-data branch."
              when
                amount >= 1000
              then
                outcome "GREEN"
              not_exists
                flag "no-amount"
            }
        """.trimIndent()

        assertEquals(expected = emptyList(), actual = errorsOf(rules = rules))
    }

    @Test
    fun `an empty not_exists block is rejected`() {
        val failure = runCatching {
            Parser(
                input = """
                    rule "empty" {
                      description "d"
                      when
                        amount >= 1000
                      then
                        outcome "GREEN"
                      not_exists
                    }
                """.trimIndent()
            ).parseRules()
        }.exceptionOrNull()

        assertTrue(actual = failure is ParseException, message = "got: $failure")
        assertTrue(
            actual = failure.message?.contains(other = "Empty 'not_exists' block") == true,
            message = "got: ${failure.message}",
        )
    }

    @Test
    fun `a second not_exists block is rejected`() {
        val failure = runCatching {
            Parser(
                input = """
                    rule "twice" {
                      description "d"
                      when
                        amount >= 1000
                      then
                        outcome "GREEN"
                      not_exists
                        flag "a"
                      not_exists
                        flag "b"
                    }
                """.trimIndent()
            ).parseRules()
        }.exceptionOrNull()

        assertTrue(
            actual = failure?.message?.contains(other = "Duplicate 'not_exists' block") == true,
            message = "got: ${failure?.message}",
        )
    }

    @Test
    fun `an else block written after not_exists is rejected as an ordering mistake`() {
        val failure = runCatching {
            Parser(
                input = """
                    rule "out-of-order" {
                      description "d"
                      when
                        amount >= 1000
                      then
                        outcome "GREEN"
                      not_exists
                        flag "a"
                      else
                        outcome "RED"
                    }
                """.trimIndent()
            ).parseRules()
        }.exceptionOrNull()

        assertTrue(
            actual = failure?.message?.contains(other = "is out of place") == true,
            message = "got: ${failure?.message}",
        )
    }

    @Test
    fun `an action may not be named not_exists`() {
        val diagnostics = Validator.validate(
            asts = Parser(input = threeBranchRule).parseRules(),
            schema = schema,
            actions = ActionSchema(
                actions = actionSchema.actions +
                    ("not_exists" to ActionDefinition(name = "not_exists", argTypes = emptyList()))
            ),
        ).diagnostics

        assertTrue(
            actual = diagnostics.any { diagnostic ->
                diagnostic.severity == Severity.ERROR && "not_exists" in diagnostic.message
            },
            message = "got: $diagnostics",
        )
    }

    @Test
    fun `an unknown action in a not_exists block is reported`() {
        val rules = """
            rule "bad-branch" {
              description "d"
              when
                amount >= 1000
              then
                outcome "GREEN"
              not_exists
                nosuchaction "x"
            }
        """.trimIndent()

        assertTrue(
            actual = errorsOf(rules = rules).any { "nosuchaction" in it.message },
            message = "got: ${errorsOf(rules = rules)}",
        )
    }

    private fun traceOf(result: EvaluationResult): DecisionTree {
        return assertIs<DecisionTree>(value = result.trace)
    }

    private fun branchedRule(condition: String): String {
        return """
            rule "branched" {
              description "Reports which branch a condition selected."
              when
                $condition
              then
                outcome "GREEN"
              else
                outcome "RED"
              not_exists
                outcome "UNKNOWN"
            }
        """.trimIndent()
    }

    private fun compile(rules: String) =
        Compiler.compileRules(asts = Parser(input = rules).parseRules(), schema = schema)

    private fun prepare(vararg fields: Pair<String, Any?>) =
        PreparedRuleContext.prepare(ctx = RuleContext.of(*fields), schema = schema)

    private fun evaluate(rules: String, vararg fields: Pair<String, Any?>): EvaluationResult =
        RuleEngine(compiledRules = compile(rules = rules)).evaluate(prepared = prepare(*fields))

    private fun errorsOf(rules: String): List<ValidationDiagnostic> =
        Validator.validate(asts = Parser(input = rules).parseRules(), schema = schema, actions = actionSchema)
            .diagnostics
            .filter { it.severity == Severity.ERROR }
}
