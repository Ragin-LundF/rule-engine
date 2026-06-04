package ruleengine.compiler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ruleengine.dsl.parser.Parser
import ruleengine.dsl.ast.AndAst
import ruleengine.dsl.ast.OrAst
import ruleengine.core.domain.FieldDefinition
import ruleengine.core.domain.FieldId
import ruleengine.core.domain.FieldSchema
import ruleengine.core.domain.FieldType
import ruleengine.core.domain.NormalizerId
import ruleengine.core.domain.OperatorId
import ruleengine.core.normalizer.NormalizerRegistry
import ruleengine.evaluator.RuleEngine
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.RuleContext

/**
 * Tests that parentheses can be used to create logical groups in rule conditions.
 *
 * Example:
 *   (purpose contains "miete" or purpose contains "rent") and amount >= 500
 *
 * Without brackets the `or` would bind less tightly and the semantics would differ.
 */
class ParenthesizedExpressionTest {

    private val schema = FieldSchema(
        name = "transaction-v1",
        fields = mapOf(
            FieldId("purpose") to FieldDefinition(
                id = FieldId("purpose"), type = FieldType.TEXT,
                normalizers = listOf(NormalizerId("trim"), NormalizerId("lowercase")),
                operators = setOf(OperatorId("contains"))
            ),
            FieldId("amount") to FieldDefinition(
                id = FieldId("amount"), type = FieldType.DECIMAL,
                normalizers = emptyList(),
                operators = setOf(OperatorId("gte"))
            )
        )
    )

    @Test
    fun `parenthesised OR block parses into correct AST structure`() {
        // The outer condition must be AND(OR(contains miete, contains rent), gte 500)
        val txt = """
            rule "rent-payment" {
              when
                (purpose contains "miete"
                or purpose contains "rent")
                and amount >= 500

              then
                label "rent"
            }
        """.trimIndent()

        val ast = Parser(txt).parseRules().single()
        val root = ast.condition
        assertTrue(root is AndAst, "Root should be AND, got $root")
        val rootAnd: AndAst = root
        assertEquals(2, rootAnd.children.size)
        val orBlock = rootAnd.children.filterIsInstance<OrAst>().first()
        assertEquals(2, orBlock.children.size, "OR block should have two children")
    }

    @Test
    fun `rule with brackets matches when OR branch and numeric threshold are both satisfied`() {
        val txt = """
            rule "rent-payment" {
              when
                (purpose contains "miete"
                or purpose contains "rent")
                and amount >= 500

              then
                label "rent"
            }
        """.trimIndent()

        val asts = Parser(txt).parseRules()
        val validation = Validator.validate(asts, schema)
        assertTrue(validation.isValid, "Validation failed: ${validation.diagnostics}")

        val compiled = Compiler.compileRules(asts, schema, NormalizerRegistry.default)
        val engine = RuleEngine(compiledRules = compiled, schema = schema)

        // Matches: purpose contains "miete" → true, amount 850 ≥ 500 → true
        assertTrue(engine.evaluate(PreparedRuleContext.prepare(RuleContext.of("purpose" to "Miete Januar", "amount" to "850"), schema)).matches.isNotEmpty())

        // Matches: purpose contains "rent" → true, amount 600 ≥ 500 → true
        assertTrue(engine.evaluate(PreparedRuleContext.prepare(RuleContext.of("purpose" to "Monthly Rent", "amount" to "600"), schema)).matches.isNotEmpty())

        // Does NOT match: purpose matches but amount too low
        assertTrue(engine.evaluate(PreparedRuleContext.prepare(RuleContext.of("purpose" to "Miete", "amount" to "100"), schema)).matches.isEmpty())

        // Does NOT match: amount fine but purpose doesn't match
        assertTrue(engine.evaluate(PreparedRuleContext.prepare(RuleContext.of("purpose" to "Salary payment", "amount" to "1000"), schema)).matches.isEmpty())
    }

    @Test
    fun `brackets change evaluation order compared to flat expression`() {
        // Without brackets: purpose contains "miete" or (purpose contains "rent" and amount >= 500)
        val flatRule = """
            rule "flat" {
              when
                purpose contains "miete"
                or purpose contains "rent"
                and amount >= 500
              then
                label "test"
            }
        """.trimIndent()

        // With brackets: (purpose contains "miete" or purpose contains "rent") and amount >= 500
        val bracketRule = """
            rule "bracket" {
              when
                (purpose contains "miete"
                or purpose contains "rent")
                and amount >= 500
              then
                label "test"
            }
        """.trimIndent()

        val flatAsts = Parser(flatRule).parseRules()
        val bracketAsts = Parser(bracketRule).parseRules()

        // The flat rule's root should be OR; the bracket rule's root should be AND
        assertTrue(flatAsts.single().condition is OrAst,
            "Flat rule root should be OR (or binds less tightly than and)")
        assertTrue(bracketAsts.single().condition is AndAst,
            "Bracket rule root should be AND")

        val flatCompiled = Compiler.compileRules(flatAsts, schema, NormalizerRegistry.default)
        val bracketCompiled = Compiler.compileRules(bracketAsts, schema, NormalizerRegistry.default)

        val flatEngine = RuleEngine(compiledRules = flatCompiled, schema = schema)
        val bracketEngine = RuleEngine(compiledRules = bracketCompiled, schema = schema)

        // purpose contains "miete", amount = 100 (below threshold)
        // flat:    OR(contains-miete=true, AND(contains-rent=false, gte-500=false)) → true  (miete branch wins)
        // bracket: AND(OR(miete=true, rent=false), gte-500=false)                 → false (amount check fails)
        val ctx = PreparedRuleContext.prepare(RuleContext.of("purpose" to "Miete", "amount" to "100"), schema)

        assertTrue(flatEngine.evaluate(ctx).matches.isNotEmpty(),
            "Flat rule should match because 'miete' branch alone satisfies the OR")
        assertTrue(bracketEngine.evaluate(ctx).matches.isEmpty(),
            "Bracket rule should NOT match because amount < 500 fails the outer AND")
    }
}





