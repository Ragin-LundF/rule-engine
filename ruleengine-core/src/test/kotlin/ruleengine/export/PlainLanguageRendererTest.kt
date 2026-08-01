package ruleengine.export

import ruleengine.core.domain.dto.FieldDefinition
import ruleengine.core.domain.dto.FieldId
import ruleengine.core.domain.dto.FieldSchema
import ruleengine.core.domain.dto.FieldType
import ruleengine.dsl.parser.Parser
import ruleengine.export.dto.PlainAll
import ruleengine.export.dto.PlainAny
import ruleengine.export.dto.PlainCondition
import ruleengine.export.dto.PlainLeaf
import ruleengine.export.dto.PlainNot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * The sentences a customer reads are the product here, so these tests assert the exact wording.
 *
 * Brittle on purpose: a silent change to how a rule is described is the one regression this feature
 * cannot afford, because nobody reviewing the diff would notice the document now says something
 * subtly different from what the engine does.
 */
class PlainLanguageRendererTest {

    private val schema = FieldSchema(
        name = "warehouse",
        fields = mapOf(
            FieldId(value = "shipment") to FieldDefinition(
                id = FieldId(value = "shipment"),
                type = FieldType.OBJECT,
                fields = mapOf(
                    FieldId(value = "transitDays") to FieldDefinition(
                        id = FieldId(value = "transitDays"),
                        type = FieldType.INTEGER,
                    ),
                    FieldId(value = "declaredValue") to FieldDefinition(
                        id = FieldId(value = "declaredValue"),
                        type = FieldType.DECIMAL,
                    ),
                    FieldId(value = "service") to FieldDefinition(
                        id = FieldId(value = "service"),
                        type = FieldType.TEXT,
                    ),
                    FieldId(value = "customer") to FieldDefinition(
                        id = FieldId(value = "customer"),
                        type = FieldType.OBJECT,
                        fields = mapOf(
                            FieldId(value = "tier") to FieldDefinition(
                                id = FieldId(value = "tier"),
                                type = FieldType.TEXT,
                            ),
                        ),
                    ),
                ),
            ),
            FieldId(value = "parcels") to FieldDefinition(
                id = FieldId(value = "parcels"),
                type = FieldType.COLLECTION,
                fields = mapOf(
                    FieldId(value = "weightKg") to FieldDefinition(
                        id = FieldId(value = "weightKg"),
                        type = FieldType.DECIMAL,
                    ),
                ),
            ),
            FieldId(value = "amount") to FieldDefinition(
                id = FieldId(value = "amount"),
                type = FieldType.DECIMAL,
            ),
            FieldId(value = "tags") to FieldDefinition(
                id = FieldId(value = "tags"),
                type = FieldType.STRING_SET,
            ),
            FieldId(value = "bookedOn") to FieldDefinition(
                id = FieldId(value = "bookedOn"),
                type = FieldType.DATE,
            ),
            FieldId(value = "bookedAt") to FieldDefinition(
                id = FieldId(value = "bookedAt"),
                type = FieldType.DATE_TIME,
            ),
        ),
    )

    /** Renders the condition of a single-rule DSL snippet. */
    private fun render(condition: String, withSchema: Boolean = true): PlainCondition {
        val dsl = """
            rule "r" {
              when
                $condition
              then
                label "a"
            }
        """.trimIndent()
        val rule = Parser(input = dsl).parseRules().single()

        return PlainLanguageRenderer.render(
            expr = rule.condition,
            schema = if (withSchema) schema else null,
        )
    }

    private fun leafText(condition: String, withSchema: Boolean = true): String {
        val rendered = render(condition = condition, withSchema = withSchema)
        assertIs<PlainLeaf>(value = rendered)

        return rendered.text
    }

    // ── operators on a plain field ────────────────────────────────────────────

    @Test
    fun `renders every named operator as a phrase`() {
        val expected = mapOf(
            "shipment.service equals \"express\"" to "Service is \"express\"",
            "shipment.service contains \"exp\"" to "Service contains \"exp\"",
            "shipment.service startsWith \"ex\"" to "Service starts with \"ex\"",
            "shipment.service endsWith \"ss\"" to "Service ends with \"ss\"",
            "shipment.service regex \"^ex\"" to "Service matches the pattern \"^ex\"",
            "shipment.service in [\"a\", \"b\"]" to "Service is one of \"a\", \"b\"",
            "shipment.transitDays gt 2" to "Transit Days is more than 2",
            "shipment.transitDays gte 2" to "Transit Days is at least 2",
            "shipment.transitDays lt 2" to "Transit Days is less than 2",
            "shipment.transitDays lte 2" to "Transit Days is at most 2",
            "shipment.declaredValue between 1000 25000" to "Declared Value is between 1000 and 25000",
            "tags containsAny [\"vip\", \"premium\"]" to "Tags includes at least one of \"vip\", \"premium\"",
            "tags containsAll [\"vip\", \"premium\"]" to "Tags includes all of \"vip\", \"premium\"",
        )

        expected.forEach { (condition, sentence) ->
            assertEquals(expected = sentence, actual = leafText(condition = condition))
        }
    }

    @Test
    fun `renders symbolic operators the same way as their named form`() {
        assertEquals(
            expected = leafText(condition = "shipment.transitDays gte 2"),
            actual = leafText(condition = "shipment.transitDays >= 2"),
        )
    }

    @Test
    fun `renders a boolean literal without quotes`() {
        assertEquals(expected = "Amount is 0", actual = leafText(condition = "amount equals 0"))
    }

    @Test
    fun `notes case insensitivity rather than dropping it`() {
        assertEquals(
            expected = "Service is \"express\", ignoring capitalisation",
            actual = leafText(condition = "shipment.service equals \"express\" ignoreCase"),
        )
    }

    // ── dates ─────────────────────────────────────────────────────────────────

    @Test
    fun `a date reads as a point in time rather than a quantity`() {
        val expected = mapOf(
            "bookedOn equals \"2024-06-15\"" to "Booked On is on 2024-06-15",
            "bookedOn gt \"2024-06-15\"" to "Booked On is after 2024-06-15",
            "bookedOn gte \"2024-06-15\"" to "Booked On is on or after 2024-06-15",
            "bookedOn lt \"2024-06-15\"" to "Booked On is before 2024-06-15",
            "bookedOn lte \"2024-06-15\"" to "Booked On is on or before 2024-06-15",
        )

        expected.forEach { (condition, sentence) ->
            assertEquals(expected = sentence, actual = leafText(condition = condition))
        }
    }

    @Test
    fun `a date_time says at rather than on`() {
        // A date compares by calendar day, a date_time by instant — "on 09:30" would overstate the
        // precision of a date comparison and understate a date_time one.
        assertEquals(
            expected = "Booked At is at or after 2024-06-15T09:30:00",
            actual = leafText(condition = "bookedAt gte \"2024-06-15T09:30:00\""),
        )
    }

    @Test
    fun `a date range keeps the between wording`() {
        assertEquals(
            expected = "Booked On is between 2024-01-01 and 2024-12-31",
            actual = leafText(condition = "bookedOn between \"2024-01-01\" \"2024-12-31\""),
        )
    }

    @Test
    fun `without a schema a date falls back to the numeric wording`() {
        // The type is what makes the temporal wording correct; guessing it from the literal would
        // rename any text field that happens to hold something date-shaped.
        assertEquals(
            expected = "Booked On is at least \"2024-06-15\"",
            actual = leafText(condition = "bookedOn gte \"2024-06-15\"", withSchema = false),
        )
    }

    @Test
    fun `a number still reads as a quantity`() {
        assertEquals(
            expected = "Transit Days is at least 2",
            actual = leafText(condition = "shipment.transitDays gte 2"),
        )
    }

    // ── field labels ──────────────────────────────────────────────────────────

    @Test
    fun `drops a structure root and keeps the rest of the path`() {
        assertEquals(
            expected = "Customer › Tier is \"gold\"",
            actual = leafText(condition = "shipment.customer.tier equals \"gold\""),
        )
    }

    @Test
    fun `keeps every segment when no schema says the root is a structure`() {
        // Without a schema, dropping the first segment would silently rename the field.
        assertEquals(
            expected = "Shipment › Customer › Tier is \"gold\"",
            actual = leafText(condition = "shipment.customer.tier equals \"gold\"", withSchema = false),
        )
    }

    @Test
    fun `prefers a declared alias over the derived label`() {
        val aliased = FieldSchema(
            name = "aliased",
            fields = mapOf(
                FieldId(value = "declaredValue") to FieldDefinition(
                    id = FieldId(value = "declaredValue"),
                    type = FieldType.DECIMAL,
                    alias = "Insured amount",
                ),
            ),
        )
        val rule = Parser(
            input = """
                rule "r" {
                  when
                    declaredValue gt 100
                  then
                    label "a"
                }
            """.trimIndent()
        ).parseRules().single()

        val rendered = PlainLanguageRenderer.render(expr = rule.condition, schema = aliased)
        assertIs<PlainLeaf>(value = rendered)
        assertEquals(expected = "Insured amount is more than 100", actual = rendered.text)
    }

    // ── aggregates ────────────────────────────────────────────────────────────

    @Test
    fun `renders an aggregate over a plain collection path`() {
        assertEquals(
            expected = "the total Weight Kg of parcels is more than 100",
            actual = leafText(condition = "sum(parcels.weightKg) > 100"),
        )
    }

    @Test
    fun `renders count over a filtered collection without a measured member`() {
        assertEquals(
            expected = "the number of parcels where Damaged is true is more than 0",
            actual = leafText(condition = "count(parcels[damaged == true]) > 0"),
        )
    }

    @Test
    fun `renders an aggregate of a member of a filtered collection`() {
        assertEquals(
            expected = "the total Weight Kg of parcels where Category is \"fragile\" " +
                "is more than the total Weight Kg of parcels multiplied by 0.25",
            actual = leafText(
                condition = "sum(parcels[category == \"fragile\"].weightKg) > sum(parcels.weightKg) * 0.25"
            ),
        )
    }

    @Test
    fun `names every aggregate function`() {
        val expected = mapOf(
            "sum" to "the total Weight Kg of parcels",
            "count" to "the number of Weight Kg values of parcels",
            "avg" to "the average Weight Kg of parcels",
            "median" to "the median Weight Kg of parcels",
            "max" to "the highest Weight Kg of parcels",
            "min" to "the lowest Weight Kg of parcels",
        )

        expected.forEach { (function, phrase) ->
            assertEquals(
                expected = "$phrase is more than 1",
                actual = leafText(condition = "$function(parcels.weightKg) > 1"),
            )
        }
    }

    @Test
    fun `renders a filter that reads a nested member of the filtered element`() {
        assertEquals(
            expected = "the number of parcels where Origin › Hub is \"HAM\" is at least 2",
            actual = leafText(condition = "count(parcels[origin.hub == \"HAM\"]) >= 2"),
        )
    }

    // ── boolean structure ─────────────────────────────────────────────────────

    @Test
    fun `an and becomes an all node with one child per condition`() {
        val rendered = render(
            condition = "shipment.customer.tier equals \"gold\"\n    and shipment.service contains \"express\""
        )
        assertIs<PlainAll>(value = rendered)
        assertEquals(
            expected = listOf(
                PlainLeaf(text = "Customer › Tier is \"gold\""),
                PlainLeaf(text = "Service contains \"express\""),
            ),
            actual = rendered.children,
        )
    }

    @Test
    fun `an or becomes an any node`() {
        val rendered = render(
            condition = "shipment.service contains \"a\"\n    or shipment.service contains \"b\""
        )
        assertIs<PlainAny>(value = rendered)
        assertEquals(expected = 2, actual = rendered.children.size)
    }

    @Test
    fun `a not keeps its own node rather than inverting the child wording`() {
        // `not (a and b)` is not `not a and not b`, so the negation cannot be folded into the leaves.
        val rendered = render(condition = "not (shipment.service contains \"a\" and amount gt 1)")
        assertIs<PlainNot>(value = rendered)
        assertIs<PlainAll>(value = rendered.child)
    }

    @Test
    fun `nested groups keep their structure`() {
        val rendered = render(
            condition = "(shipment.service contains \"a\" or shipment.service contains \"b\")\n" +
                "    and amount gte 500"
        )
        assertIs<PlainAll>(value = rendered)
        assertIs<PlainAny>(value = rendered.children.first())
        assertEquals(expected = PlainLeaf(text = "Amount is at least 500"), actual = rendered.children[1])
    }
}
