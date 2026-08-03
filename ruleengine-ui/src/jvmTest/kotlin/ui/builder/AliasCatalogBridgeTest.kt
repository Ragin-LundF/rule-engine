package ui.builder

import ruleengine.schema.FieldSchemaLoader
import ui.builder.model.catalog.scalarPaths
import ui.workbench.builderCatalogFieldsFrom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The engine → Builder bridge for `alias:`.
 *
 * `FieldDefinition.toCatalogFieldInfo` is the only crossing point into the Builder's platform-neutral
 * model, and it used to drop `alias`. Everything downstream then failed by exact match: an alias-authored
 * condition row found no field, fell back to `type = "text"` and was marked unknown. These assertions pin
 * the chain a condition row actually walks — `builderCatalogFieldsFrom` then `scalarPaths()` then the
 * `id == condition.field` lookup of `ConditionRowEditor`.
 */
class AliasCatalogBridgeTest {

    private val schema = FieldSchemaLoader.loadFromString(
        content = """
            schema: bridge-v1

            fields:
              reports:
                type: object
                fields:
                  income:
                    type: object
                    fields:
                      daysOfReport: {type: integer, alias: TRANSACTION_HISTORY_DAYS}
                      accountData:
                        type: collection
                        fields:
                          accountType: {type: text, alias: ACCOUNT_TYPE}
        """.trimIndent()
    )

    private val options = builderCatalogFieldsFrom(schema = schema).scalarPaths()

    @Test
    fun `a nested alias is offered as its own option alongside the dotted path`() {
        assertEquals(
            expected = listOf("reports.income.daysOfReport", "TRANSACTION_HISTORY_DAYS"),
            actual = options.map { it.id },
        )
    }

    @Test
    fun `an alias-authored row resolves to the declared type rather than the text fallback`() {
        val row = options.firstOrNull { it.id == "TRANSACTION_HISTORY_DAYS" }
        assertNotNull(actual = row, message = "The dropdown must offer the alias the rule was written with")
        assertEquals(
            expected = "integer",
            actual = row.type,
            message = "A 'text' type here is the unresolved-field fallback, which offers the wrong operators",
        )
    }

    @Test
    fun `an alias below a collection is not offered, because a plain condition cannot read it`() {
        assertEquals(expected = emptyList(), actual = options.filter { it.id == "ACCOUNT_TYPE" })
    }
}
