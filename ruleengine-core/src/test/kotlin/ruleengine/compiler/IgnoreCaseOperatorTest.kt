package ruleengine.compiler

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
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
            FieldId("purpose") to FieldDefinition(
                id = FieldId("purpose"), type = FieldType.TEXT,
                normalizers = listOf(NormalizerId("trim")),   // no lowercase
                operators = setOf(
                    OperatorId("equals"), OperatorId("contains"),
                    OperatorId("startsWith"), OperatorId("endsWith"), OperatorId("in")
                )
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

    private fun ctx(purpose: String): PreparedRuleContext {
        val prepared = PreparedRuleContext.prepare(RuleContext.of("purpose" to purpose), schema)
        return prepared
    }

    // ── equals ignoreCase ─────────────────────────────────────────────────

    @Test
    fun `equals ignoreCase matches mixed-case input`() {
        val e = engine("""
            rule "r" {
              when purpose equals "Miete" ignoreCase
              then label "ok"
            }
        """.trimIndent())
        assertTrue(e.evaluate(ctx("MIETE")).matches.isNotEmpty())
        assertTrue(e.evaluate(ctx("miete")).matches.isNotEmpty())
        assertTrue(e.evaluate(ctx("Miete")).matches.isNotEmpty())
        assertTrue(e.evaluate(ctx("MiEtE")).matches.isNotEmpty())
    }

    @Test
    fun `equals without ignoreCase is case-sensitive`() {
        val e = engine("""
            rule "r" {
              when purpose equals "Miete"
              then label "ok"
            }
        """.trimIndent())
        assertTrue(e.evaluate(ctx("Miete")).matches.isNotEmpty())
        assertTrue(e.evaluate(ctx("MIETE")).matches.isEmpty())  // no match without ignoreCase
    }

    // ── contains ignoreCase ───────────────────────────────────────────────

    @Test
    fun `contains ignoreCase matches mixed-case substring`() {
        val e = engine("""
            rule "r" {
              when purpose contains "rueckuberweisung" ignoreCase
              then label "chargeback"
            }
        """.trimIndent())
        assertTrue(e.evaluate(ctx("Rueckuberweisung Lastschrift")).matches.isNotEmpty())
        assertTrue(e.evaluate(ctx("RUECKUBERWEISUNG 123")).matches.isNotEmpty())
        assertTrue(e.evaluate(ctx("prefix rueckuberweisung suffix")).matches.isNotEmpty())
        assertFalse(e.evaluate(ctx("Normal payment")).matches.isNotEmpty())
    }

    @Test
    fun `contains ignoreCase works in bracket OR group`() {
        val e = engine("""
            rule "chargeback" {
              when
                (purpose contains "rueckuberweisung" ignoreCase
                or purpose contains "nicht gedeckt" ignoreCase)
              then label "chargeback"
            }
        """.trimIndent())
        assertTrue(e.evaluate(ctx("RUECKUBERWEISUNG")).matches.isNotEmpty())
        assertTrue(e.evaluate(ctx("Nicht Gedeckt")).matches.isNotEmpty())
        assertTrue(e.evaluate(ctx("Regular payment")).matches.isEmpty())
    }

    // ── startsWith ignoreCase ─────────────────────────────────────────────

    @Test
    fun `startsWith ignoreCase matches prefix regardless of case`() {
        val e = engine("""
            rule "r" {
              when purpose startsWith "Sammel" ignoreCase
              then label "batch"
            }
        """.trimIndent())
        assertTrue(e.evaluate(ctx("SAMMELAUFTRAG 001")).matches.isNotEmpty())
        assertTrue(e.evaluate(ctx("sammelauftrag")).matches.isNotEmpty())
        assertFalse(e.evaluate(ctx("Einzelauftrag")).matches.isNotEmpty())
    }

    // ── endsWith ignoreCase ───────────────────────────────────────────────

    @Test
    fun `endsWith ignoreCase matches suffix regardless of case`() {
        val e = engine("""
            rule "r" {
              when purpose endsWith "Versicherung" ignoreCase
              then label "insurance"
            }
        """.trimIndent())
        assertTrue(e.evaluate(ctx("Allianz VERSICHERUNG")).matches.isNotEmpty())
        assertTrue(e.evaluate(ctx("HUK-COBURG versicherung")).matches.isNotEmpty())
        assertFalse(e.evaluate(ctx("Stadtwerke Muenchen")).matches.isNotEmpty())
    }

    // ── in ignoreCase ─────────────────────────────────────────────────────

    @Test
    fun `in ignoreCase matches set members regardless of case`() {
        val e = engine("""
            rule "r" {
              when purpose in ["PMNT", "CCRD", "SALA"] ignoreCase
              then label "known-sepa"
            }
        """.trimIndent())
        assertTrue(e.evaluate(ctx("pmnt")).matches.isNotEmpty())
        assertTrue(e.evaluate(ctx("Ccrd")).matches.isNotEmpty())
        assertTrue(e.evaluate(ctx("sala")).matches.isNotEmpty())
        assertFalse(e.evaluate(ctx("UNKNW")).matches.isNotEmpty())
    }
}

