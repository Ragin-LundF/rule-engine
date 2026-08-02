package ruleengine.evaluator.compiled

import ruleengine.compiler.Compiler
import ruleengine.core.domain.dto.NormalizerId
import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.dsl.parser.Parser
import ruleengine.evaluator.RuleEngine
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.RuleContext
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A member declared inside a collection or an object must match on its declared normalizers, exactly
 * as the same field would at the top level.
 *
 * `PreparedRuleContext` normalises only the values it prepares and deliberately prepares no
 * collection member, so a path reaching one is read raw from the input map. The declared normalizers
 * travel with the compiled path instead.
 */
class CollectionMemberNormalizerTest {

    private val schema = FieldSchema(
        name = "normalizer-schema",
        fields = mapOf(
            FieldId(value = "invoices") to FieldDefinition(
                id = FieldId(value = "invoices"),
                type = FieldType.COLLECTION,
                fields = mapOf(
                    FieldId(value = "customerId") to FieldDefinition(
                        id = FieldId(value = "customerId"),
                        type = FieldType.TEXT,
                        normalizers = listOf(NormalizerId(value = "trim"), NormalizerId(value = "lowercase"))
                    ),
                    FieldId(value = "amount") to FieldDefinition(
                        id = FieldId(value = "amount"),
                        type = FieldType.DECIMAL
                    )
                )
            ),
            FieldId(value = "customer") to FieldDefinition(
                id = FieldId(value = "customer"),
                type = FieldType.OBJECT,
                fields = mapOf(
                    FieldId(value = "country") to FieldDefinition(
                        id = FieldId(value = "country"),
                        type = FieldType.TEXT,
                        normalizers = listOf(NormalizerId(value = "uppercase"))
                    )
                )
            )
        )
    )

    @Test
    fun `a filter on a collection member matches the normalized value`() {
        val matched = evaluate(
            condition = """sum(invoices[customerId == "acme"].amount) > 100""",
            fields = arrayOf("invoices" to listOf(mapOf("customerId" to "  ACME  ", "amount" to 500)))
        )

        assertTrue(
            actual = matched,
            message = "trim + lowercase are declared on customerId, so '  ACME  ' must match \"acme\""
        )
    }

    @Test
    fun `a filter on a collection member does not match a different value`() {
        val matched = evaluate(
            condition = """sum(invoices[customerId == "acme"].amount) > 100""",
            fields = arrayOf("invoices" to listOf(mapOf("customerId" to "  OTHER  ", "amount" to 500)))
        )

        assertFalse(
            actual = matched,
            message = "normalization must not make unrelated values equal"
        )
    }

    // ── the literal side ──────────────────────────────────────────────────────
    //
    // A field's normalizers apply to the literal it is compared against, not only to the value read
    // from the input. The named-operator path always did this; the symbolic one did not, so the two
    // spellings of one comparison answered differently on the same data.

    @Test
    fun `both spellings of a filter comparison normalize the literal`() {
        val fields = arrayOf<Pair<String, Any?>>(
            "invoices" to listOf(mapOf("customerId" to "acme", "amount" to 500))
        )

        assertTrue(
            actual = evaluate(condition = """sum(invoices[customerId == "ACME"].amount) > 100""", fields = fields),
            message = "lowercase is declared on customerId, so the symbolic form must match \"ACME\""
        )
        assertTrue(
            actual = evaluate(condition = """sum(invoices[customerId equals "ACME"].amount) > 100""", fields = fields),
            message = "the named form has always matched; both spellings must now agree"
        )
    }

    @Test
    fun `a top-level comparison normalizes the literal`() {
        assertTrue(
            actual = evaluate(
                condition = """customer.country == "de" and sum(invoices.amount) > 100""",
                fields = arrayOf(
                    "customer" to mapOf("country" to "DE"),
                    "invoices" to listOf(mapOf("amount" to 500))
                )
            ),
            message = "uppercase is declared on customer.country, so the literal \"de\" must match"
        )
    }

    @Test
    fun `a written-out list normalizes every item`() {
        assertTrue(
            actual = evaluate(
                condition = """sum(invoices[customerId in ["ACME", "OTHER"]].amount) > 100""",
                fields = arrayOf("invoices" to listOf(mapOf("customerId" to "acme", "amount" to 500)))
            ),
            message = "each item is normalized like a single literal"
        )
    }

    /** Normalizing the literal must not make unrelated values equal. */
    @Test
    fun `normalizing the literal still discriminates`() {
        assertFalse(
            actual = evaluate(
                condition = """sum(invoices[customerId == "ACME"].amount) > 100""",
                fields = arrayOf("invoices" to listOf(mapOf("customerId" to "other", "amount" to 500)))
            ),
            message = "a different customer must not match"
        )
    }

    /**
     * The object case takes the prepared fast path rather than the raw walk. It is asserted here so
     * both halves of the same guarantee stay covered by one test class.
     */
    @Test
    fun `a member of an object matches the normalized value`() {
        val matched = evaluate(
            condition = """customer.country == "DE" and sum(invoices.amount) > 100""",
            fields = arrayOf(
                "customer" to mapOf("country" to "de"),
                "invoices" to listOf(mapOf("amount" to 500))
            )
        )

        assertTrue(actual = matched, message = "uppercase is declared on customer.country")
    }

    private fun evaluate(condition: String, fields: Array<Pair<String, Any?>>): Boolean {
        val rule = """
            rule "normalizer-test" {
              when
                $condition
              then
                flag "ok"
            }
        """.trimIndent()
        val compiled = Compiler.compileRules(asts = Parser(input = rule).parseRules(), schema = schema)
        val prepared = PreparedRuleContext.prepare(ctx = RuleContext.of(*fields), schema = schema)
        return RuleEngine(compiledRules = compiled).evaluate(prepared = prepared).matches.isNotEmpty()
    }
}
