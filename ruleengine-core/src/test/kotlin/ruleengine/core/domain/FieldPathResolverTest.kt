package ruleengine.core.domain

import ruleengine.core.domain.dto.FieldDefinition
import ruleengine.core.domain.dto.FieldId
import ruleengine.core.domain.dto.FieldSchema
import ruleengine.core.domain.dto.FieldType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FieldPathResolverTest {

    private fun field(
        name: String,
        type: FieldType,
        alias: String? = null,
        nested: List<FieldDefinition> = emptyList()
    ): FieldDefinition {
        return FieldDefinition(
            id = FieldId(value = name),
            type = type,
            alias = alias,
            fields = nested.associateBy { it.id }
        )
    }

    private val schema = FieldSchema(
        name = "resolver-v1",
        fields = listOf(
            field(
                name = "shipment",
                type = FieldType.OBJECT,
                nested = listOf(
                    field(name = "transitDays", type = FieldType.INTEGER),
                    field(
                        name = "customer",
                        type = FieldType.OBJECT,
                        nested = listOf(field(name = "loyaltyTier", type = FieldType.TEXT, alias = "tier"))
                    )
                )
            ),
            field(
                name = "parcels",
                type = FieldType.COLLECTION,
                nested = listOf(field(name = "weightKg", type = FieldType.DECIMAL))
            ),
            field(name = "route.legs", type = FieldType.INTEGER),
            field(name = "carrier", type = FieldType.TEXT, alias = "line")
        ).associateBy { it.id }
    )

    @Test
    fun `resolves a nested path to its declaration`() {
        val resolution = FieldPathResolver.resolve(identifier = "shipment.transitDays", schema = schema)

        val resolved = assertIs<FieldPathResolution.Resolved>(value = resolution)
        assertEquals(expected = FieldId(value = "shipment.transitDays"), actual = resolved.id)
        assertEquals(expected = FieldType.INTEGER, actual = resolved.definition.type)
    }

    @Test
    fun `resolves an alias inside a path to the declared name`() {
        val resolution = FieldPathResolver.resolve(identifier = "shipment.customer.tier", schema = schema)

        val resolved = assertIs<FieldPathResolution.Resolved>(value = resolution)
        assertEquals(expected = FieldId(value = "shipment.customer.loyaltyTier"), actual = resolved.id)
    }

    @Test
    fun `resolves a top level alias`() {
        val resolution = FieldPathResolver.resolve(identifier = "line", schema = schema)

        val resolved = assertIs<FieldPathResolution.Resolved>(value = resolution)
        assertEquals(expected = FieldId(value = "carrier"), actual = resolved.id)
    }

    @Test
    fun `prefers a flat dotted declaration over a walk`() {
        val resolution = FieldPathResolver.resolve(identifier = "route.legs", schema = schema)

        val resolved = assertIs<FieldPathResolution.Resolved>(value = resolution)
        assertEquals(expected = FieldId(value = "route.legs"), actual = resolved.id)
    }

    @Test
    fun `reports the collection a path reads through`() {
        val resolution = FieldPathResolver.resolve(identifier = "parcels.weightKg", schema = schema)

        val crossing = assertIs<FieldPathResolution.CrossesCollection>(value = resolution)
        assertEquals(expected = "parcels", actual = crossing.collectionPath)
    }

    @Test
    fun `reports unknown members and unknown roots`() {
        assertIs<FieldPathResolution.Unknown>(
            value = FieldPathResolver.resolve(identifier = "shipment.customer.tir", schema = schema)
        )
        assertIs<FieldPathResolution.Unknown>(
            value = FieldPathResolver.resolve(identifier = "bogus.transitDays", schema = schema)
        )
        assertIs<FieldPathResolution.Unknown>(
            value = FieldPathResolver.resolve(identifier = "bogus", schema = schema)
        )
        assertIs<FieldPathResolution.Unknown>(
            value = FieldPathResolver.resolve(identifier = "shipment.transitDays.extra", schema = schema)
        )
    }

    @Test
    fun `scalar paths cover object trees but not collections`() {
        val paths = FieldPathResolver.scalarPaths(schema = schema).keys.map { it.value }.toSet()

        assertEquals(
            expected = setOf(
                "shipment.transitDays",
                "shipment.customer.loyaltyTier",
                "route.legs",
                "carrier"
            ),
            actual = paths
        )
    }
}
