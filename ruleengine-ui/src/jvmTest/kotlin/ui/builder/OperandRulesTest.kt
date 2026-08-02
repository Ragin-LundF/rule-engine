package ui.builder

import ui.builder.model.BuilderOperand
import ui.builder.model.BuilderPathStep
import ui.builder.model.catalog.CatalogFieldInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

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

    // ── list variables ────────────────────────────────────────────────────────

    private val listVariable = BuilderOperand.FieldRef(
        path = listOf(BuilderPathStep(name = "\$topics")),
    )
    private val literal = BuilderOperand.Literal(text = "billing", numeric = false)
    private val variableFields = listOf(
        CatalogFieldInfo(id = "\$topics", type = OperatorOptions.LIST_VARIABLE_TYPE),
        CatalogFieldInfo(id = "\$tier", type = OperatorOptions.VARIABLE_TYPE),
    )

    @Test
    fun `a list variable offers contains and nothing else`() {
        val operators = OperandRules.operatorsFor(
            left = listVariable,
            right = literal,
            fields = variableFields,
        )

        assertEquals(expected = listOf(OperatorOptions.CONTAINS), actual = operators)
    }

    /** `contains` on an aggregate or a plain comparison is either rejected or can never match. */
    @Test
    fun `an untyped variable still offers the symbolic comparisons and no contains`() {
        val untyped = BuilderOperand.FieldRef(
            path = listOf(BuilderPathStep(name = "\$tier")),
        )

        val operators = OperandRules.operatorsFor(left = untyped, right = literal, fields = variableFields)

        assertEquals(expected = OperatorOptions.COMPARISON_NUMERIC, actual = operators)
    }

    @Test
    fun `a list variable row does not offer ignoreCase`() {
        assertFalse(
            actual = OperandRules.supportsIgnoreCase(
                left = listVariable,
                right = literal,
                fields = variableFields,
            )
        )
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
