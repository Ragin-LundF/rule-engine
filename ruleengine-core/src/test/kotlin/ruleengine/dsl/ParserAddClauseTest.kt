package ruleengine.dsl

import ruleengine.dsl.ast.AssignmentKindAst
import ruleengine.dsl.ast.FieldAccessAst
import ruleengine.dsl.ast.LiteralValueAst
import ruleengine.dsl.ast.StringLiteral
import ruleengine.dsl.ast.VariableAssignmentAst
import ruleengine.dsl.ast.VariableRefAst
import ruleengine.dsl.diagnostics.ParseException
import ruleengine.dsl.parser.Parser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The `add <value> to <name>` clause, which shares [VariableAssignmentAst] with `set` and is told
 * apart by [AssignmentKindAst].
 */
class ParserAddClauseTest {

    @Test
    fun `add clause becomes an assignment of kind ADD`() {
        val rule = parseSingle(
            body = """
                when
                  amount > 0
                then
                  add "billing" to topics
            """.trimIndent()
        )

        val assignment = rule.assignments.single()
        assertEquals(expected = "topics", actual = assignment.name)
        assertEquals(expected = AssignmentKindAst.ADD, actual = assignment.kind)
        val value = assertIs<LiteralValueAst>(value = assignment.expression)
        assertEquals(expected = "billing", actual = assertIs<StringLiteral>(value = value.literal).value)
    }

    @Test
    fun `set clause is still an assignment of kind SET`() {
        val rule = parseSingle(
            body = """
                when
                  amount > 0
                then
                  set total = 1
            """.trimIndent()
        )

        assertEquals(expected = AssignmentKindAst.SET, actual = rule.assignments.single().kind)
    }

    @Test
    fun `set and add in one block keep their source order`() {
        val rule = parseSingle(
            body = """
                when
                  amount > 0
                then
                  add "first" to topics
                  set total = 1
                  add "second" to topics
            """.trimIndent()
        )

        assertEquals(
            expected = listOf("topics", "total", "topics"),
            actual = rule.assignments.map { assignment -> assignment.name },
        )
        assertEquals(
            expected = listOf(AssignmentKindAst.ADD, AssignmentKindAst.SET, AssignmentKindAst.ADD),
            actual = rule.assignments.map { assignment -> assignment.kind },
        )
    }

    @Test
    fun `add clause in an else block lands in the else assignments`() {
        val rule = parseSingle(
            body = """
                when
                  amount > 0
                then
                  flag "ok"
                else
                  add "unmatched" to topics
            """.trimIndent()
        )

        assertTrue(actual = rule.assignments.isEmpty())
        assertEquals(expected = AssignmentKindAst.ADD, actual = rule.elseAssignments.single().kind)
    }

    @Test
    fun `add clause accepts a field as its value`() {
        val rule = parseSingle(
            body = """
                when
                  amount > 0
                then
                  add category to topics
            """.trimIndent()
        )

        assertIs<FieldAccessAst>(value = rule.assignments.single().expression)
    }

    @Test
    fun `add clause accepts another variable as its value`() {
        val rule = parseSingle(
            body = """
                when
                  amount > 0
                then
                  add ${'$'}derived to topics
            """.trimIndent()
        )

        assertIs<VariableRefAst>(value = rule.assignments.single().expression)
    }

    @Test
    fun `add clause without to is a parse error naming the keyword`() {
        val failure = assertFailsWith<ParseException> {
            parseSingle(
                body = """
                    when
                      amount > 0
                    then
                      add "billing" topics
                """.trimIndent()
            )
        }

        assertTrue(actual = failure.messageText.contains(other = "'to'"))
    }

    @Test
    fun `add clause with a dollar-prefixed target is a parse error`() {
        val failure = assertFailsWith<ParseException> {
            parseSingle(
                body = """
                    when
                      amount > 0
                    then
                      add "billing" to ${'$'}topics
                """.trimIndent()
            )
        }

        assertTrue(actual = failure.messageText.contains(other = "without the '\$' prefix"))
    }

    @Test
    fun `add clause after stop is rejected`() {
        val failure = assertFailsWith<ParseException> {
            parseSingle(
                body = """
                    when
                      amount > 0
                    then
                      stop
                      add "billing" to topics
                """.trimIndent()
            )
        }

        assertTrue(actual = failure.messageText.contains(other = "'stop' must be the last statement"))
    }

    /**
     * The kind is part of what an assignment *is*, not metadata like its position. Builder staleness
     * detection compares rule trees, so an `add` that equals a `set` would let a mode change go
     * unnoticed.
     */
    @Test
    fun `kind takes part in equality`() {
        val expression = LiteralValueAst(literal = StringLiteral(value = "billing"))
        val set = VariableAssignmentAst(name = "topics", expression = expression, kind = AssignmentKindAst.SET)
        val add = VariableAssignmentAst(name = "topics", expression = expression, kind = AssignmentKindAst.ADD)

        assertNotEquals(illegal = set, actual = add)
        assertNotEquals(illegal = set.hashCode(), actual = add.hashCode())
    }

    private fun parseSingle(body: String) = Parser(
        input = """
            rule "r" {
              description "d"
              $body
            }
        """.trimIndent()
    ).parseRules().single()
}
