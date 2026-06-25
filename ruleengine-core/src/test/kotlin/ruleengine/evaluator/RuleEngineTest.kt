package ruleengine.evaluator

import ruleengine.core.domain.FieldDefinition
import ruleengine.core.domain.FieldId
import ruleengine.core.domain.FieldSchema
import ruleengine.core.domain.FieldType
import ruleengine.core.domain.NormalizerId
import ruleengine.core.domain.OperatorId
import ruleengine.evaluator.compiled.AndExpression
import ruleengine.evaluator.compiled.CompiledActionArgument
import ruleengine.evaluator.compiled.ComparisonOperator
import ruleengine.evaluator.compiled.DecimalComparisonExpression
import ruleengine.evaluator.compiled.RegexExtractExpression
import ruleengine.evaluator.compiled.TextEqualsExpression
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.RuleContext
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

class RuleEngineTest {
    @Test
    fun `programmatic compiled rule matches input`() {
        val schema = FieldSchema(
            name = "test",
            fields = mapOf(
                FieldId(value = "purpose") to FieldDefinition(
                    id = FieldId(value = "purpose"), type = FieldType.TEXT,
                    normalizers = listOf(NormalizerId(value = "trim")),
                    operators = setOf(OperatorId(value = "equals"))
                ),
                FieldId(value = "amount") to FieldDefinition(
                    id = FieldId(value = "amount"), type = FieldType.DECIMAL,
                    normalizers = emptyList(), operators = setOf(OperatorId(value = "gte"))
                )
            )
        )

        val expr = AndExpression(
            children = listOf(
                TextEqualsExpression(field = FieldId(value = "purpose"), expectedNormalized = "miete"),
                DecimalComparisonExpression(
                    field = FieldId(value = "amount"),
                    expected = BigDecimal("500"),
                    op = ComparisonOperator.GTE
                )
            )
        )

        val rule = CompiledRule(
            id = "rent-payment",
            expression = expr,
            actions = listOf(
                CompiledAction(
                    name = "label",
                    arguments = listOf(CompiledActionArgument.Static(value = "rent"))
                )
            )
        )

        val engine = RuleEngine(compiledRules = listOf(rule))

        val ctx = RuleContext.of("purpose" to "miete", "amount" to "850")
        val prepared = PreparedRuleContext.prepare(ctx = ctx, schema = schema)

        val result = engine.evaluate(prepared = prepared)
        assertEquals(expected = 1, actual = result.matches.size)
        assertEquals(expected = "rent-payment", actual = result.matches.first().ruleId)
    }

    @Test
    fun `short-circuit stops after first match per output group`() {
        val first = labelRule(id = "first", label = "groceries")
        val second = labelRule(id = "second", label = "groceries")

        val withoutShortCircuit = engineFor(rules = listOf(first, second), shortCircuit = false)
            .evaluate(prepared = preparedPurpose())
        assertEquals(expected = 2, actual = withoutShortCircuit.matches.size)

        val withShortCircuit = engineFor(rules = listOf(first, second), shortCircuit = true)
            .evaluate(prepared = preparedPurpose())
        assertEquals(expected = 1, actual = withShortCircuit.matches.size)
        assertEquals(expected = "first", actual = withShortCircuit.matches.first().ruleId)
    }

    @Test
    fun `rule shared across output groups is reported once`() {
        val shared = CompiledRule(
            id = "shared",
            expression = purposeEquals(),
            actions = listOf(
                CompiledAction(name = "label", arguments = listOf(CompiledActionArgument.Static(value = "groceries"))),
                CompiledAction(name = "score", arguments = listOf(CompiledActionArgument.Static(value = 10)))
            )
        )
        val scoreOnly = CompiledRule(
            id = "score-only",
            expression = purposeEquals(),
            actions = listOf(
                CompiledAction(name = "score", arguments = listOf(CompiledActionArgument.Static(value = 10)))
            )
        )

        val result = engineFor(rules = listOf(shared, scoreOnly), shortCircuit = true)
            .evaluate(prepared = preparedPurpose())

        assertEquals(expected = 2, actual = result.matches.size)
        assertEquals(expected = 1, actual = result.matches.count { it.ruleId == "shared" })
        assertEquals(expected = 1, actual = result.matches.count { it.ruleId == "score-only" })
    }

    @Test
    fun `rules without static output are always evaluated`() {
        val dynamic = CompiledRule(
            id = "dynamic",
            expression = purposeEquals(),
            actions = listOf(
                CompiledAction(
                    name = "label",
                    arguments = listOf(
                        CompiledActionArgument.ExtractionRef(
                            extraction = RegexExtractExpression(
                                field = FieldId(value = "purpose"),
                                pattern = Regex(pattern = "(.+)"),
                                groupIndex = 1
                            )
                        )
                    )
                )
            )
        )
        val labelled = labelRule(id = "labelled", label = "groceries")

        val result = engineFor(rules = listOf(labelled, dynamic), shortCircuit = true)
            .evaluate(prepared = preparedPurpose())

        assertEquals(expected = 2, actual = result.matches.size)
        val dynamicMatch = result.matches.first { it.ruleId == "dynamic" }
        assertEquals(expected = "coffee", actual = dynamicMatch.actions.first().arguments.first())
    }

    private fun purposeEquals(): TextEqualsExpression {
        return TextEqualsExpression(field = FieldId(value = "purpose"), expectedNormalized = "coffee")
    }

    private fun labelRule(id: String, label: String): CompiledRule {
        return CompiledRule(
            id = id,
            expression = purposeEquals(),
            actions = listOf(
                CompiledAction(name = "label", arguments = listOf(CompiledActionArgument.Static(value = label)))
            )
        )
    }

    private fun engineFor(rules: List<CompiledRule>, shortCircuit: Boolean): RuleEngine {
        return RuleEngine(compiledRules = rules, shortCircuitByOutput = shortCircuit)
    }

    private fun purposeSchema(): FieldSchema {
        return FieldSchema(
            name = "test",
            fields = mapOf(
                FieldId(value = "purpose") to FieldDefinition(
                    id = FieldId(value = "purpose"),
                    type = FieldType.TEXT,
                    normalizers = listOf(NormalizerId(value = "trim"))
                )
            )
        )
    }

    private fun preparedPurpose(): PreparedRuleContext {
        val ctx = RuleContext.of("purpose" to "coffee")
        return PreparedRuleContext.prepare(ctx = ctx, schema = purposeSchema())
    }
}
