package ruleengine.compiler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ruleengine.core.domain.*
import ruleengine.core.normalizer.NormalizerRegistry
import ruleengine.dsl.parser.Parser
import ruleengine.evaluator.RuleEngine
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.RuleContext

/**
 * Full operator coverage test — verifies every supported operator end-to-end
 * within a single schema and rule-set, then also loads the real resource files
 * (full-schema.yaml + rule files) to ensure they parse and validate cleanly.
 */
class FullOperatorCoverageTest {

    // ── Shared schema covering all field types ────────────────────────────

    private val schema = FieldSchema(
        name = "full-test",
        fields = mapOf(
            // TEXT — no normalizer (ignoreCase carries the weight in relevant tests)
            FieldId("purpose") to FieldDefinition(
                id = FieldId("purpose"), type = FieldType.TEXT,
                normalizers = listOf(NormalizerId("trim")),
                operators = setOf(
                    OperatorId("equals"), OperatorId("contains"),
                    OperatorId("startsWith"), OperatorId("endsWith"),
                    OperatorId("in"), OperatorId("regex")
                )
            ),
            // TEXT — with lowercase normalizer (classic approach)
            FieldId("purposeNorm") to FieldDefinition(
                id = FieldId("purposeNorm"), type = FieldType.TEXT,
                normalizers = listOf(NormalizerId("trim"), NormalizerId("lowercase")),
                operators = setOf(OperatorId("equals"), OperatorId("contains"), OperatorId("endsWith"))
            ),
            FieldId("iban") to FieldDefinition(
                id = FieldId("iban"), type = FieldType.TEXT,
                normalizers = listOf(NormalizerId("trim")),
                operators = setOf(OperatorId("regex"), OperatorId("startsWith"))
            ),
            FieldId("sepaCode") to FieldDefinition(
                id = FieldId("sepaCode"), type = FieldType.TEXT,
                normalizers = listOf(NormalizerId("trim"), NormalizerId("uppercase")),
                operators = setOf(OperatorId("equals"), OperatorId("in"))
            ),
            FieldId("counterparty") to FieldDefinition(
                id = FieldId("counterparty"), type = FieldType.TEXT,
                normalizers = listOf(NormalizerId("trim")),
                operators = setOf(OperatorId("equals"), OperatorId("contains"), OperatorId("endsWith"))
            ),
            // DECIMAL
            FieldId("amount") to FieldDefinition(
                id = FieldId("amount"), type = FieldType.DECIMAL,
                normalizers = emptyList(),
                operators = setOf(
                    OperatorId("equals"), OperatorId("gt"), OperatorId("gte"),
                    OperatorId("lt"), OperatorId("lte"), OperatorId("between")
                )
            ),
            // INTEGER
            FieldId("count") to FieldDefinition(
                id = FieldId("count"), type = FieldType.INTEGER,
                normalizers = emptyList(),
                operators = setOf(
                    OperatorId("equals"), OperatorId("gt"), OperatorId("gte"),
                    OperatorId("lt"), OperatorId("lte"), OperatorId("between")
                )
            ),
            // STRING_SET
            FieldId("tags") to FieldDefinition(
                id = FieldId("tags"), type = FieldType.STRING_SET,
                normalizers = listOf(NormalizerId("trim"), NormalizerId("lowercase")),
                operators = setOf(OperatorId("containsAny"), OperatorId("containsAll"))
            )
        )
    )

    private val allOperatorsRuleText = """
        # equals (case-sensitive via normalizer)
        rule "eq-sepa" {
          when sepaCode equals "PMNT"
          then label "eq-match"
        }

        # equals ignoreCase
        rule "eq-ic-counterparty" {
          when counterparty equals "Netflix" ignoreCase
          then label "streaming"
        }

        # contains (via normalizer lowercase)
        rule "contains-norm" {
          when purposeNorm contains "miete"
          then label "rent"
        }

        # contains ignoreCase (no normalizer, flag does the work)
        rule "contains-ic" {
          when purpose contains "Rueckuberweisung" ignoreCase
          then label "chargeback"
        }

        # startsWith
        rule "starts-iban" {
          when iban startsWith "DE"
          then label "german-iban"
        }

        # startsWith ignoreCase
        rule "starts-ic" {
          when purpose startsWith "Sammel" ignoreCase
          then label "batch"
        }

        # endsWith
        rule "ends-norm" {
          when purposeNorm endsWith "gmbh"
          then label "company"
        }

        # endsWith ignoreCase
        rule "ends-ic" {
          when purpose endsWith "Versicherung" ignoreCase
          then label "insurance"
        }

        # in (list)
        rule "in-sepa" {
          when sepaCode in ["CCRD", "DCRD"]
          then label "card"
        }

        # regex
        rule "regex-iban" {
          when iban regex "^(DE|AT|CH)"
          then label "dach"
        }

        # regex ignoreCase
        rule "regex-fraud-ic" {
          when purpose regex "betrug|phishing" ignoreCase
          then label "fraud"
        }

        # not + contains
        rule "not-spam" {
          when not purpose contains "spam"
          then label "not-spam"
        }

        # decimal gt / gte / lt / lte
        rule "amount-gt" {
          when amount > 0
          then label "credit"
        }
        rule "amount-gte" {
          when amount >= 500
          then label "big-credit"
        }
        rule "amount-lt" {
          when amount < 0
          then label "debit"
        }
        rule "amount-lte" {
          when amount <= -500
          then label "big-debit"
        }

        # decimal between
        rule "amount-between" {
          when amount between 100 5000
          then label "normal-range"
        }

        # integer gt / gte / lt / lte / between
        rule "count-gt" {
          when count > 0
          then label "has-count"
        }
        rule "count-between" {
          when count between 2 10
          then label "count-range"
        }

        # string_set containsAny / containsAll
        rule "tag-any" {
          when tags containsAny ["premium", "vip"]
          then label "premium-customer"
        }
        rule "tag-all" {
          when tags containsAll ["verified", "premium"]
          then label "trusted"
        }

        # bracket group (OR inside AND)
        rule "bracket-or" {
          when
            (purpose contains "rueckuberweisung" ignoreCase
            or purpose contains "nicht gedeckt" ignoreCase)
            and amount < 0
          then label "bracket-chargeback"
        }
    """.trimIndent()

    private fun buildEngine(): RuleEngine {
        val asts = Parser(allOperatorsRuleText).parseRules()
        val validation = Validator.validate(asts, schema)
        assertTrue(validation.isValid, "Validation failed: ${validation.diagnostics}")
        val compiled = Compiler.compileRules(asts, schema, NormalizerRegistry.default)
        return RuleEngine(compiledRules = compiled, schema = schema)
    }

    private fun ctx(vararg pairs: Pair<String, Any?>): PreparedRuleContext =
        PreparedRuleContext.prepare(RuleContext.of(*pairs), schema)

    // ─── Individual operator assertions ────────────────────────────────────

    @Test
    fun `equals matches SEPA code after uppercase normalization`() {
        val e = buildEngine()
        assertTrue(e.evaluate(ctx("sepaCode" to "pmnt")).matches.any { it.ruleId == "eq-sepa" })
        assertTrue(e.evaluate(ctx("sepaCode" to "PMNT")).matches.any { it.ruleId == "eq-sepa" })
    }

    @Test
    fun `equals ignoreCase on counterparty`() {
        val e = buildEngine()
        assertTrue(e.evaluate(ctx("counterparty" to "NETFLIX")).matches.any { it.ruleId == "eq-ic-counterparty" })
        assertTrue(e.evaluate(ctx("counterparty" to "netflix")).matches.any { it.ruleId == "eq-ic-counterparty" })
        assertTrue(e.evaluate(ctx("counterparty" to "Netflix")).matches.any { it.ruleId == "eq-ic-counterparty" })
    }

    @Test
    fun `contains via normalizer and via ignoreCase`() {
        val e = buildEngine()
        // purposeNorm has lowercase normalizer → "Miete" becomes "miete"
        assertTrue(e.evaluate(ctx("purposeNorm" to "Miete Januar")).matches.any { it.ruleId == "contains-norm" })
        // purpose has trim only → ignoreCase needed
        assertTrue(e.evaluate(ctx("purpose" to "RUECKUBERWEISUNG 001")).matches.any { it.ruleId == "contains-ic" })
    }

    @Test
    fun `startsWith and endsWith with ignoreCase`() {
        val e = buildEngine()
        assertTrue(e.evaluate(ctx("iban" to "DE89370400440532013000")).matches.any { it.ruleId == "starts-iban" })
        assertTrue(e.evaluate(ctx("purpose" to "SAMMELAUFTRAG 001")).matches.any { it.ruleId == "starts-ic" })
        assertTrue(e.evaluate(ctx("purposeNorm" to "muster gmbh")).matches.any { it.ruleId == "ends-norm" })
        assertTrue(e.evaluate(ctx("purpose" to "Allianz VERSICHERUNG")).matches.any { it.ruleId == "ends-ic" })
    }

    @Test
    fun `in operator list match`() {
        val e = buildEngine()
        assertTrue(e.evaluate(ctx("sepaCode" to "CCRD")).matches.any { it.ruleId == "in-sepa" })
        assertTrue(e.evaluate(ctx("sepaCode" to "dcrd")).matches.any { it.ruleId == "in-sepa" }) // uppercase normalizer
        assertTrue(e.evaluate(ctx("sepaCode" to "PMNT")).matches.none { it.ruleId == "in-sepa" })
    }

    @Test
    fun `regex operator with and without ignoreCase`() {
        val e = buildEngine()
        assertTrue(e.evaluate(ctx("iban" to "DE89370400440532013000")).matches.any { it.ruleId == "regex-iban" })
        assertTrue(e.evaluate(ctx("iban" to "GB29NWBK60161331926819")).matches.none { it.ruleId == "regex-iban" })
        assertTrue(e.evaluate(ctx("purpose" to "BETRUG!")).matches.any { it.ruleId == "regex-fraud-ic" })
        assertTrue(e.evaluate(ctx("purpose" to "Normal payment")).matches.none { it.ruleId == "regex-fraud-ic" })
    }

    @Test
    fun `not operator`() {
        val e = buildEngine()
        assertTrue(e.evaluate(ctx("purpose" to "Normal payment")).matches.any { it.ruleId == "not-spam" })
        assertTrue(e.evaluate(ctx("purpose" to "spam offer")).matches.none { it.ruleId == "not-spam" })
    }

    @Test
    fun `decimal comparison operators gt gte lt lte`() {
        val e = buildEngine()
        val debit = ctx("amount" to "-600")
        assertTrue(e.evaluate(debit).matches.any { it.ruleId == "amount-lt" })
        assertTrue(e.evaluate(debit).matches.any { it.ruleId == "amount-lte" })
        assertTrue(e.evaluate(debit).matches.none { it.ruleId == "amount-gt" })

        val credit = ctx("amount" to "850")
        assertTrue(e.evaluate(credit).matches.any { it.ruleId == "amount-gt" })
        assertTrue(e.evaluate(credit).matches.any { it.ruleId == "amount-gte" })
        assertTrue(e.evaluate(credit).matches.none { it.ruleId == "amount-lt" })
    }

    @Test
    fun `decimal between operator`() {
        val e = buildEngine()
        assertTrue(e.evaluate(ctx("amount" to "2500")).matches.any { it.ruleId == "amount-between" })
        assertTrue(e.evaluate(ctx("amount" to "100")).matches.any  { it.ruleId == "amount-between" })
        assertTrue(e.evaluate(ctx("amount" to "5000")).matches.any { it.ruleId == "amount-between" })
        assertTrue(e.evaluate(ctx("amount" to "99")).matches.none  { it.ruleId == "amount-between" })
        assertTrue(e.evaluate(ctx("amount" to "5001")).matches.none{ it.ruleId == "amount-between" })
    }

    @Test
    fun `integer between operator`() {
        val e = buildEngine()
        assertTrue(e.evaluate(ctx("count" to 5)).matches.any { it.ruleId == "count-between" })
        assertTrue(e.evaluate(ctx("count" to 1)).matches.none { it.ruleId == "count-between" })
        assertTrue(e.evaluate(ctx("count" to 11)).matches.none { it.ruleId == "count-between" })
    }

    @Test
    fun `string set containsAny and containsAll`() {
        val e = buildEngine()
        val premiumVerified = ctx("tags" to listOf("premium", "verified"))
        assertTrue(e.evaluate(premiumVerified).matches.any { it.ruleId == "tag-any" })
        assertTrue(e.evaluate(premiumVerified).matches.any { it.ruleId == "tag-all" })

        val vipOnly = ctx("tags" to listOf("vip"))
        assertTrue(e.evaluate(vipOnly).matches.any { it.ruleId == "tag-any" })
        assertTrue(e.evaluate(vipOnly).matches.none { it.ruleId == "tag-all" }) // missing "premium"
    }

    @Test
    fun `bracket OR group with AND`() {
        val e = buildEngine()
        // purpose contains "Rueckuberweisung" AND amount < 0 → matches rule "bracket-or"
        assertTrue(e.evaluate(ctx("purpose" to "Rueckuberweisung 001", "amount" to "-250"))
            .matches.any { it.ruleId == "bracket-or" })
        // purpose contains "Nicht Gedeckt" AND amount < 0 → matches
        assertTrue(e.evaluate(ctx("purpose" to "Lastschrift Nicht Gedeckt", "amount" to "-10"))
            .matches.any { it.ruleId == "bracket-or" })
        // purpose matches but amount positive → does NOT match
        assertTrue(e.evaluate(ctx("purpose" to "Rueckuberweisung 001", "amount" to "10"))
            .matches.none { it.ruleId == "bracket-or" })
    }

    // ─── Resource file loading test ────────────────────────────────────────

    @Test
    fun `chargebacks rule file parses and validates without errors`() {
        val url = javaClass.classLoader.getResource("rules-full/chargebacks.rule")
            ?: error("chargebacks.rule not found on classpath")
        val text = java.io.File(url.toURI()).readText()
        val asts = Parser(text).parseRules()
        assertTrue(asts.isNotEmpty())

        val schemaUrl = javaClass.classLoader.getResource("full-schema.yaml")
            ?: error("full-schema.yaml not found")
        val loadedSchema = ruleengine.schema.FieldSchemaLoader.load(java.nio.file.Path.of(schemaUrl.toURI()))
        val result = Validator.validate(asts, loadedSchema)
        assertTrue(result.isValid, "chargebacks.rule validation failed: ${result.diagnostics}")
    }

    @Test
    fun `fraud-detection rule file parses and validates without errors`() {
        val url = javaClass.classLoader.getResource("rules-full/fraud-detection.rule")
            ?: error("fraud-detection.rule not found on classpath")
        val text = java.io.File(url.toURI()).readText()
        val asts = Parser(text).parseRules()
        assertTrue(asts.isNotEmpty())

        val schemaUrl = javaClass.classLoader.getResource("full-schema.yaml") ?: error("schema not found")
        val loadedSchema = ruleengine.schema.FieldSchemaLoader.load(java.nio.file.Path.of(schemaUrl.toURI()))
        val result = Validator.validate(asts, loadedSchema)
        assertTrue(result.isValid, "fraud-detection.rule validation failed: ${result.diagnostics}")
    }

    @Test
    fun `sepa-classification rule file parses and validates without errors`() {
        val url = javaClass.classLoader.getResource("rules-full/sepa-classification.rule")
            ?: error("sepa-classification.rule not found on classpath")
        val text = java.io.File(url.toURI()).readText()
        val asts = Parser(text).parseRules()
        assertTrue(asts.isNotEmpty())

        val schemaUrl = javaClass.classLoader.getResource("full-schema.yaml") ?: error("schema not found")
        val loadedSchema = ruleengine.schema.FieldSchemaLoader.load(java.nio.file.Path.of(schemaUrl.toURI()))
        val result = Validator.validate(asts, loadedSchema)
        assertTrue(result.isValid, "sepa-classification.rule validation failed: ${result.diagnostics}")
    }
}




