package ui.builder

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The paths a plain condition row may name.
 *
 * A schema that describes its input with nested `fields:` blocks exposes a single top-level object, so a
 * condition on `reports.income.daysOfReport` used to resolve to nothing and was marked unknown even
 * though the engine accepted it. These assertions pin the two halves of that: a nested scalar must be
 * reachable by its dotted path, and anything below a collection must not be, because the engine rejects
 * a path that crosses one.
 */
class CatalogScalarPathsTest {

    private val reports = CatalogFieldInfo(
        id = "reports",
        type = "object",
        nestedFields = listOf(
            CatalogFieldInfo(
                id = "income",
                type = "object",
                nestedFields = listOf(
                    CatalogFieldInfo(id = "daysOfReport", type = "integer"),
                    CatalogFieldInfo(id = "spendingToIncomeRatio", type = "decimal"),
                    CatalogFieldInfo(
                        id = "accountData",
                        type = "collection",
                        nestedFields = listOf(CatalogFieldInfo(id = "accountType", type = "text")),
                    ),
                ),
            ),
            CatalogFieldInfo(
                id = "spending",
                type = "object",
                nestedFields = listOf(CatalogFieldInfo(id = "countSpendingTransactions", type = "integer")),
            ),
        ),
    )

    private val catalog = listOf(reports)

    @Test
    fun `nested scalars are offered by their dotted path`() {
        assertEquals(
            expected = listOf(
                "reports.income.daysOfReport",
                "reports.income.spendingToIncomeRatio",
                "reports.spending.countSpendingTransactions",
            ),
            actual = catalog.scalarPaths().map { it.id },
        )
    }

    @Test
    fun `a nested scalar keeps its declared type, so the row can offer numeric operators`() {
        val ratio = catalog.scalarPaths().single { it.id == "reports.income.spendingToIncomeRatio" }

        assertEquals(expected = "decimal", actual = ratio.type)
        assertTrue(
            actual = "<" in OperatorOptions.forField(fieldType = ratio.type, schemaOperators = ratio.operators),
            message = "a decimal must offer '<', which is what the rule DSL uses",
        )
    }

    @Test
    fun `members below a collection are not offered, because the engine rejects such a path`() {
        assertFalse(actual = catalog.scalarPaths().any { it.id.contains(other = "accountData") })
    }

    @Test
    fun `the structures themselves are not offered, having no operators to compare with`() {
        assertFalse(actual = catalog.scalarPaths().any { OperatorOptions.isStructureType(fieldType = it.type) })
    }

    @Test
    fun `a flat schema passes through unchanged`() {
        val flat = listOf(
            CatalogFieldInfo(id = "amount", type = "decimal"),
            CatalogFieldInfo(id = "user.profile.age", type = "integer"),
        )

        assertEquals(expected = flat, actual = flat.scalarPaths())
    }

    @Test
    fun `an object without declared members contributes nothing`() {
        val empty = listOf(CatalogFieldInfo(id = "payload", type = "object"))

        assertEquals(expected = emptyList(), actual = empty.scalarPaths())
    }
}
