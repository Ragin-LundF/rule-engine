package ruleengine.compiler

import ruleengine.core.domain.dto.FieldDefinition
import ruleengine.core.domain.dto.FieldId
import ruleengine.core.domain.dto.FieldSchema
import ruleengine.core.domain.dto.FieldType
import ruleengine.core.domain.dto.NormalizerId
import ruleengine.core.domain.dto.OperatorId
import ruleengine.core.normalizer.NormalizerRegistry
import ruleengine.dsl.ast.AndAst
import ruleengine.dsl.ast.OrAst
import ruleengine.dsl.parser.Parser
import ruleengine.evaluator.RuleEngine
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.RuleContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
            FieldId(value = "purpose") to FieldDefinition(
                id = FieldId(value = "purpose"),
                type = FieldType.TEXT,
                normalizers = listOf(NormalizerId(value = "trim"), NormalizerId(value = "lowercase")),
                operators = setOf(OperatorId(value = "contains"))
            ),
            FieldId(value = "amount") to FieldDefinition(
                id = FieldId(value = "amount"),
                type = FieldType.DECIMAL,
                normalizers = emptyList(),
                operators = setOf(OperatorId(value = "gte"))
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

        val ast = Parser(input = txt).parseRules().single()
        val root = ast.condition
        assertTrue(actual = root is AndAst, message = "Root should be AND, got $root")
        val rootAnd: AndAst = root
        assertEquals(expected = 2, actual = rootAnd.children.size)
        val orBlock = rootAnd.children.filterIsInstance<OrAst>().first()
        assertEquals(expected = 2, actual = orBlock.children.size, message = "OR block should have two children")
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

        val asts = Parser(input = txt).parseRules()
        val validation = Validator.validate(asts = asts, schema = schema)
        assertTrue(actual = validation.isValid, message = "Validation failed: ${validation.diagnostics}")

        val compiled = Compiler.compileRules(
            asts = asts, schema = schema, normalizerRegistry = NormalizerRegistry.default
        )
        val engine = RuleEngine(compiledRules = compiled)

        // Matches: purpose contains "miete" → true, amount 850 ≥ 500 → true
        assertTrue(
            actual = engine.evaluate(
                PreparedRuleContext.prepare(
                    ctx = RuleContext.of(
                        "purpose" to "Miete Januar",
                        "amount" to "850"
                    ), schema = schema
                )
            ).matches.isNotEmpty()
        )

        // Matches: purpose contains "rent" → true, amount 600 ≥ 500 → true
        assertTrue(
            actual = engine.evaluate(
                PreparedRuleContext.prepare(
                    ctx = RuleContext.of(
                        "purpose" to "Monthly Rent",
                        "amount" to "600"
                    ), schema = schema
                )
            ).matches.isNotEmpty()
        )

        // Does NOT match: purpose matches but amount too low
        assertTrue(
            actual = engine.evaluate(
                PreparedRuleContext.prepare(
                    ctx = RuleContext.of("purpose" to "Miete", "amount" to "100"),
                    schema = schema
                )
            ).matches.isEmpty()
        )

        // Does NOT match: amount fine but purpose doesn't match
        assertTrue(
            actual = engine.evaluate(
                PreparedRuleContext.prepare(
                    ctx = RuleContext.of(
                        "purpose" to "Salary payment",
                        "amount" to "1000"
                    ), schema = schema
                )
            ).matches.isEmpty()
        )
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

        val flatAsts = Parser(input = flatRule).parseRules()
        val bracketAsts = Parser(input = bracketRule).parseRules()

        // The flat rule's root should be OR; the bracket rule's root should be AND
        assertTrue(
            actual = flatAsts.single().condition is OrAst,
            message = "Flat rule root should be OR (or binds less tightly than and)"
        )
        assertTrue(actual = bracketAsts.single().condition is AndAst, message = "Bracket rule root should be AND")

        val flatCompiled = Compiler.compileRules(
            asts = flatAsts, schema = schema, normalizerRegistry = NormalizerRegistry.default
        )
        val bracketCompiled = Compiler.compileRules(
            asts = bracketAsts, schema = schema, normalizerRegistry = NormalizerRegistry.default
        )

        val flatEngine = RuleEngine(compiledRules = flatCompiled)
        val bracketEngine = RuleEngine(compiledRules = bracketCompiled)

        // purpose contains "miete", amount = 100 (below threshold)
        // flat:    OR(contains-miete=true, AND(contains-rent=false, gte-500=false)) → true  (miete branch wins)
        // bracket: AND(OR(miete=true, rent=false), gte-500=false)                 → false (amount check fails)
        val ctx =
            PreparedRuleContext.prepare(ctx = RuleContext.of("purpose" to "Miete", "amount" to "100"), schema = schema)

        assertTrue(
            actual = flatEngine.evaluate(prepared = ctx).matches.isNotEmpty(),
            message = "Flat rule should match because 'miete' branch alone satisfies the OR"
        )
        assertTrue(
            actual = bracketEngine.evaluate(prepared = ctx).matches.isEmpty(),
            message = "Bracket rule should NOT match because amount < 500 fails the outer AND"
        )
    }
}





