package ruleengine.compiler

import ruleengine.core.domain.dto.FieldDefinition
import ruleengine.core.domain.dto.FieldId
import ruleengine.core.domain.dto.FieldSchema
import ruleengine.core.domain.dto.FieldType
import ruleengine.core.domain.dto.NormalizerId
import ruleengine.core.domain.dto.OperatorId
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
            FieldId(value = "iban") to FieldDefinition(
                id = FieldId(value = "iban"), type = FieldType.TEXT,
                normalizers = listOf(NormalizerId(value = "trim"), NormalizerId(value = "uppercase")),
                operators = setOf(
                    OperatorId(value = "regex"),
                    OperatorId(value = "equals"),
                    OperatorId(value = "startsWith")
                )
            ),
            FieldId(value = "purpose") to FieldDefinition(
                id = FieldId(value = "purpose"), type = FieldType.TEXT,
                normalizers = listOf(NormalizerId(value = "trim")),
                operators = setOf(OperatorId(value = "regex"), OperatorId(value = "contains"))
            )
        )
    )

    private fun engine(ruleText: String): RuleEngine {
        val asts = Parser(input = ruleText).parseRules()
        val validation = Validator.validate(asts = asts, schema = schema)
        assertTrue(actual = validation.isValid, message = "Validation failed: ${validation.diagnostics}")
        val compiled = Compiler.compileRules(
            asts = asts,
            schema = schema,
            normalizerRegistry = NormalizerRegistry.default
        )
        return RuleEngine(compiledRules = compiled)
    }

    private fun ctx(iban: String = "", purpose: String = ""): PreparedRuleContext {
        val prepared = PreparedRuleContext.prepare(
            ctx = RuleContext.of("iban" to iban, "purpose" to purpose),
            schema = schema
        )
        return prepared
    }

    // ── Basic regex matching ──────────────────────────────────────────────

    @Test
    fun `regex matches DACH IBAN prefix`() {
        val e = engine(
            ruleText = """
                        rule "dach-iban" {
                          when iban regex "^(DE|AT|CH)"
                          then label "dach"
                        }
                    """.trimIndent()
        )
        // regex runs against original value; trim normalizer applies but NOT uppercase (original preserved for regex)
        assertTrue(actual = e.evaluate(prepared = ctx(iban = "DE89370400440532013000")).matches.isNotEmpty())
        assertTrue(actual = e.evaluate(prepared = ctx(iban = "AT611904300234573201")).matches.isNotEmpty())
        assertTrue(actual = e.evaluate(prepared = ctx(iban = "CH5604835012345678009")).matches.isNotEmpty())
        assertTrue(actual = e.evaluate(prepared = ctx(iban = "GB29NWBK60161331926819")).matches.isEmpty())
    }

    @Test
    fun `not regex detects non-DACH IBANs`() {
        val e = engine(
            ruleText = """
                        rule "foreign" {
                          when not iban regex "^(DE|AT|CH)"
                          then flag "foreign-iban"
                        }
                    """.trimIndent()
        )
        assertTrue(actual = e.evaluate(prepared = ctx(iban = "GB29NWBK60161331926819")).matches.isNotEmpty())
        assertTrue(actual = e.evaluate(prepared = ctx(iban = "DE89370400440532013000")).matches.isEmpty())
    }

    @Test
    fun `regex with digit anchor matches all-zero synthetic IBAN`() {
        val e = engine(
            ruleText = """
                        rule "synthetic" {
                          when iban regex "^[A-Z]{2}[0-9]{2}0{8,}"
                          then flag "synthetic"
                        }
                    """.trimIndent()
        )
        assertTrue(actual = e.evaluate(prepared = ctx(iban = "DE000000000000000000")).matches.isNotEmpty())
        assertFalse(actual = e.evaluate(prepared = ctx(iban = "DE89370400440532013000")).matches.isNotEmpty())
    }

    // ── regex ignoreCase modifier ─────────────────────────────────────────

    @Test
    fun `regex ignoreCase matches regardless of casing in input`() {
        val e = engine(
            ruleText = """
                        rule "fraud-keyword" {
                          when purpose regex "betrug|phishing|scam" ignoreCase
                          then label "fraud"
                        }
                    """.trimIndent()
        )
        assertTrue(actual = e.evaluate(prepared = ctx(purpose = "BETRUG Verdacht")).matches.isNotEmpty())
        assertTrue(actual = e.evaluate(prepared = ctx(purpose = "Phishing-Link gesehen")).matches.isNotEmpty())
        assertTrue(actual = e.evaluate(prepared = ctx(purpose = "Normaler Einkauf")).matches.isEmpty())
    }

    @Test
    fun `regex without ignoreCase is case-sensitive`() {
        val e = engine(
            ruleText = """
                        rule "case-sensitive" {
                          when purpose regex "^Miete"
                          then label "rent"
                        }
                    """.trimIndent()
        )
        assertTrue(actual = e.evaluate(prepared = ctx(purpose = "Miete Januar")).matches.isNotEmpty())
        assertTrue(actual = e.evaluate(prepared = ctx(purpose = "MIETE Januar")).matches.isEmpty())
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
        val asts = Parser(input = txt).parseRules()
        val result = Validator.validate(asts = asts, schema = schema)
        assertFalse(actual = result.isValid, message = "Validator should reject invalid regex")
        assertTrue(actual = result.diagnostics.any { it.message.contains(other = "regex", ignoreCase = true) })
    }
}

