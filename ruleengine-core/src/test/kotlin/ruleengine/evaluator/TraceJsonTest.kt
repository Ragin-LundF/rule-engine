package ruleengine.evaluator

import ruleengine.compiler.Compiler
import ruleengine.compiler.Validator
import ruleengine.core.domain.FieldDefinition
import ruleengine.core.domain.FieldId
import ruleengine.core.domain.FieldSchema
import ruleengine.core.domain.FieldType
import ruleengine.core.domain.NormalizerId
import ruleengine.core.domain.OperatorId
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
        val engine = RuleEngine(compiledRules = compiled, schema = schema)

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
    }
}

