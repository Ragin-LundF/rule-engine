package ui.builder

import ui.builder.model.BuilderOperand
import ui.builder.model.BuilderPathStep
import ui.builder.model.catalog.CatalogFieldInfo
import ui.builder.model.fieldOperand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    // ── the catalog a filter's operands resolve against ───────────────────────
    //
    // The engine lays the element's members over the document's fields and lets the element win for a
    // shared name (`ValueExpressionCompiler.elementSchema` plus `ElementRuleContext`). An operand chip
    // inside a filter has to offer the same thing, or `parcels[code in priorityCodes]` names a
    // document field the picker cannot show.

    @Test
    fun `filter catalog offers the element's members and the document's fields`() {
        val documentFields = fields + CatalogFieldInfo(id = "priorityCodes", type = "string_set")
        val catalog = OperandRules.filterCatalog(fields = documentFields, path = path, depth = 0)

        assertEquals(
            expected = listOf("code", "damaged", "origin", "scans", "parcels", "priorityCodes"),
            actual = catalog.map { it.id },
            message = "element members first, then the document fields behind them",
        )
    }

    @Test
    fun `a member shadows a document field of the same name`() {
        val documentFields = fields + CatalogFieldInfo(id = "code", type = "integer")
        val catalog = OperandRules.filterCatalog(fields = documentFields, path = path, depth = 0)

        assertEquals(
            expected = 1,
            actual = catalog.count { it.id == "code" },
            message = "the shadowed document field must not appear twice",
        )
        assertEquals(
            expected = "text",
            actual = catalog.first { it.id == "code" }.type,
            message = "the element's 'code' wins, as it does at evaluation time",
        )
    }

    @Test
    fun `filter catalog is empty for an undeclared segment`() {
        val catalog = OperandRules.filterCatalog(
            fields = fields,
            path = listOf(BuilderPathStep(name = "unknown")),
            depth = 0,
        )

        assertTrue(actual = catalog.isEmpty())
    }

    @Test
    fun `filter catalog keeps collection members, unlike the flat dropdown`() {
        val catalog = OperandRules.filterCatalog(fields = fields, path = path, depth = 0)

        // A chip can aggregate over one — `parcels[count(scans) > 2]` — which is exactly the shape the
        // flat dropdown has to leave out and the whole reason this catalog is nested.
        assertTrue(actual = catalog.any { it.id == "scans" })
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


    // ── ignoreCase ────────────────────────────────────────────────────────────

    @Test
    fun `a text row offers ignoreCase`() {
        assertTrue(
            actual = OperandRules.supportsIgnoreCase(
                left = fieldOperand(name = "unknownTextField"),
                right = literal,
                fields = fields,
            )
        )
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

    /** `every(...) == true` compares two booleans; folding case there changes nothing. */
    @Test
    fun `a boolean row does not offer ignoreCase`() {
        val predicate = BuilderOperand.Call(function = "every", args = listOf(fieldOperand(name = "orders")))
        val booleanLiteral = BuilderOperand.Literal(text = "true", numeric = false)

        assertFalse(
            actual = OperandRules.supportsIgnoreCase(
                left = predicate,
                right = booleanLiteral,
                fields = fields,
            )
        )
    }

    // ── function operands ─────────────────────────────────────────────────────

    @Test
    fun `a numeric function is treated as numeric`() {
        val call = BuilderOperand.Call(
            function = "daysBetween",
            args = listOf(fieldOperand(name = "from"), fieldOperand(name = "to")),
        )

        assertEquals(expected = OperandRules.OperandKind.FUNCTION, actual = OperandRules.kindOf(operand = call))
        assertEquals(
            expected = OperatorOptions.COMPARISON_NUMERIC,
            actual = OperandRules.operatorsFor(
                left = call,
                right = BuilderOperand.Literal(text = "90", numeric = true),
                fields = fields,
            ),
        )
    }

    /**
     * A predicate answers true or false, so the other side is compared against `true` — offering
     * ordering operators there would produce a rule that can never match.
     */
    @Test
    fun `a boolean predicate does not make the other side numeric`() {
        val predicate = BuilderOperand.Call(function = "every", args = listOf(fieldOperand(name = "orders")))

        assertFalse(actual = OperandRules.canBeNumeric(operand = predicate, fields = fields))
        assertEquals(
            expected = OperatorOptions.COMPARISON_TEXT,
            actual = OperandRules.operatorsFor(left = predicate, right = literal, fields = fields),
        )
    }

    @Test
    fun `the function kind is offered beside the aggregate kind`() {
        val numeric = BuilderOperand.Literal(text = "10", numeric = true)

        val kinds = OperandRules.availableKinds(other = numeric, fields = fields)

        assertTrue(actual = kinds.contains(element = OperandRules.OperandKind.FUNCTION), message = "$kinds")
        assertTrue(actual = kinds.contains(element = OperandRules.OperandKind.AGGREGATE), message = "$kinds")
    }

    @Test
    fun `switching a side to a function wraps what was there`() {
        val previous = fieldOperand(name = "amount")

        val operand = OperandRules.defaultOperand(
            kind = OperandRules.OperandKind.FUNCTION,
            fields = fields,
            previous = previous,
        )

        val call = operand as BuilderOperand.Call
        assertEquals(expected = "abs", actual = call.function)
        assertEquals(expected = listOf(previous), actual = call.args)
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
