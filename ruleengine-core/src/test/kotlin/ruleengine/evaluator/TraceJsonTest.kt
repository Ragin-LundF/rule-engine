package ruleengine.evaluator

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import ruleengine.dsl.parser.Parser
import ruleengine.compiler.Compiler
import ruleengine.compiler.Validator
import ruleengine.core.domain.FieldSchema
import ruleengine.core.domain.FieldId
import ruleengine.core.domain.FieldDefinition
import ruleengine.core.domain.FieldType
import ruleengine.core.domain.NormalizerId
import ruleengine.core.domain.OperatorId
import ruleengine.core.normalizer.NormalizerRegistry
import ruleengine.evaluator.context.RuleContext

class TraceJsonTest {
    @Test
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

        val asts = Parser(txt).parseRules()
        val schema = FieldSchema(
            name = "transaction-v1",
            fields = mapOf(
                FieldId("purpose") to FieldDefinition(FieldId("purpose"), FieldType.TEXT, normalizers = listOf(NormalizerId("trim"), NormalizerId("lowercase")), operators = setOf(OperatorId("contains"), OperatorId("equals"))),
                FieldId("amount") to FieldDefinition(FieldId("amount"), FieldType.DECIMAL, normalizers = emptyList(), operators = setOf(OperatorId("gte"), OperatorId("gt"), OperatorId("equals")))
            )
        )

        val validation = Validator.validate(asts, schema)
        assertTrue(validation.isValid)

        val compiled = Compiler.compileRules(asts, schema, NormalizerRegistry.default)
        val engine = RuleEngine(compiledRules = compiled, schema = schema)

        val ctx = RuleContext.of("purpose" to "Miete Januar", "amount" to "850")
        val prepared = ruleengine.evaluator.context.PreparedRuleContext.prepare(ctx = ctx, schema = schema)
        val result = engine.evaluate(prepared = prepared, includeTrace = true)

        assertNotNull(result.trace)
        val mapper = ObjectMapper().registerKotlinModule()
        val json = mapper.writeValueAsString(result.trace)
        val node = mapper.readTree(json)

        // matchedRules should contain the rule id
        val matched = node.get("matchedRules")
        assertNotNull(matched)
        assertTrue(matched.isArray())
        val found = matched.elements().asSequence().any { it.asText() == "rent-payment" }
        assertTrue(found)

        // root should exist and have children (AND node has children)
        val root = node.get("root")
        assertNotNull(root)
        val children = root.get("children")
        assertNotNull(children)
    }
}

