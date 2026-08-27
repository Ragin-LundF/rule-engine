package ruleengine.evaluator.compiled

import ruleengine.compiler.Compiler
import ruleengine.core.domain.dto.ConditionVerdict
import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.dsl.parser.Parser
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.RuleContext
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A field the record carries but whose value cannot be read as its declared type is undecided.
 *
 * `RULE-SPEC.md` lists this as one of the sources of an undecided condition, alongside an absent
 * field and an unpublished variable — but only the other two were covered. `PreparedRuleContext`
 * simply does not prepare a value it cannot read, and every leaf answers `UNKNOWN` when its prepared
 * value is absent, so the two arrive at the same verdict by different routes.
 */
class UnreadableValueTest {

    private val schema = FieldSchema(
        name = "unreadable-schema",
        fields = listOf(
            FieldId(value = "count") to FieldType.INTEGER,
            FieldId(value = "amount") to FieldType.DECIMAL,
            FieldId(value = "active") to FieldType.BOOLEAN,
            FieldId(value = "openedAt") to FieldType.DATE,
            FieldId(value = "tags") to FieldType.STRING_SET,
        ).associate { (id, type) -> id to FieldDefinition(id = id, type = type) }
    )

    @Test
    fun `text where an integer is declared is undecided`() {
        assertEquals(
            expected = ConditionVerdict.UNKNOWN,
            actual = verdict(condition = "count > 1", fields = arrayOf("count" to "not-a-number")),
            message = "the field is present, but nothing about it can decide the comparison"
        )
    }

    @Test
    fun `text where a decimal is declared is undecided`() {
        assertEquals(
            expected = ConditionVerdict.UNKNOWN,
            actual = verdict(condition = "amount > 1", fields = arrayOf("amount" to "twelve")),
        )
    }

    @Test
    fun `a non-boolean where a boolean is declared is undecided`() {
        assertEquals(
            expected = ConditionVerdict.UNKNOWN,
            actual = verdict(condition = "active equals true", fields = arrayOf("active" to "yes")),
            message = "only true/false and their string spellings are readable as a boolean"
        )
    }

    @Test
    fun `an unparseable date is undecided`() {
        assertEquals(
            expected = ConditionVerdict.UNKNOWN,
            actual = verdict(
                condition = """openedAt > "2020-01-01"""",
                fields = arrayOf("openedAt" to "the third of May")
            ),
        )
    }

    @Test
    fun `a scalar where a string set is declared is undecided`() {
        assertEquals(
            expected = ConditionVerdict.UNKNOWN,
            actual = verdict(
                condition = """tags containsAny ["a"]""",
                fields = arrayOf("tags" to 42)
            ),
        )
    }

    // ── a readable value of the same shape still decides ──────────────────────

    @Test
    fun `a numeric string where an integer is declared still decides`() {
        assertEquals(
            expected = ConditionVerdict.TRUE,
            actual = verdict(condition = "count > 1", fields = arrayOf("count" to "5")),
            message = "a string that reads as a number is readable, and must not be swept up"
        )
    }

    @Test
    fun `a readable boolean string still decides`() {
        assertEquals(
            expected = ConditionVerdict.TRUE,
            actual = verdict(condition = "active equals true", fields = arrayOf("active" to "true")),
        )
    }

    private fun verdict(condition: String, fields: Array<Pair<String, Any?>>): ConditionVerdict {
        val rule = """
            rule "unreadable-test" {
              description "unreadable values are undecided"
              when
                $condition
              then
                flag "ok"

              not_exists
                flag "undecided"
            }
        """.trimIndent()
        val compiled = Compiler.compileRules(asts = Parser(input = rule).parseRules(), schema = schema).single()
        val prepared = PreparedRuleContext.prepare(ctx = RuleContext.of(*fields), schema = schema)
        return compiled.expression.evaluate(context = prepared, trace = null)
    }
}
