package ruleengine.evaluator.context

import ruleengine.core.domain.dto.FieldId
import ruleengine.evaluator.context.dto.PreparedInteger
import ruleengine.evaluator.context.dto.PreparedText
import ruleengine.schema.FieldSchemaLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A scalar declared inside an `object` must be available under its dotted path, because that is the field id
 * a compiled plain condition asks for. Members of a `collection` must not be, since a projection over a list
 * has no single value to prepare.
 */
class PreparedNestedFieldTest {

    private val schema = FieldSchemaLoader.loadFromString(
        content = """
            schema: prepared-nested-v1

            fields:
              shipment:
                type: object
                fields:
                  transitDays:
                    type: integer
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

    private val context = RuleContext.of(
        "shipment" to mapOf(
            "transitDays" to 4,
            "customer" to mapOf("loyaltyTier" to " Gold ")
        ),
        "parcels" to listOf(mapOf("weightKg" to 12.5))
    )

    @Test
    fun `nested object leaves are prepared under their dotted path`() {
        val prepared = PreparedRuleContext.prepare(ctx = context, schema = schema)

        assertEquals(
            expected = PreparedInteger(value = 4),
            actual = prepared.get(field = FieldId(value = "shipment.transitDays"))
        )
        assertEquals(
            expected = PreparedText(original = " Gold ", normalized = "gold"),
            actual = prepared.get(field = FieldId(value = "shipment.customer.loyaltyTier")),
            message = "Normalizers declared on a nested member must be applied"
        )
    }

    @Test
    fun `collection members and structures themselves are not prepared`() {
        val prepared = PreparedRuleContext.prepare(ctx = context, schema = schema)

        assertNull(actual = prepared.get(field = FieldId(value = "parcels.weightKg")))
        assertNull(actual = prepared.get(field = FieldId(value = "parcels")))
        assertNull(actual = prepared.get(field = FieldId(value = "shipment")))
        assertNull(
            actual = prepared.get(field = FieldId(value = "shipment.customer.tier")),
            message = "Only the declared name is prepared; the alias is resolved before the lookup"
        )
    }

    @Test
    fun `a flat dotted declaration keeps its own value`() {
        val mixedSchema = FieldSchemaLoader.loadFromString(
            content = """
                schema: prepared-mixed-v1

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

        val prepared = PreparedRuleContext.prepare(ctx = context, schema = mixedSchema)

        assertEquals(
            expected = PreparedText(original = "4", normalized = "4"),
            actual = prepared.get(field = FieldId(value = "shipment.transitDays")),
            message = "The flat declaration is prepared first and must not be replaced by the nested one"
        )
    }
}
