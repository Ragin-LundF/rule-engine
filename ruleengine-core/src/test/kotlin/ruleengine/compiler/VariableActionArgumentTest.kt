package ruleengine.compiler

import ruleengine.core.domain.dto.EvaluationResult
import ruleengine.core.domain.dto.action.ActionArgType
import ruleengine.core.domain.dto.action.ActionDefinition
import ruleengine.core.domain.dto.action.ActionSchema
import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.core.errors.Severity
import ruleengine.core.errors.ValidationDiagnostic
import ruleengine.dsl.ast.AssignmentKindAst
import ruleengine.dsl.parser.Parser
import ruleengine.evaluator.RuleEngine
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.RuleContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The `variable_string` / `variable_list` argument types.
 *
 * They are opt-in metadata: an action that declares one gets its argument checked and the editor gets
 * something to complete against, while an action that declares a literal type keeps accepting a `$name`
 * unchecked, exactly as it always has. Both halves are tested, because the second is what keeps existing
 * projects from acquiring diagnostics they never had.
 */
class VariableActionArgumentTest {

    private val schema = FieldSchema(
        name = "orders",
        fields = mapOf(
            FieldId(value = "amount") to FieldDefinition(
                id = FieldId(value = "amount"),
                type = FieldType.DECIMAL,
            ),
        ),
    )

    private val actionSchema = ActionSchema(
        actions = mapOf(
            "reason" to ActionDefinition(name = "reason", argTypes = listOf(ActionArgType.VARIABLE_STRING)),
            "topics" to ActionDefinition(name = "topics", argTypes = listOf(ActionArgType.VARIABLE_LIST)),
            "label" to ActionDefinition(name = "label", argTypes = listOf(ActionArgType.STRING)),
            "score" to ActionDefinition(name = "score", argTypes = listOf(ActionArgType.INTEGER)),
        )
    )

    // ── declared: accepted ────────────────────────────────────────────────────

    @Test
    fun `a set variable satisfies a variable_string argument`() {
        val errors = errorsOf(
            rules = """
                rule "reports" {
                  description "d"
                  when
                    amount > 0
                  then
                    set why = "too-large"
                    reason ${'$'}why
                }
            """.trimIndent()
        )

        assertEquals(expected = emptyList(), actual = errors)
    }

    @Test
    fun `an add variable satisfies a variable_list argument`() {
        val errors = errorsOf(
            rules = """
                rule "reports" {
                  description "d"
                  when
                    amount > 0
                  then
                    add "billing" to matters
                    topics ${'$'}matters
                }
            """.trimIndent()
        )

        assertEquals(expected = emptyList(), actual = errors)
    }

    @Test
    fun `a variable an earlier rule published satisfies the declaration`() {
        val errors = errorsOf(
            rules = """
                rule "publishes" {
                  description "d"
                  when
                    amount > 0
                  then
                    set why = "too-large"
                }
                rule "consumes" {
                  description "d"
                  when
                    amount > 1
                  then
                    reason ${'$'}why
                }
            """.trimIndent()
        )

        assertEquals(expected = emptyList(), actual = errors)
    }

    // ── declared: rejected ────────────────────────────────────────────────────

    @Test
    fun `a literal where a variable is declared is an error`() {
        val errors = errorsOf(
            rules = """
                rule "reports" {
                  description "d"
                  when
                    amount > 0
                  then
                    reason "too-large"
                }
            """.trimIndent()
        )

        assertEquals(expected = 1, actual = errors.size)
        assertTrue(
            actual = errors.single().message.contains(other = "must be written as a variable reference"),
            message = "got: ${errors.single().message}",
        )
    }

    @Test
    fun `a set variable where a list is declared is an error`() {
        val errors = errorsOf(
            rules = """
                rule "reports" {
                  description "d"
                  when
                    amount > 0
                  then
                    set matters = "billing"
                    topics ${'$'}matters
                }
            """.trimIndent()
        )

        assertEquals(expected = 1, actual = errors.size)
        assertTrue(
            actual = errors.single().message.contains(other = "is written with a 'set' clause"),
            message = "got: ${errors.single().message}",
        )
    }

    @Test
    fun `a list variable where a plain value is declared is an error`() {
        val errors = errorsOf(
            rules = """
                rule "reports" {
                  description "d"
                  when
                    amount > 0
                  then
                    add "billing" to why
                    reason ${'$'}why
                }
            """.trimIndent()
        )

        assertEquals(expected = 1, actual = errors.size)
        assertTrue(
            actual = errors.single().message.contains(other = "is written with an 'add' clause"),
            message = "got: ${errors.single().message}",
        )
    }

    /** Already reported as an unknown variable — saying it twice would read as two problems. */
    @Test
    fun `an unknown variable is reported once, as an unknown variable`() {
        val errors = errorsOf(
            rules = """
                rule "reports" {
                  description "d"
                  when
                    amount > 0
                  then
                    reason ${'$'}nosuchvariable
                }
            """.trimIndent()
        )

        assertEquals(expected = 1, actual = errors.size)
        assertTrue(
            actual = errors.single().message.contains(other = "reads unknown variable"),
            message = "got: ${errors.single().message}",
        )
    }

    @Test
    fun `an extraction reference where a variable is declared is an error`() {
        val errors = errorsOf(
            rules = """
                rule "reports" {
                  description "d"
                  when
                    amount > 0
                  then
                    reason ${'$'}1
                }
            """.trimIndent()
        )

        assertTrue(
            actual = errors.any { it.message.contains(other = "must be written as a variable reference") },
            message = "got: $errors",
        )
    }

    // ── undeclared: unchanged ─────────────────────────────────────────────────

    @Test
    fun `a variable passed to a string action is still accepted with no diagnostic`() {
        val errors = errorsOf(
            rules = """
                rule "reports" {
                  description "d"
                  when
                    amount > 0
                  then
                    set why = "too-large"
                    label ${'$'}why
                }
            """.trimIndent()
        )

        assertEquals(expected = emptyList(), actual = errors)
    }

    @Test
    fun `a list variable passed to an integer action is still accepted with no diagnostic`() {
        val errors = errorsOf(
            rules = """
                rule "reports" {
                  description "d"
                  when
                    amount > 0
                  then
                    add "billing" to matters
                    score ${'$'}matters
                }
            """.trimIndent()
        )

        assertEquals(expected = emptyList(), actual = errors)
    }

    // ── evaluation: no compiler or evaluator change was needed ────────────────

    @Test
    fun `a variable_list argument reaches the consumer as a list`() {
        val result = evaluate(
            rules = """
                rule "reports" {
                  description "d"
                  when
                    amount > 0
                  then
                    add "billing" to matters
                    add "refund" to matters
                    topics ${'$'}matters
                }
            """.trimIndent(),
            "amount" to 5,
        )

        assertEquals(
            expected = listOf(listOf("billing", "refund")),
            actual = result.matches.single().actions.single { it.name == "topics" }.arguments,
        )
    }

    @Test
    fun `a variable argument of a rule that never published it arrives as null`() {
        val result = evaluate(
            rules = """
                rule "publishes" {
                  description "Never matches, so nothing is published."
                  when
                    amount > 100
                  then
                    set why = "too-large"
                }
                rule "consumes" {
                  description "d"
                  when
                    amount > 0
                  then
                    reason ${'$'}why
                }
            """.trimIndent(),
            "amount" to 5,
        )

        assertNull(actual = result.matches.single().actions.single().arguments.single())
    }

    private fun validate(rules: String): List<ValidationDiagnostic> {
        return Validator.validate(
            asts = Parser(input = rules).parseRules(),
            schema = schema,
            actions = actionSchema,
        ).diagnostics
    }

    private fun errorsOf(rules: String): List<ValidationDiagnostic> {
        return validate(rules = rules).filter { it.severity == Severity.ERROR }
    }

    private fun evaluate(rules: String, vararg fields: Pair<String, Any?>): EvaluationResult {
        val compiled = Compiler.compileRules(asts = Parser(input = rules).parseRules(), schema = schema)
        return RuleEngine(compiledRules = compiled).evaluate(
            prepared = PreparedRuleContext.prepare(ctx = RuleContext.of(*fields), schema = schema),
        )
    }

    @Test
    fun `the loader reads both spellings of each variable arg type`() {
        val loaded = ruleengine.schema.ActionSchemaLoader.loadFromString(
            content = """
                actions:
                  a:
                    argTypes: [variable_string]
                  b:
                    argTypes: [variableString]
                  c:
                    argTypes: [variable_list]
                  d:
                    argTypes: [variableList]
            """.trimIndent()
        )

        assertEquals(
            expected = listOf(
                ActionArgType.VARIABLE_STRING,
                ActionArgType.VARIABLE_STRING,
                ActionArgType.VARIABLE_LIST,
                ActionArgType.VARIABLE_LIST,
            ),
            actual = listOf("a", "b", "c", "d").map { name ->
                loaded.actions.getValue(name).argTypes.single()
            },
        )
    }

    @Test
    fun `an unknown arg type is still rejected`() {
        val failure = runCatching {
            ruleengine.schema.ActionSchemaLoader.loadFromString(
                content = """
                    actions:
                      a:
                        argTypes: [variable]
                """.trimIndent()
            )
        }.exceptionOrNull()

        assertTrue(
            actual = failure?.message?.contains(other = "variable") == true,
            message = "got: ${failure?.message}",
        )
    }

    @Test
    fun `an add and a set of the same name is still the pre-existing kind clash`() {
        val errors = errorsOf(
            rules = """
                rule "one" {
                  description "d"
                  when
                    amount > 0
                  then
                    set matters = "billing"
                }
                rule "two" {
                  description "d"
                  when
                    amount > 1
                  then
                    add "refund" to matters
                }
            """.trimIndent()
        )

        assertTrue(
            actual = errors.any { it.message.contains(other = "written by both a 'set' and an 'add' clause") },
            message = "got: $errors",
        )
    }

    @Test
    fun `an inherited variable is checked against the declaration too`() {
        val errors = Validator.validate(
            asts = Parser(
                input = """
                    rule "consumes" {
                      description "d"
                      when
                        amount > 0
                      then
                        topics ${'$'}why
                    }
                """.trimIndent()
            ).parseRules(),
            schema = schema,
            actions = actionSchema,
            inheritedVariables = mapOf("why" to AssignmentKindAst.SET),
        ).diagnostics.filter { it.severity == Severity.ERROR }

        assertEquals(expected = 1, actual = errors.size)
        assertTrue(
            actual = errors.single().message.contains(other = "is written with a 'set' clause"),
            message = "got: ${errors.single().message}",
        )
    }
}
