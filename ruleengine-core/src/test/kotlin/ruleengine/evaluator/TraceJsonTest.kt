package ruleengine.evaluator

import ruleengine.compiler.Compiler
import ruleengine.compiler.Validator
import ruleengine.core.domain.dto.NormalizerId
import ruleengine.core.domain.dto.OperatorId
import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.core.normalizer.NormalizerRegistry
import ruleengine.dsl.parser.Parser
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.RuleContext
import ruleengine.jackson.JacksonUtil
import tools.jackson.databind.JsonNode
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TraceJsonTest {
    @Test
    @Suppress("LongMethod")
    fun `decision tree serializes to JSON and contains expected fields`() {
        val txt = """
            rule "rent-payment" {
              when
                purpose contains "miete"
                and amount >= 500

              then
                label "rent"
            }
        """.trimIndent()

        val asts = Parser(input = txt).parseRules()
        val schema = FieldSchema(
            name = "transaction-v1",
            fields = mapOf(
                FieldId(value = "purpose") to FieldDefinition(
                    id = FieldId(value = "purpose"),
                    type = FieldType.TEXT,
                    normalizers = listOf(NormalizerId(value = "trim"), NormalizerId(value = "lowercase")),
                    operators = setOf(OperatorId(value = "contains"), OperatorId(value = "equals"))
                ),
                FieldId(value = "amount") to FieldDefinition(
                    id = FieldId(value = "amount"),
                    type = FieldType.DECIMAL,
                    normalizers = emptyList(),
                    operators = setOf(
                        OperatorId(value = "gte"),
                        OperatorId(value = "gt"),
                        OperatorId(value = "equals")
                    )
                )
            )
        )

        val validation = Validator.validate(asts = asts, schema = schema)
        assertTrue(actual = validation.isValid)

        val compiled = Compiler.compileRules(
            asts = asts,
            schema = schema,
            normalizerRegistry = NormalizerRegistry.default
        )
        val engine = RuleEngine(compiledRules = compiled)

        val ctx = RuleContext.of("purpose" to "Miete Januar", "amount" to "850")
        val prepared = PreparedRuleContext.prepare(ctx = ctx, schema = schema)
        val result = engine.evaluate(prepared = prepared, includeTrace = true)

        assertNotNull(actual = result.trace)
        val mapper = JacksonUtil.jsonMapper
        val json = mapper.writeValueAsString(result.trace)
        val node = mapper.readTree(json)

        // matchedRules should contain the rule id
        val matched: JsonNode = node.get("matchedRules")
        assertNotNull(actual = matched)
        assertTrue(actual = matched.isArray)
        var found = false
        for (i in 0 until matched.size()) {
            val el = matched.get(i)
            if (el.asString() == "rent-payment") {
                found = true
                break
            }
        }
        assertTrue(found)

        // root should exist and have children (AND node has children)
        val root = node.get("root")
        assertNotNull(actual = root)
        val children = root.get("children")
        assertNotNull(actual = children)

        // These leaves report no actual value, and the mapper's NON_NULL inclusion must therefore keep
        // "actual" out of the JSON entirely — existing consumers see byte-identical output.
        assertTrue(
            actual = "actual" !in json,
            message = "Expected no 'actual' key when nothing set one, got: $json",
        )
    }

    @Test
    fun `an aggregate condition serializes its actual value`() {
        val txt = """
            rule "busy-basket" {
              when
                count(items) >= 5

              then
                label "busy"
            }
        """.trimIndent()

        val schema = FieldSchema(
            name = "basket-v1",
            fields = mapOf(
                FieldId(value = "items") to FieldDefinition(
                    id = FieldId(value = "items"),
                    type = FieldType.STRING_SET
                )
            )
        )

        val compiled = Compiler.compileRules(asts = Parser(input = txt).parseRules(), schema = schema)
        val prepared = PreparedRuleContext.prepare(
            ctx = RuleContext.of("items" to listOf(mapOf("sku" to "a"), mapOf("sku" to "b"))),
            schema = schema
        )
        val result = RuleEngine(compiledRules = compiled).evaluate(prepared = prepared, includeTrace = true)

        val json = JacksonUtil.jsonMapper.writeValueAsString(result.trace)
        // root (EVALUATION) → children[0] (RULE) → children[0] (CONDITION)
        val condition = JacksonUtil.jsonMapper.readTree(json)
            .get("root").get("children").get(0)
            .get("children").get(0)

        assertNotNull(actual = condition)
        assertTrue(actual = condition.get("field").asString() == "count(items)")
        assertTrue(
            actual = condition.get("actual").asInt() == 2,
            message = "Expected the recorded count in the JSON, got: $json",
        )
    }
}

