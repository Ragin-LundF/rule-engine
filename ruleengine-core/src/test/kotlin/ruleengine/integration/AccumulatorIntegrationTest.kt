package ruleengine.integration

import ruleengine.compiler.Compiler
import ruleengine.compiler.Validator
import ruleengine.core.domain.dto.EvaluationResult
import ruleengine.core.domain.dto.NormalizerId
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
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * End-to-end behaviour of list variables: `add` accumulates, `$name contains` reads back.
 *
 * The scenario throughout is support-ticket triage, the case the feature was built for: several rules
 * reach the same topic by different evidence, each guards itself on whether that topic is already
 * assigned, and the guard is what stops the second rule doing the expensive text matching again.
 */
class AccumulatorIntegrationTest {

    private val schema = FieldSchema(
        name = "tickets",
        fields = mapOf(
            // Normalised like a real free-text field, so the guards are compared against the same
            // lowercase form the rules are written in.
            FieldId(value = "subject") to FieldDefinition(
                id = FieldId(value = "subject"),
                type = FieldType.TEXT,
                normalizers = listOf(NormalizerId(value = "trim"), NormalizerId(value = "lowercase"))
            ),
            FieldId(value = "channel") to FieldDefinition(
                id = FieldId(value = "channel"),
                type = FieldType.TEXT
            ),
            FieldId(value = "attachmentCount") to FieldDefinition(
                id = FieldId(value = "attachmentCount"),
                type = FieldType.INTEGER
            )
        )
    )

    /**
     * Four rules, two of which reach "billing" by different evidence. The guard on the second is the
     * whole point of the feature.
     */
    private val triageRules = """
        rule "billing-from-refund" {
          description "d"
          when
            not ${'$'}topics contains "billing"
            and subject contains "refund"
          then
            topic "billing"
            add "billing" to topics
        }
        rule "billing-from-payment" {
          description "d"
          when
            not ${'$'}topics contains "billing"
            and subject contains "payment"
          then
            topic "billing"
            add "billing" to topics
        }
        rule "card-issue" {
          description "d"
          when
            not ${'$'}topics contains "cards"
            and subject contains "card"
          then
            topic "cards"
            add "cards" to topics
        }
        rule "shipping" {
          description "d"
          when
            not ${'$'}topics contains "shipping"
            and subject contains "parcel"
          then
            topic "shipping"
            add "shipping" to topics
        }
    """.trimIndent()

    // ── evaluation ────────────────────────────────────────────────────────────

    @Test
    fun `a rule whose topic is already assigned does not fire again`() {
        val result = evaluate(
            rules = triageRules,
            "subject" to "Card declined at checkout, please refund the payment",
        )

        // "payment" is in the subject, so billing-from-payment would match on its own — the guard is
        // the only reason it does not.
        assertEquals(
            expected = listOf("billing-from-refund", "card-issue"),
            actual = result.matches.map { match -> match.ruleId },
        )
    }

    @Test
    fun `the accumulator holds each topic once, in the order it was added`() {
        val result = evaluate(
            rules = triageRules,
            "subject" to "Card declined at checkout, please refund the payment",
        )

        assertEquals(expected = listOf("billing", "cards"), actual = result.variables["topics"])
    }

    @Test
    fun `a subject matching nothing leaves the accumulator unset`() {
        val result = evaluate(rules = triageRules, "subject" to "thank you for your help")

        assertTrue(actual = result.matches.isEmpty())
        assertFalse(actual = result.variables.containsKey(key = "topics"))
    }

    @Test
    fun `an unguarded rule adding a value already present does not duplicate it`() {
        val result = evaluate(
            rules = """
                rule "first" {
                  description "d"
                  when
                    subject contains "refund"
                  then
                    add "billing" to topics
                }
                rule "second" {
                  description "d"
                  when
                    subject contains "payment"
                  then
                    add "billing" to topics
                }
            """.trimIndent(),
            "subject" to "refund the payment",
        )

        // Both rules ran — de-duplication happens in the list, not by suppressing the rule.
        assertEquals(expected = 2, actual = result.matches.size)
        assertEquals(expected = listOf("billing"), actual = result.variables["topics"])
    }

    @Test
    fun `an action of the adding rule reads the accumulator after the append`() {
        val result = evaluate(
            rules = """
                rule "billing" {
                  description "d"
                  when
                    subject contains "refund"
                  then
                    add "billing" to topics
                    topic ${'$'}topics
                }
            """.trimIndent(),
            "subject" to "please refund",
        )

        assertEquals(expected = listOf("billing"), actual = result.matches.single().actions.single().arguments.single())
    }

    @Test
    fun `an add clause in an else block accumulates on the false branch`() {
        val result = evaluate(
            rules = """
                rule "triage" {
                  description "d"
                  when
                    subject contains "refund"
                  then
                    add "billing" to topics
                  else
                    add "unclassified" to topics
                }
            """.trimIndent(),
            "subject" to "where is my parcel",
        )

        assertEquals(expected = RuleBranch.ELSE, actual = result.matches.single().branch)
        assertEquals(expected = listOf("unclassified"), actual = result.variables["topics"])
    }

    @Test
    fun `a value added before a stop is still published`() {
        val result = evaluate(
            rules = """
                rule "halting" {
                  description "d"
                  when
                    subject contains "refund"
                  then
                    add "billing" to topics
                    stop
                }
                rule "never-reached" {
                  description "d"
                  when
                    subject contains "refund"
                  then
                    add "other" to topics
                }
            """.trimIndent(),
            "subject" to "please refund",
        )

        assertEquals(expected = "halting", actual = result.stoppedBy)
        assertEquals(expected = listOf("billing"), actual = result.variables["topics"])
    }

    /**
     * `or` is not short-circuited away by the guard: a false first operand still lets the second
     * decide. Only `and` skips the work behind a failed guard.
     */
    @Test
    fun `an or keeps evaluating its second operand when the guard is false`() {
        val result = evaluate(
            rules = """
                rule "either" {
                  description "d"
                  when
                    ${'$'}topics contains "billing"
                    or subject contains "parcel"
                  then
                    topic "shipping"
                }
            """.trimIndent(),
            "subject" to "where is my parcel",
        )

        assertEquals(expected = "either", actual = result.matches.single().ruleId)
    }

    @Test
    fun `an add clause accepts a field value`() {
        val result = evaluate(
            rules = """
                rule "by-channel" {
                  description "d"
                  when
                    subject contains "refund"
                  then
                    add channel to sources
                }
            """.trimIndent(),
            "subject" to "please refund",
            "channel" to "email",
        )

        assertEquals(expected = listOf("email"), actual = result.variables["sources"])
    }

    @Test
    fun `an add of an absent field creates the list but adds nothing`() {
        val result = evaluate(
            rules = """
                rule "by-channel" {
                  description "d"
                  when
                    subject contains "refund"
                  then
                    add channel to sources
                }
            """.trimIndent(),
            "subject" to "please refund",
        )

        assertEquals(expected = emptyList<Any?>(), actual = result.variables["sources"])
    }

    @Test
    fun `a number is found again regardless of how it was written`() {
        val result = evaluate(
            rules = """
                rule "collect" {
                  description "d"
                  when
                    attachmentCount > 0
                  then
                    add attachmentCount to counts
                }
                rule "guarded" {
                  description "d"
                  when
                    ${'$'}counts contains 2
                  then
                    topic "has-two"
                }
            """.trimIndent(),
            "subject" to "x",
            "attachmentCount" to 2,
        )

        // The list holds the integer 2; the guard writes the literal 2. They must find each other.
        assertEquals(expected = listOf("collect", "guarded"), actual = result.matches.map { it.ruleId })
    }

    @Test
    fun `an accumulator does not leak into the next evaluation of the same context`() {
        val engine = RuleEngine(compiledRules = compile(rules = triageRules))
        val prepared = prepare("subject" to "please refund")

        val first = engine.evaluate(prepared = prepared)
        val second = engine.evaluate(prepared = prepared)

        assertEquals(expected = listOf("billing"), actual = first.variables["topics"])
        assertEquals(expected = listOf("billing"), actual = second.variables["topics"])
    }

    // ── validation ────────────────────────────────────────────────────────────

    /** The case the scope relaxation exists for: the first rule guards on the list it creates. */
    @Test
    fun `a rule may read the accumulator it is itself the first to write`() {
        assertTrue(actual = errorsOf(rules = triageRules).isEmpty())
    }

    @Test
    fun `two rules adding to the same name produce no warning`() {
        val diagnostics = validate(
            rules = """
                rule "first" {
                  description "d"
                  when
                    subject contains "a"
                  then
                    add "x" to topics
                }
                rule "second" {
                  description "d"
                  when
                    subject contains "b"
                  then
                    add "y" to topics
                }
            """.trimIndent()
        )

        assertTrue(actual = diagnostics.isEmpty())
    }

    @Test
    fun `two rules setting the same name still warn`() {
        val warnings = validate(
            rules = """
                rule "first" {
                  description "d"
                  when
                    subject contains "a"
                  then
                    set topic = "x"
                }
                rule "second" {
                  description "d"
                  when
                    subject contains "b"
                  then
                    set topic = "y"
                }
            """.trimIndent()
        ).filter { diagnostic -> diagnostic.severity == Severity.WARNING }

        assertEquals(expected = 1, actual = warnings.size)
    }

    @Test
    fun `a name written by both set and add is an error`() {
        val errors = errorsOf(
            rules = """
                rule "sets" {
                  description "d"
                  when
                    subject contains "a"
                  then
                    set topics = "x"
                }
                rule "adds" {
                  description "d"
                  when
                    subject contains "b"
                  then
                    add "y" to topics
                }
            """.trimIndent()
        )

        assertEquals(expected = 1, actual = errors.size)
        assertContains(charSequence = errors[0].message, other = "both a 'set' and an 'add' clause")
    }

    @Test
    fun `an add target named like a schema field is an error`() {
        val errors = errorsOf(
            rules = """
                rule "adds" {
                  description "d"
                  when
                    subject contains "a"
                  then
                    add "x" to subject
                }
            """.trimIndent()
        )

        assertEquals(expected = 1, actual = errors.size)
        assertContains(charSequence = errors[0].message, other = "name of a schema field")
    }

    /**
     * The relaxation is per rule, not per file: a name only some *later* rule accumulates is still a
     * forward reference.
     */
    @Test
    fun `reading an accumulator written only by a later rule is still an error`() {
        val errors = errorsOf(
            rules = """
                rule "reads" {
                  description "d"
                  when
                    ${'$'}topics contains "billing"
                  then
                    topic "x"
                }
                rule "writes" {
                  description "d"
                  when
                    subject contains "a"
                  then
                    add "billing" to topics
                }
            """.trimIndent()
        )

        assertEquals(expected = 1, actual = errors.size)
        assertContains(charSequence = errors[0].message, other = "unknown variable '\$topics'")
    }

    @Test
    fun `a typo in an accumulator read is reported with a suggestion`() {
        val errors = errorsOf(
            rules = """
                rule "adds" {
                  description "d"
                  when
                    not ${'$'}topics contains "billing"
                  then
                    add "billing" to topics
                }
                rule "reads" {
                  description "d"
                  when
                    ${'$'}topcis contains "billing"
                  then
                    topic "x"
                }
            """.trimIndent()
        )

        assertEquals(expected = 1, actual = errors.size)
        assertContains(charSequence = errors[0].message, other = "unknown variable '\$topcis'")
        assertEquals(expected = "Did you mean '\$topics'?", actual = errors[0].suggestion)
    }

    @Test
    fun `an unknown field in an add value is an error`() {
        val errors = errorsOf(
            rules = """
                rule "adds" {
                  description "d"
                  when
                    subject contains "a"
                  then
                    add nosuchfield to topics
                }
            """.trimIndent()
        )

        assertEquals(expected = 1, actual = errors.size)
        assertContains(charSequence = errors[0].message, other = "Unknown field 'nosuchfield'")
    }

    /**
     * `add` is the first keyword reserved after actions could already be named anything, so a project
     * that declares an `add` action does exist. It has to be told to rename rather than left with
     * `add "x"` failing as a malformed accumulator clause.
     */
    @Test
    fun `an action named add in the action schema is an error`() {
        val actions = ActionSchema(
            actions = mapOf("add" to ActionDefinition(name = "add", argTypes = listOf(ActionArgType.STRING)))
        )

        val errors = Validator.validate(asts = emptyList(), schema = schema, actions = actions)
            .diagnostics.filter { diagnostic -> diagnostic.severity == Severity.ERROR }

        assertEquals(expected = 1, actual = errors.size)
        assertContains(charSequence = errors.single().message, other = "'add' is a rule keyword")
        assertContains(charSequence = errors.single().suggestion.orEmpty(), other = "append")
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun compile(rules: String) =
        Compiler.compileRules(asts = Parser(input = rules).parseRules(), schema = schema)

    private fun prepare(vararg fields: Pair<String, Any?>) =
        PreparedRuleContext.prepare(ctx = RuleContext.of(*fields), schema = schema)

    private fun evaluate(rules: String, vararg fields: Pair<String, Any?>): EvaluationResult =
        RuleEngine(compiledRules = compile(rules = rules)).evaluate(prepared = prepare(*fields))

    private fun validate(rules: String): List<ValidationDiagnostic> =
        Validator.validate(asts = Parser(input = rules).parseRules(), schema = schema).diagnostics

    private fun errorsOf(rules: String): List<ValidationDiagnostic> =
        validate(rules = rules).filter { it.severity == Severity.ERROR }
}
