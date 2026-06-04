package ruleengine.compiler

import ruleengine.core.domain.FieldDefinition
import ruleengine.core.domain.FieldId
import ruleengine.core.domain.FieldSchema
import ruleengine.core.domain.FieldType
import ruleengine.core.domain.NormalizerId
import ruleengine.core.domain.OperatorId
import ruleengine.core.normalizer.NormalizerRegistry
import ruleengine.dsl.parser.Parser
import ruleengine.evaluator.RuleEngine
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.RuleContext
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for the `regex` operator.
 *
 * - regex matches against the field's **original** (pre-normalization) value.
 * - The `ignoreCase` modifier compiles the pattern with RegexOption.IGNORE_CASE.
 * - Validation rejects invalid regex patterns at compile time.
 */
class RegexOperatorTest {

    private val schema = FieldSchema(
        name = "test",
        fields = mapOf(
            FieldId("iban") to FieldDefinition(
                id = FieldId("iban"), type = FieldType.TEXT,
                normalizers = listOf(NormalizerId("trim"), NormalizerId("uppercase")),
                operators = setOf(OperatorId("regex"), OperatorId("equals"), OperatorId("startsWith"))
            ),
            FieldId("purpose") to FieldDefinition(
                id = FieldId("purpose"), type = FieldType.TEXT,
                normalizers = listOf(NormalizerId("trim")),
                operators = setOf(OperatorId("regex"), OperatorId("contains"))
            )
        )
    )

    private fun engine(ruleText: String): RuleEngine {
        val asts = Parser(ruleText).parseRules()
        val validation = Validator.validate(asts, schema)
        assertTrue(validation.isValid, "Validation failed: ${validation.diagnostics}")
        val compiled = Compiler.compileRules(asts, schema, NormalizerRegistry.default)
        return RuleEngine(compiledRules = compiled, schema = schema)
    }

    private fun ctx(iban: String = "", purpose: String = ""): PreparedRuleContext {
        val prepared = PreparedRuleContext.prepare(RuleContext.of("iban" to iban, "purpose" to purpose), schema)
        return prepared
    }

    // ── Basic regex matching ──────────────────────────────────────────────

    @Test
    fun `regex matches DACH IBAN prefix`() {
        val e = engine(
            """
            rule "dach-iban" {
              when iban regex "^(DE|AT|CH)"
              then label "dach"
            }
        """.trimIndent()
        )
        // regex runs against original value; trim normalizer applies but NOT uppercase (original preserved for regex)
        assertTrue(e.evaluate(ctx(iban = "DE89370400440532013000")).matches.isNotEmpty())
        assertTrue(e.evaluate(ctx(iban = "AT611904300234573201")).matches.isNotEmpty())
        assertTrue(e.evaluate(ctx(iban = "CH5604835012345678009")).matches.isNotEmpty())
        assertTrue(e.evaluate(ctx(iban = "GB29NWBK60161331926819")).matches.isEmpty())
    }

    @Test
    fun `not regex detects non-DACH IBANs`() {
        val e = engine(
            """
            rule "foreign" {
              when not iban regex "^(DE|AT|CH)"
              then flag "foreign-iban"
            }
        """.trimIndent()
        )
        assertTrue(e.evaluate(ctx(iban = "GB29NWBK60161331926819")).matches.isNotEmpty())
        assertTrue(e.evaluate(ctx(iban = "DE89370400440532013000")).matches.isEmpty())
    }

    @Test
    fun `regex with digit anchor matches all-zero synthetic IBAN`() {
        val e = engine(
            """
            rule "synthetic" {
              when iban regex "^[A-Z]{2}[0-9]{2}0{8,}"
              then flag "synthetic"
            }
        """.trimIndent()
        )
        assertTrue(e.evaluate(ctx(iban = "DE000000000000000000")).matches.isNotEmpty())
        assertFalse(e.evaluate(ctx(iban = "DE89370400440532013000")).matches.isNotEmpty())
    }

    // ── regex ignoreCase modifier ─────────────────────────────────────────

    @Test
    fun `regex ignoreCase matches regardless of casing in input`() {
        val e = engine(
            """
            rule "fraud-keyword" {
              when purpose regex "betrug|phishing|scam" ignoreCase
              then label "fraud"
            }
        """.trimIndent()
        )
        assertTrue(e.evaluate(ctx(purpose = "BETRUG Verdacht")).matches.isNotEmpty())
        assertTrue(e.evaluate(ctx(purpose = "Phishing-Link gesehen")).matches.isNotEmpty())
        assertTrue(e.evaluate(ctx(purpose = "Normaler Einkauf")).matches.isEmpty())
    }

    @Test
    fun `regex without ignoreCase is case-sensitive`() {
        val e = engine(
            """
            rule "case-sensitive" {
              when purpose regex "^Miete"
              then label "rent"
            }
        """.trimIndent()
        )
        assertTrue(e.evaluate(ctx(purpose = "Miete Januar")).matches.isNotEmpty())
        assertTrue(e.evaluate(ctx(purpose = "MIETE Januar")).matches.isEmpty())
    }

    // ── Validator rejects invalid regex ──────────────────────────────────

    @Test
    fun `validator reports error for invalid regex pattern`() {
        val txt = """
            rule "bad-regex" {
              when iban regex "[invalid("
              then label "x"
            }
        """.trimIndent()
        val asts = Parser(txt).parseRules()
        val result = Validator.validate(asts, schema)
        assertFalse(result.isValid, "Validator should reject invalid regex")
        assertTrue(result.diagnostics.any { it.message.contains("regex", ignoreCase = true) })
    }
}

