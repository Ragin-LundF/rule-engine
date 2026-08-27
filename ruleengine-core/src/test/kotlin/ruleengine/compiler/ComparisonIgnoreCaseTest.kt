package ruleengine.compiler

import ruleengine.core.domain.dto.ConditionVerdict
import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.dsl.ast.ComparisonExpressionAst
import ruleengine.dsl.parser.Parser
import ruleengine.evaluator.compiled.value.result.TextExpressionValue
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.RuleContext
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The `ignoreCase` modifier on a symbolic comparison.
 *
 * It was readable only after a named operator, so `purpose contains "rent" ignoreCase` worked while
 * `$topic == "Billing" ignoreCase` did not parse at all. That gap was not cosmetic: a variable and an
 * aggregate *always* take the symbolic path, and a normalizer declared on a field cannot reach a value
 * a rule computed — so there was no way to compare one case-insensitively.
 *
 * Both operands are folded once before any operator reads them, so every operator that compares text
 * honours the modifier from the same place.
 */
class ComparisonIgnoreCaseTest {

    private val schema = FieldSchema(
        name = "ignore-case-schema",
        fields = mapOf(
            FieldId(value = "purpose") to FieldDefinition(
                id = FieldId(value = "purpose"),
                type = FieldType.TEXT,
            ),
            FieldId(value = "other") to FieldDefinition(
                id = FieldId(value = "other"),
                type = FieldType.TEXT,
            ),
            FieldId(value = "tags") to FieldDefinition(
                id = FieldId(value = "tags"),
                type = FieldType.STRING_SET,
            ),
            FieldId(value = "amount") to FieldDefinition(
                id = FieldId(value = "amount"),
                type = FieldType.DECIMAL,
            ),
        )
    )

    // ── the gap it closes: a computed operand ─────────────────────────────────

    @Test
    fun `a variable compares case-insensitively with the modifier`() {
        assertEquals(
            expected = ConditionVerdict.TRUE,
            actual = verdictAfterSet(condition = """${'$'}topic == "Billing" ignoreCase""", published = "billing"),
            message = "a variable always takes the symbolic path, so this was previously unwritable"
        )
    }

    @Test
    fun `a variable is case-sensitive without the modifier`() {
        assertEquals(
            expected = ConditionVerdict.FALSE,
            actual = verdictAfterSet(condition = """${'$'}topic == "Billing"""", published = "billing"),
        )
    }

    // ── every operator that compares text honours it ──────────────────────────

    @Test
    fun `symbolic equality folds case`() {
        assertEquals(
            expected = ConditionVerdict.TRUE,
            actual = verdict("""purpose == "RENT" ignoreCase""", "purpose" to "rent"),
        )
        assertEquals(
            expected = ConditionVerdict.FALSE,
            actual = verdict("""purpose == "RENT"""", "purpose" to "rent"),
        )
    }

    @Test
    fun `symbolic inequality folds case`() {
        assertEquals(
            expected = ConditionVerdict.FALSE,
            actual = verdict("""purpose != "RENT" ignoreCase""", "purpose" to "rent"),
            message = "folded to the same value, so they are not unequal"
        )
    }

    @Test
    fun `a field-to-field comparison folds both sides`() {
        assertEquals(
            expected = ConditionVerdict.TRUE,
            actual = verdict("purpose == other ignoreCase", "purpose" to "Rent", "other" to "rENT"),
        )
    }

    @Test
    fun `contains folds case as a substring test`() {
        assertEquals(
            expected = ConditionVerdict.TRUE,
            actual = verdictAfterSet(condition = """${'$'}topic contains "BILL" ignoreCase""", published = "billing"),
        )
    }

    @Test
    fun `membership folds case on both sides`() {
        assertEquals(
            expected = ConditionVerdict.TRUE,
            actual = verdict("""purpose in tags ignoreCase""", "purpose" to "RENT", "tags" to listOf("rent")),
        )
        assertEquals(
            expected = ConditionVerdict.FALSE,
            actual = verdict("""purpose in tags""", "purpose" to "RENT", "tags" to listOf("rent")),
        )
    }

    // ── non-text operands are untouched ───────────────────────────────────────

    @Test
    fun `a numeric comparison is unaffected by the modifier`() {
        assertEquals(
            expected = ConditionVerdict.TRUE,
            actual = verdict("amount > 5 ignoreCase", "amount" to 10),
            message = "case is a property of text alone; folding must leave a number alone"
        )
    }

    // ── round trip ────────────────────────────────────────────────────────────

    @Test
    fun `the parsed AST carries the modifier, and drops it when absent`() {
        val withModifier = Parser(input = rule("""purpose == "RENT" ignoreCase""")).parseRules()
            .single().condition as ComparisonExpressionAst
        val without = Parser(input = rule("""purpose == "RENT"""")).parseRules()
            .single().condition as ComparisonExpressionAst

        assertEquals(expected = true, actual = withModifier.ignoreCase)
        assertEquals(expected = false, actual = without.ignoreCase)
    }

    /** The modifier is a keyword, so it cannot be read as an implicitly `and`-joined field. */
    @Test
    fun `the modifier does not swallow the next condition`() {
        val condition = """purpose == "RENT" ignoreCase
    and amount > 5"""

        assertEquals(
            expected = ConditionVerdict.TRUE,
            actual = verdict(condition, "purpose" to "rent", "amount" to 10),
        )
        assertEquals(
            expected = ConditionVerdict.FALSE,
            actual = verdict(condition, "purpose" to "rent", "amount" to 1),
            message = "the second condition must still be evaluated",
        )
    }

    private fun verdict(condition: String, vararg fields: Pair<String, Any?>): ConditionVerdict {
        val compiled = Compiler.compileRules(asts = Parser(input = rule(condition)).parseRules(), schema = schema)
        val prepared = PreparedRuleContext.prepare(ctx = RuleContext.of(*fields), schema = schema)
        return compiled.single().expression.evaluate(context = prepared, trace = null)
    }

    /**
     * The verdict of [condition] with `$topic` already published as [published].
     *
     * A variable is the case the modifier exists for: it always takes the symbolic path, and a
     * normalizer declared on a field cannot reach a value a rule computed. Published straight onto the
     * context rather than through a preceding rule, so the assertion is about the comparison alone.
     */
    private fun verdictAfterSet(condition: String, published: String): ConditionVerdict {
        val compiled = Compiler.compileRules(asts = Parser(input = rule(condition)).parseRules(), schema = schema)
        val prepared = PreparedRuleContext.prepare(ctx = RuleContext.of("purpose" to "any"), schema = schema)
        prepared.variables["topic"] = TextExpressionValue(value = published)
        return compiled.single().expression.evaluate(context = prepared, trace = null)
    }

    private fun rule(condition: String) = """
        rule "ignore-case" {
          description "symbolic comparison with the ignoreCase modifier"
          when
            $condition
          then
            flag "ok"
        }
    """.trimIndent()
}
