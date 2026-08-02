package ruleengine.compiler

import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.core.errors.Severity
import ruleengine.dsl.parser.Parser
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers the N-segment path walk in [ValueExpressionValidator]: declared nested members are typed
 * from their leaf, filter fields resolve against the element they filter, and schemas that declare
 * no nested members keep the permissive behaviour they had before nesting existed.
 */
class NestedPathValidationTest {

    private fun field(name: String, type: FieldType, nested: List<FieldDefinition> = emptyList()) =
        FieldDefinition(
            id = FieldId(value = name),
            type = type,
            fields = nested.associateBy { it.id }
        )

    /** orders: collection { status: text, customer: object { country: text }, items: collection { price, sku } } */
    private val nestedSchema = FieldSchema(
        name = "nested-schema",
        fields = listOf(
            field(
                name = "orders",
                type = FieldType.COLLECTION,
                nested = listOf(
                    field(name = "status", type = FieldType.TEXT),
                    field(
                        name = "customer",
                        type = FieldType.OBJECT,
                        nested = listOf(field(name = "country", type = FieldType.TEXT))
                    ),
                    field(
                        name = "items",
                        type = FieldType.COLLECTION,
                        nested = listOf(
                            field(name = "price", type = FieldType.DECIMAL),
                            field(name = "sku", type = FieldType.TEXT)
                        )
                    )
                )
            )
        ).associateBy { it.id }
    )

    /** The pre-nesting shape: a collection declared as STRING_SET with no members. */
    private val legacySchema = FieldSchema(
        name = "legacy-schema",
        fields = listOf(field(name = "transactions", type = FieldType.STRING_SET)).associateBy { it.id }
    )

    private fun validate(condition: String, schema: FieldSchema = nestedSchema): ValidationResult {
        val rule = """
            rule "test" {
              when
                $condition
              then
                flag "ok"
            }
        """.trimIndent()
        return Validator.validate(asts = Parser(input = rule).parseRules(), schema = schema)
    }

    private fun assertNoErrors(result: ValidationResult, label: String) {
        val errors = result.diagnostics.filter { it.severity == Severity.ERROR }
        assertTrue(
            actual = errors.isEmpty(),
            message = "$label should not produce errors, got: ${errors.map { it.message }}"
        )
    }

    // --- depth ---

    @Test
    fun `three level projection validates`() {
        assertNoErrors(
            result = validate(condition = "sum(orders.items.price) > 100"),
            label = "sum(orders.items.price)"
        )
    }

    @Test
    fun `filters at two levels validate against their own element fields`() {
        assertNoErrors(
            result = validate(
                condition = """sum(orders[status == "paid"].items[price > 0].price) > 100"""
            ),
            label = "filters at two depths"
        )
    }

    @Test
    fun `object nested inside a collection validates`() {
        assertNoErrors(
            result = validate(condition = """orders.customer.country == "DE""""),
            label = "orders.customer.country"
        )
    }

    // --- unknown members ---

    @Test
    fun `unknown member deep in the path produces error naming it`() {
        val result = validate(condition = "sum(orders.items.bogus) > 1")
        assertFalse(actual = result.isValid)
        val error = result.diagnostics.first { it.severity == Severity.ERROR }
        assertTrue(
            actual = error.message.contains("bogus"),
            message = "Expected member name in message, got: ${error.message}"
        )
    }

    @Test
    fun `unknown field inside a filter produces error`() {
        val result = validate(condition = """count(orders[bogus == "x"]) > 0""")
        assertFalse(actual = result.isValid)
        assertTrue(
            actual = result.diagnostics.any { it.severity == Severity.ERROR && it.message.contains("bogus") },
            message = "Expected an error naming 'bogus', got: ${result.diagnostics.map { it.message }}"
        )
    }

    // --- legacy filter predicates ---
    //
    // Everything but `==`, `!=` and a field-against-field comparison parses as a ConditionAst inside
    // `[...]`, which went entirely unchecked. The modern form of the same mistake was always an error.

    @Test
    fun `unknown field in a legacy filter predicate produces error`() {
        val result = validate(condition = "count(orders[bogus > 1]) > 0")
        assertFalse(
            actual = result.isValid,
            message = "A legacy filter predicate naming an undeclared member must be reported"
        )
        val error = result.diagnostics.first { it.severity == Severity.ERROR }
        assertTrue(
            actual = error.message.contains("bogus"),
            message = "Expected an error naming 'bogus', got: ${error.message}"
        )
    }

    @Test
    fun `both halves of an and predicate inside a filter are checked`() {
        val result = validate(condition = """count(orders[legacyBogus > 1 and modernBogus == "x"]) > 0""")
        val messages = result.diagnostics.filter { it.severity == Severity.ERROR }.map { it.message }
        assertTrue(
            actual = messages.any { it.contains("legacyBogus") },
            message = "Expected the legacy half to be checked, got: $messages"
        )
        assertTrue(
            actual = messages.any { it.contains("modernBogus") },
            message = "Expected the modern half to be checked, got: $messages"
        )
    }

    /** A member reached through a nested object is a path, not a member whose name contains a dot. */
    @Test
    fun `dotted field in a legacy filter predicate validates`() {
        assertNoErrors(
            result = validate(condition = """count(orders[customer.country contains "D"]) > 0"""),
            label = "legacy predicate naming a nested member"
        )
    }

    @Test
    fun `unknown nested member in a legacy filter predicate produces error`() {
        val result = validate(condition = "count(orders[customer.bogus > 1]) > 0")
        assertFalse(actual = result.isValid)
        val error = result.diagnostics.first { it.severity == Severity.ERROR }
        assertTrue(
            actual = error.message.contains("customer.bogus"),
            message = "Expected the message to name the dotted field, got: ${error.message}"
        )
    }

    /** A predicate that reads into a collection projects many values where one is compared. */
    @Test
    fun `legacy filter predicate crossing a collection produces error`() {
        val result = validate(condition = "count(orders[items.price > 1]) > 0")
        assertFalse(actual = result.isValid)
        val error = result.diagnostics.first { it.severity == Severity.ERROR }
        assertTrue(
            actual = error.message.contains("items"),
            message = "Expected the message to name the collection, got: ${error.message}"
        )
    }

    @Test
    fun `operator without a filter equivalent is reported instead of thrown`() {
        val result = validate(condition = """count(orders[status startsWith "pa"]) > 0""")
        assertFalse(actual = result.isValid)
        assertTrue(
            actual = result.diagnostics.any {
                it.severity == Severity.ERROR && it.message.contains("not supported in filter segments")
            },
            message = "Expected the unsupported-operator message, got: ${result.diagnostics.map { it.message }}"
        )
    }

    @Test
    fun `ignoreCase inside a filter is reported instead of thrown`() {
        val result = validate(condition = """count(orders[status in ["paid"] ignoreCase]) > 0""")
        assertFalse(actual = result.isValid)
        val error = result.diagnostics.first { it.severity == Severity.ERROR }
        assertTrue(
            actual = error.message.contains("'ignoreCase' modifier is not supported in filter segments"),
            message = "Expected the ignoreCase message, got: ${error.message}"
        )
    }

    @Test
    fun `declared member in a legacy filter predicate still validates`() {
        assertNoErrors(
            result = validate(condition = "count(orders[items > 0]) > 0"),
            label = "legacy predicate naming a declared member"
        )
    }

    // --- leaf typing ---

    @Test
    fun `text leaf of a nested path is typed as text`() {
        assertNoErrors(
            result = validate(condition = """orders.status == "paid""""),
            label = "text equality on a nested leaf"
        )

        // Ordering comparisons are text-illegal, which is only detectable once the leaf is typed. The path
        // reads through a collection, which a plain condition cannot do either — assert the message so this
        // stays a real check instead of passing on whichever error happens to come first.
        val result = validate(condition = """orders.status > "paid"""")
        assertFalse(
            actual = result.isValid,
            message = "Ordering comparison on a nested text leaf should be rejected"
        )
        val error = result.diagnostics.first { it.severity == Severity.ERROR }
        assertTrue(
            actual = error.message.contains("collection 'orders'"),
            message = "Expected the message to name the collection, got: ${error.message}"
        )
    }

    // --- structures are not comparable directly ---

    @Test
    fun `legacy condition on a collection field is rejected with a helpful message`() {
        val result = validate(condition = """orders equals "x"""")
        assertFalse(actual = result.isValid)
        val error = result.diagnostics.first { it.severity == Severity.ERROR }
        assertTrue(
            actual = error.message.contains("collection"),
            message = "Expected the message to name the type, got: ${error.message}"
        )
    }

    // --- backward compatibility ---

    @Test
    fun `undeclared collection members stay permissive`() {
        assertNoErrors(
            result = validate(
                condition = """sum(transactions[label == "risk"].amount) > 100""",
                schema = legacySchema
            ),
            label = "filtered path on a schema without declared members"
        )
    }

    @Test
    fun `undeclared root in a multi segment path warns instead of failing`() {
        val result = validate(condition = "sum(unknownRoot.amount) > 1", schema = legacySchema)
        assertTrue(
            actual = result.diagnostics.any {
                it.severity == Severity.WARNING && it.message.contains("unknownRoot")
            },
            message = "Expected a warning naming the root, got: ${result.diagnostics.map { it.message }}"
        )
        assertNoErrors(result = result, label = "undeclared multi-segment root")
    }

    @Test
    fun `undeclared root in a single segment path is still an error`() {
        val result = validate(condition = "unknownField > 1", schema = legacySchema)
        assertFalse(actual = result.isValid)
    }
}
