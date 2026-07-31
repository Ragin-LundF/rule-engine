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
 * Tests for the `ignoreCase` trailing modifier on all text operators:
 * equals, contains, startsWith, endsWith, in
 *
 * The `ignoreCase` flag makes comparisons case-insensitive without requiring
 * a `lowercase` normalizer on the field.
 */
class IgnoreCaseOperatorTest {

    /** Schema WITHOUT a lowercase normalizer — ignoreCase carries the weight. */
    private val schema = FieldSchema(
        name = "test",
        fields = mapOf(
            FieldId(value = "purpose") to FieldDefinition(
                id = FieldId(value = "purpose"),
                type = FieldType.TEXT,
                normalizers = listOf(NormalizerId(value = "trim")),   // no lowercase
                operators = setOf(
                    OperatorId(value = "equals"), OperatorId(value = "contains"),
                    OperatorId(value = "startsWith"), OperatorId(value = "endsWith"), OperatorId(value = "in")
                )
            )
        )
    )

    private fun engine(ruleText: String): RuleEngine {
        val asts = Parser(input = ruleText).parseRules()
        val validation = Validator.validate(asts = asts, schema = schema)
        assertTrue(actual = validation.isValid, message = "Validation failed: ${validation.diagnostics}")
        val compiled = Compiler.compileRules(
            asts = asts, schema = schema, normalizerRegistry = NormalizerRegistry.default
        )
        return RuleEngine(compiledRules = compiled)
    }

    private fun ctx(purpose: String): PreparedRuleContext {
        val prepared = PreparedRuleContext.prepare(ctx = RuleContext.of("purpose" to purpose), schema = schema)
        return prepared
    }

    // ── equals ignoreCase ─────────────────────────────────────────────────

    @Test
    fun `equals ignoreCase matches mixed-case input`() {
        val e = engine(
            ruleText = """
                        rule "r" {
                          when purpose equals "Miete" ignoreCase
                          then label "ok"
                        }
                    """.trimIndent()
        )
        assertTrue(actual = e.evaluate(prepared = ctx("MIETE")).matches.isNotEmpty())
        assertTrue(actual = e.evaluate(prepared = ctx("miete")).matches.isNotEmpty())
        assertTrue(actual = e.evaluate(prepared = ctx("Miete")).matches.isNotEmpty())
        assertTrue(actual = e.evaluate(prepared = ctx("MiEtE")).matches.isNotEmpty())
    }

    @Test
    fun `equals without ignoreCase is case-sensitive`() {
        val e = engine(
            ruleText = """
                        rule "r" {
                          when purpose equals "Miete"
                          then label "ok"
                        }
                    """.trimIndent()
        )
        assertTrue(actual = e.evaluate(prepared = ctx("Miete")).matches.isNotEmpty())
        assertTrue(actual = e.evaluate(prepared = ctx("MIETE")).matches.isEmpty())  // no match without ignoreCase
    }

    // ── contains ignoreCase ───────────────────────────────────────────────

    @Test
    fun `contains ignoreCase matches mixed-case substring`() {
        val e = engine(
            ruleText = """
                        rule "r" {
                          when purpose contains "rueckuberweisung" ignoreCase
                          then label "chargeback"
                        }
                    """.trimIndent()
        )
        assertTrue(actual = e.evaluate(prepared = ctx("Rueckuberweisung Lastschrift")).matches.isNotEmpty())
        assertTrue(actual = e.evaluate(prepared = ctx("RUECKUBERWEISUNG 123")).matches.isNotEmpty())
        assertTrue(actual = e.evaluate(prepared = ctx("prefix rueckuberweisung suffix")).matches.isNotEmpty())
        assertFalse(actual = e.evaluate(prepared = ctx("Normal payment")).matches.isNotEmpty())
    }

    @Test
    fun `contains ignoreCase works in bracket OR group`() {
        val e = engine(
            ruleText = """
                        rule "chargeback" {
                          when
                            (purpose contains "rueckuberweisung" ignoreCase
                            or purpose contains "nicht gedeckt" ignoreCase)
                          then label "chargeback"
                        }
                    """.trimIndent()
        )
        assertTrue(actual = e.evaluate(prepared = ctx("RUECKUBERWEISUNG")).matches.isNotEmpty())
        assertTrue(actual = e.evaluate(prepared = ctx("Nicht Gedeckt")).matches.isNotEmpty())
        assertTrue(actual = e.evaluate(prepared = ctx("Regular payment")).matches.isEmpty())
    }

    // ── startsWith ignoreCase ─────────────────────────────────────────────

    @Test
    fun `startsWith ignoreCase matches prefix regardless of case`() {
        val e = engine(
            ruleText = """
                        rule "r" {
                          when purpose startsWith "Sammel" ignoreCase
                          then label "batch"
                        }
                    """.trimIndent()
        )
        assertTrue(actual = e.evaluate(prepared = ctx("SAMMELAUFTRAG 001")).matches.isNotEmpty())
        assertTrue(actual = e.evaluate(prepared = ctx("sammelauftrag")).matches.isNotEmpty())
        assertFalse(actual = e.evaluate(prepared = ctx("Einzelauftrag")).matches.isNotEmpty())
    }

    // ── endsWith ignoreCase ───────────────────────────────────────────────

    @Test
    fun `endsWith ignoreCase matches suffix regardless of case`() {
        val e = engine(
            ruleText = """
                        rule "r" {
                          when purpose endsWith "Versicherung" ignoreCase
                          then label "insurance"
                        }
                    """.trimIndent()
        )
        assertTrue(actual = e.evaluate(prepared = ctx("Allianz VERSICHERUNG")).matches.isNotEmpty())
        assertTrue(actual = e.evaluate(prepared = ctx("HUK-COBURG versicherung")).matches.isNotEmpty())
        assertFalse(actual = e.evaluate(prepared = ctx("Stadtwerke Muenchen")).matches.isNotEmpty())
    }

    // ── in ignoreCase ─────────────────────────────────────────────────────

    @Test
    fun `in ignoreCase matches set members regardless of case`() {
        val e = engine(
            ruleText = """
                        rule "r" {
                          when purpose in ["PMNT", "CCRD", "SALA"] ignoreCase
                          then label "known-sepa"
                        }
                    """.trimIndent()
        )
        assertTrue(actual = e.evaluate(prepared = ctx("pmnt")).matches.isNotEmpty())
        assertTrue(actual = e.evaluate(prepared = ctx("Ccrd")).matches.isNotEmpty())
        assertTrue(actual = e.evaluate(prepared = ctx("sala")).matches.isNotEmpty())
        assertFalse(actual = e.evaluate(prepared = ctx("UNKNW")).matches.isNotEmpty())
    }
}

