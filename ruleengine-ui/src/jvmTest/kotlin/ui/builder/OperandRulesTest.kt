package ui.builder

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What a filter row on a path segment may name.
 *
 * The engine evaluates a filter against the element, walking any path it contains, so
 * `parcels[origin.hub == "HAM"]` is a legal filter over a nested object. The dropdown has to offer
 * that dotted path or the round-tripped rule shows a value that is not in its own option list.
 */
class OperandRulesTest {

    private val fields = listOf(
        CatalogFieldInfo(
            id = "parcels",
            type = "collection",
            nestedFields = listOf(
                CatalogFieldInfo(id = "code", type = "text"),
                CatalogFieldInfo(id = "damaged", type = "boolean"),
                CatalogFieldInfo(
                    id = "origin",
                    type = "object",
                    nestedFields = listOf(CatalogFieldInfo(id = "hub", type = "text")),
                ),
                CatalogFieldInfo(
                    id = "scans",
                    type = "collection",
                    nestedFields = listOf(CatalogFieldInfo(id = "site", type = "text")),
                ),
            ),
        ),
    )

    private val path = listOf(BuilderPathStep(name = "parcels"))

    @Test
    fun `filter options offer nested object members by their dotted path`() {
        val options = OperandRules.filterFieldOptions(fields = fields, path = path, depth = 0)

        // 'origin' itself is gone: it holds no value to compare. Its scalar member is offered instead.
        // 'scans' is gone too — projecting a collection yields many values, not one.
        assertEquals(expected = listOf("code", "damaged", "origin.hub"), actual = options.map { it.id })
    }

    @Test
    fun `an undeclared segment offers nothing`() {
        val options = OperandRules.filterFieldOptions(
            fields = fields,
            path = listOf(BuilderPathStep(name = "unknown")),
            depth = 0,
        )

        assertEquals(expected = emptyList(), actual = options)
    }
}
