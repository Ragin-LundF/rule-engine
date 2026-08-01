package ruleengine.compiler

import ruleengine.core.errors.Severity
import ruleengine.dsl.parser.Parser
import ruleengine.evaluator.RuleEngine
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.RuleContext
import ruleengine.schema.FieldSchemaLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A plain condition (`shipment.transitDays >= 3`, `shipment.customer.tier equals "gold"`) must reach a field
 * declared through nested `fields:` blocks, not only one declared as a single dotted key.
 *
 * Before this was supported, every such rule failed with `Unknown field '<path>' in condition`, while the
 * same path worked inside `count(...)` / `sum(...)` — the two field models did not meet. These tests cover
 * the whole chain, because resolution has to agree in three places: the validator, the compiler and the
 * prepared context that supplies the value.
 */
class NestedConditionPathTest {

    private val schema = FieldSchemaLoader.loadFromString(
        content = """
            schema: nested-conditions-v1

            fields:
              shipment:
                type: object
                fields:
                  priority:
                    type: text
                    normalizers: [trim, uppercase]
                  transitDays:
                    type: integer
                  declaredValue:
                    type: decimal
                  pickedUpAt:
                    type: date
                    format: dd.MM.yyyy
                  express:
                    type: boolean
                  customer:
                    type: object
                    fields:
                      loyaltyTier:
                        type: text
                        alias: tier
                        normalizers: [trim, lowercase]
              parcels:
                type: collection
                fields:
                  weightKg:
                    type: decimal
        """.trimIndent()
    )

    private val shipmentInput: Map<String, Any?> = mapOf(
        "shipment" to mapOf(
            "priority" to " express domestic ",
            "transitDays" to 4,
            "declaredValue" to 1250.50,
            "pickedUpAt" to "17.03.2026",
            "express" to true,
            "customer" to mapOf("loyaltyTier" to "Gold")
        ),
        "parcels" to listOf(
            mapOf("weightKg" to 12.5),
            mapOf("weightKg" to 4.0)
        )
    )

    private fun validate(condition: String): ValidationResult {
        val rule = """
            rule "nested" {
              when
                $condition
              then
                assessment "ok"
            }
        """.trimIndent()
        return Validator.validate(asts = Parser(input = rule).parseRules(), schema = schema)
    }

    /** Runs the full pipeline and reports whether the rule matched [input]. */
    private fun matches(condition: String, input: Map<String, Any?> = shipmentInput): Boolean {
        val rule = """
            rule "nested" {
              when
                $condition
              then
                assessment "ok"
            }
        """.trimIndent()
        val asts = Parser(input = rule).parseRules()
        val result = Validator.validate(asts = asts, schema = schema)
        assertTrue(
            actual = result.isValid,
            message = "'$condition' should validate, got: ${result.diagnostics}"
        )
        val engine = RuleEngine(compiledRules = Compiler.compileRules(asts = asts, schema = schema))
        val context = RuleContext.of(entries = input.entries.map { it.key to it.value }.toTypedArray())
        val prepared = PreparedRuleContext.prepare(ctx = context, schema = schema)
        return engine.evaluate(prepared = prepared).matches.isNotEmpty()
    }

    private fun errors(result: ValidationResult) = result.diagnostics.filter { it.severity == Severity.ERROR }

    // --- relational operators on a nested leaf ---

    @Test
    fun `nested integer path matches and does not match`() {
        assertTrue(actual = matches(condition = "shipment.transitDays >= 3"), message = "4 >= 3")
        assertFalse(actual = matches(condition = "shipment.transitDays >= 5"), message = "4 >= 5")
        assertTrue(actual = matches(condition = "shipment.transitDays lt 5"), message = "4 < 5 (named operator)")
    }

    @Test
    fun `nested decimal path compares against a decimal literal`() {
        assertTrue(actual = matches(condition = "shipment.declaredValue > 1000.0"), message = "1250.50 > 1000.0")
        assertFalse(actual = matches(condition = "shipment.declaredValue < 1000.0"), message = "1250.50 < 1000.0")
        assertTrue(
            actual = matches(condition = "shipment.declaredValue between 100 5000"),
            message = "1250.50 within [100, 5000]"
        )
    }

    // --- named operators, normalizers and formats on a nested leaf ---

    @Test
    fun `nested text path applies its declared normalizers`() {
        // The value is ' express domestic ' and the field declares trim + uppercase, so both the input and
        // the literal are normalized before comparison.
        assertTrue(actual = matches(condition = """shipment.priority contains "express""""), message = "contains")
        assertTrue(
            actual = matches(condition = """shipment.priority startsWith "EXPRESS""""),
            message = "startsWith on the normalized value"
        )
        assertFalse(actual = matches(condition = """shipment.priority equals "express""""), message = "equals")
    }

    @Test
    fun `nested date path is read with its declared format`() {
        assertTrue(actual = matches(condition = """shipment.pickedUpAt >= "01.03.2026""""), message = "after 1 March")
        assertFalse(actual = matches(condition = """shipment.pickedUpAt < "01.03.2026""""), message = "before 1 March")
    }

    @Test
    fun `nested boolean path compares against a boolean literal`() {
        assertTrue(actual = matches(condition = "shipment.express equals true"), message = "express is true")
        assertFalse(actual = matches(condition = "shipment.express equals false"), message = "express is not false")
    }

    @Test
    fun `alias of a nested member resolves to its declared path`() {
        assertTrue(
            actual = matches(condition = """shipment.customer.tier equals "gold""""),
            message = "'tier' is the alias of 'loyaltyTier', whose value normalizes to 'gold'"
        )
    }

    // --- error paths ---

    @Test
    fun `unknown nested member is an error naming the path`() {
        val result = validate(condition = """shipment.customer.tir equals "gold"""")
        val errors = errors(result = result)
        assertEquals(expected = 1, actual = errors.size, message = "Expected one error, got: $errors")
        assertEquals(
            expected = "Unknown field 'shipment.customer.tir' in condition",
            actual = errors.first().message
        )
        assertEquals(
            expected = "shipment.customer.tier",
            actual = errors.first().suggestion,
            message = "The suggestion should name the nested path the author meant, in the spelling they used"
        )
    }

    @Test
    fun `undeclared intermediate segment is an error`() {
        val result = validate(condition = "shipment.route.transitDays >= 3")
        assertFalse(actual = result.isValid)
        assertTrue(
            actual = errors(result = result).any { it.message.contains("shipment.route.transitDays") },
            message = "Expected the message to name the path, got: ${result.diagnostics}"
        )
    }

    @Test
    fun `path through a collection points at the aggregate functions`() {
        val result = validate(condition = "parcels.weightKg >= 5")
        val errors = errors(result = result)
        assertEquals(expected = 1, actual = errors.size, message = "Expected one error, got: $errors")
        val message = errors.first().message
        assertTrue(
            actual = message.contains("collection 'parcels'") && message.contains("count(parcels)"),
            message = "Expected the message to name the collection and an aggregate, got: $message"
        )
    }

    @Test
    fun `object leaf cannot be compared directly`() {
        val result = validate(condition = """shipment.customer equals "gold"""")
        assertFalse(actual = result.isValid)
        assertTrue(
            actual = errors(result = result).any { it.message.contains("is a object") },
            message = "Expected the message to name the type, got: ${result.diagnostics}"
        )
    }

    // --- the flat model keeps working ---

    @Test
    fun `flat dotted field id keeps precedence over a nested declaration`() {
        // Both models describe 'shipment.transitDays'; the flat declaration wins, so its own type
        // (text here) is the one that governs the comparison.
        val mixedSchema = FieldSchemaLoader.loadFromString(
            content = """
                schema: mixed-v1

                fields:
                  shipment:
                    type: object
                    fields:
                      transitDays:
                        type: integer
                  shipment.transitDays:
                    type: text
            """.trimIndent()
        )
        val asts = Parser(
            input = """
                rule "flat" {
                  when
                    shipment.transitDays equals "express"
                  then
                    assessment "ok"
                }
            """.trimIndent()
        ).parseRules()

        val result = Validator.validate(asts = asts, schema = mixedSchema)
        assertTrue(actual = result.isValid, message = "Flat declaration should govern: ${result.diagnostics}")

        val engine = RuleEngine(compiledRules = Compiler.compileRules(asts = asts, schema = mixedSchema))
        val context = RuleContext.of("shipment" to mapOf("transitDays" to "express"))
        val prepared = PreparedRuleContext.prepare(ctx = context, schema = mixedSchema)
        assertTrue(actual = engine.evaluate(prepared = prepared).matches.isNotEmpty())
    }
}
