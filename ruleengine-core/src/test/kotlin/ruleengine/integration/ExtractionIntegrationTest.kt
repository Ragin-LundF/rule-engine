package ruleengine.integration

import ruleengine.compiler.Compiler
import ruleengine.compiler.Validator
import ruleengine.core.domain.dto.FieldDefinition
import ruleengine.core.domain.dto.FieldId
import ruleengine.core.domain.dto.FieldSchema
import ruleengine.core.domain.dto.FieldType
import ruleengine.dsl.parser.Parser
import ruleengine.evaluator.RuleEngine
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.RuleContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for the `extract` DSL feature.
 *
 * Verifies the complete pipeline:
 * ```
 * DSL → Parser → Validator → Compiler → RuleEngine.evaluate()
 * ```
 * for rules that use `extract <field> regex("pattern", N) action $1`.
 */
class ExtractionIntegrationTest {

    /** Minimal field schema with a single TEXT field `reference`. */
    private val schema = FieldSchema(
        name = "extraction-test-schema",
        fields = mapOf(
            FieldId(value = "reference") to FieldDefinition(
                id = FieldId(value = "reference"),
                type = FieldType.TEXT
            ),
            FieldId(value = "amount") to FieldDefinition(
                id = FieldId(value = "amount"),
                type = FieldType.DECIMAL
            )
        )
    )

    // ──────────────────────────────────────────────────────────────────────────
    // Helper
    // ──────────────────────────────────────────────────────────────────────────

    private fun evaluate(
        dsl: String,
        contextEntries: Map<String, Any?>
    ): List<ruleengine.core.domain.dto.RuleAction> {
        val rules = Parser(input = dsl).parseRules()
        val compiled = Compiler.compileRules(asts = rules, schema = schema)
        val engine = RuleEngine(compiledRules = compiled)

        val pairs = contextEntries.entries.map { it.key to it.value }.toTypedArray()
        val ctx = RuleContext.of(entries = pairs)
        val prepared = PreparedRuleContext.prepare(ctx = ctx, schema = schema)
        return engine.evaluate(prepared = prepared).matches.flatMap { it.actions }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Tests
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `extracts capture group and passes it as action argument when rule matches`() {
        val dsl = """
            rule "extract-txn-id" {
              when
                reference regex "TXN-[0-9]+"
              then
                extract reference regex("TXN-([0-9]+)", 1) label ${'$'}1
            }
        """.trimIndent()

        val actions = evaluate(
            dsl = dsl,
            contextEntries = mapOf("reference" to "TXN-98765")
        )

        assertEquals(expected = 1, actual = actions.size)
        assertEquals(expected = "label", actual = actions[0].name)
        assertEquals(expected = "98765", actual = actions[0].arguments.first())
    }

    @Test
    fun `rule does not match when condition is false and extraction is not attempted`() {
        val dsl = """
            rule "extract-txn-id" {
              when
                reference regex "TXN-[0-9]+"
              then
                extract reference regex("TXN-([0-9]+)", 1) label ${'$'}1
            }
        """.trimIndent()

        val actions = evaluate(
            dsl = dsl,
            contextEntries = mapOf("reference" to "REF-ABCDE")
        )

        assertTrue(
            actual = actions.isEmpty(),
            message = "Expected no actions when rule condition does not match"
        )
    }

    @Test
    fun `extracted argument is null when pattern does not match the field value at runtime`() {
        // The condition uses a loose regex so the rule fires, but the extraction
        // pattern targets a specific sub-format that is absent in the input.
        val dsl = """
            rule "extract-loose" {
              when
                reference regex ".*"
              then
                extract reference regex("TXN-([0-9]+)", 1) label ${'$'}1
            }
        """.trimIndent()

        val actions = evaluate(
            dsl = dsl,
            contextEntries = mapOf("reference" to "NO-MATCH-HERE")
        )

        assertEquals(expected = 1, actual = actions.size, message = "Rule should still fire")
        assertNull(
            actual = actions[0].arguments.first(),
            message = "Extracted value should be null when extraction pattern does not match"
        )
    }

    @Test
    fun `extraction uses group 0 to return full match`() {
        val dsl = """
            rule "extract-full-match" {
              when
                reference regex "TXN-[0-9]+"
              then
                extract reference regex("(TXN-[0-9]+)", 0) label ${'$'}1
            }
        """.trimIndent()

        val actions = evaluate(
            dsl = dsl,
            contextEntries = mapOf("reference" to "prefix TXN-42 suffix")
        )

        assertEquals(expected = 1, actual = actions.size)
        // group 0 is the full match of the outer group
        val extracted = actions[0].arguments.first() as? String
        assertNotNull(actual = extracted)
        assertTrue(
            actual = extracted.contains(other = "TXN-42"),
            message = "Expected full match to contain 'TXN-42' but was '$extracted'"
        )
    }

    @Test
    fun `static action in same rule still works alongside extract action`() {
        val dsl = """
            rule "combined-actions" {
              when
                reference regex "TXN-[0-9]+"
              then
                extract reference regex("TXN-([0-9]+)", 1) label ${'$'}1
                category "transactions"
            }
        """.trimIndent()

        val actions = evaluate(
            dsl = dsl,
            contextEntries = mapOf("reference" to "TXN-777")
        )

        assertEquals(expected = 2, actual = actions.size)
        val labelAction = actions.first { it.name == "label" }
        val categoryAction = actions.first { it.name == "category" }
        assertEquals(expected = "777", actual = labelAction.arguments.first())
        assertEquals(expected = "transactions", actual = categoryAction.arguments.first())
    }

    @Test
    fun `validator rejects invalid regex in extraction`() {
        val dsl = """
            rule "bad-regex" {
              when
                reference regex ".*"
              then
                extract reference regex("[invalid", 1) label ${'$'}1
            }
        """.trimIndent()

        val rules = Parser(input = dsl).parseRules()
        val result = Validator.validate(asts = rules, schema = schema)

        assertTrue(
            actual = result.diagnostics.any { it.message.contains("Invalid regex") },
            message = "Expected a diagnostic about invalid regex pattern"
        )
    }

    @Test
    fun `validator rejects unknown source field in extraction`() {
        val dsl = """
            rule "unknown-field" {
              when
                reference regex ".*"
              then
                extract nonExistentField regex("(.*)", 1) label ${'$'}1
            }
        """.trimIndent()

        val rules = Parser(input = dsl).parseRules()
        val result = Validator.validate(asts = rules, schema = schema)

        assertTrue(
            actual = result.diagnostics.any { it.message.contains("nonExistentField") },
            message = "Expected a diagnostic about unknown source field"
        )
    }

    @Test
    fun `validator rejects extraction on non-text source field`() {
        val dsl = """
            rule "non-text-field" {
              when
                amount > 0
              then
                extract amount regex("([0-9]+)", 1) label ${'$'}1
            }
        """.trimIndent()

        val rules = Parser(input = dsl).parseRules()
        val result = Validator.validate(asts = rules, schema = schema)

        assertTrue(
            actual = result.diagnostics.any { it.message.contains("TEXT") },
            message = "Expected a diagnostic that extraction requires a TEXT field"
        )
    }
}

