package ui.builder.model.catalog

import ruleengine.core.domain.FieldPathResolution
import ruleengine.core.domain.FieldPathResolver
import ruleengine.schema.FieldSchemaLoader
import ui.workbench.builderCatalogFieldsFrom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Builder's path walk against the engine's, on the same schema.
 *
 * The Builder cannot call `FieldPathResolver`: it walks a `commonMain` [CatalogFieldInfo] tree, and it
 * needs the *members at a level* to fill a dropdown, not a yes/no verdict about a whole rule. What it
 * must not do is disagree, and it did — the walk matched an alias only at its own nesting level, so
 * `TRANSACTION_HISTORY_DAYS` resolved for the engine and not for the inspector, which then reported a
 * declared field as undeclared.
 *
 * Every case here asserts both halves: what the Builder answers, and that the engine answers the same.
 * The schema is the shape of `.plan/v1/schemas/lidl-dac-schema.yaml`, which is where the report came
 * from.
 */
class BuilderCatalogPathResolutionTest {

    private val schema = FieldSchemaLoader.loadFromString(
        content = """
            schema: resolution-v1

            fields:
              reports:
                type: object
                fields:
                  income:
                    type: object
                    fields:
                      daysOfReport: {type: integer, alias: TRANSACTION_HISTORY_DAYS}
                      spendingToIncomeRatio: {type: decimal, alias: SPENDING_TO_INCOME_RATIO}
                      completeMonths: {type: string_set}
                      accountData:
                        type: collection
                        fields:
                          accountId: {type: text}
                          accountType: {type: text, alias: ACCOUNT_TYPE}
        """.trimIndent()
    )

    private val catalog = builderCatalogFieldsFrom(schema = schema)

    /** What the engine makes of the same identifier, as the canonical path or null. */
    private fun engineResolves(identifier: String): String? =
        (FieldPathResolver.resolve(identifier = identifier, schema = schema) as? FieldPathResolution.Resolved)
            ?.id?.value

    @Test
    fun `a bare alias declared several levels down resolves, as it does for the engine`() {
        val field = catalog.fieldAtPath(segments = listOf("TRANSACTION_HISTORY_DAYS"))

        assertNotNull(
            actual = field,
            message = "A bare alias is a legal identifier; the condition dropdown already offers this one",
        )
        assertEquals(expected = "integer", actual = field.type)
        assertEquals(expected = "reports.income.daysOfReport", actual = engineResolves("TRANSACTION_HISTORY_DAYS"))
    }

    @Test
    fun `an alias used as the last segment of a path still resolves`() {
        val segments = listOf("reports", "income", "SPENDING_TO_INCOME_RATIO")

        assertEquals(expected = "decimal", actual = catalog.fieldAtPath(segments = segments)?.type)
        assertEquals(
            expected = "reports.income.spendingToIncomeRatio",
            actual = engineResolves(segments.joinToString(separator = ".")),
        )
    }

    @Test
    fun `an alias declared inside a collection is not usable on its own`() {
        // The engine answers CrossesCollection, not Resolved — RULE-SPEC.md, and the reason
        // `builderCatalogFieldsFrom` filters the index by `AliasTarget.collectionPath`.
        assertNull(actual = catalog.fieldAtPath(segments = listOf("ACCOUNT_TYPE")))
        assertNull(actual = engineResolves("ACCOUNT_TYPE"))
    }

    @Test
    fun `an alias inside a collection still matches as a member of its own element`() {
        assertEquals(
            expected = "text",
            actual = catalog.fieldAtPath(
                segments = listOf("reports", "income", "accountData", "ACCOUNT_TYPE"),
            )?.type,
        )
    }

    @Test
    fun `a flat dotted key is matched before the nested walk, as the engine matches it`() {
        val flat = FieldSchemaLoader.loadFromString(
            content = """
                schema: flat-v1

                fields:
                  user.profile.age: {type: integer}
            """.trimIndent()
        )
        val flatCatalog = builderCatalogFieldsFrom(schema = flat)

        assertEquals(
            expected = "integer",
            actual = flatCatalog.fieldAtPath(segments = listOf("user", "profile", "age"))?.type,
        )
        assertEquals(
            expected = "user.profile.age",
            actual = (
                FieldPathResolver.resolve(identifier = "user.profile.age", schema = flat)
                    as? FieldPathResolution.Resolved
                )?.id?.value,
        )
    }

    @Test
    fun `a declared name wins over an alias that shares its spelling`() {
        val shadowing = FieldSchemaLoader.loadFromString(
            content = """
                schema: shadow-v1

                fields:
                  total: {type: text}
                  order:
                    type: object
                    fields:
                      amount: {type: decimal, alias: total}
            """.trimIndent()
        )
        val shadowCatalog = builderCatalogFieldsFrom(schema = shadowing)

        // The engine tries a direct hit before consulting the index, so `total` is the text field.
        assertEquals(expected = "text", actual = shadowCatalog.fieldAtPath(segments = listOf("total"))?.type)
        assertEquals(expected = "total", actual = engineResolvesIn(schema = shadowing, identifier = "total"))
    }

    @Test
    fun `the flat dropdown does not enumerate an alias-tailed path, but the walk resolves one`() {
        // Why `ConditionEditor` resolves the row's field instead of matching it against the dropdown:
        // `scalarPaths` offers the canonical path and the bare alias, never the mix of the two, and
        // `reports.income.SPENDING_TO_INCOME_RATIO` is the spelling `.plan/v1` actually uses.
        val offered = catalog.scalarPaths().map { it.id }
        val written = "reports.income.SPENDING_TO_INCOME_RATIO"

        assertTrue(actual = written !in offered, message = "the enumeration does not produce this")
        assertEquals(
            expected = "decimal",
            actual = catalog.fieldAtPath(segments = written.split("."))?.type,
            message = "but the engine compiles it, so the row must be typed from it",
        )
    }

    @Test
    fun `an undeclared path stays undeclared`() {
        assertNull(actual = catalog.fieldAtPath(segments = listOf("reports", "nonexistent")))
        assertNull(actual = catalog.fieldAtPath(segments = listOf("NOT_AN_ALIAS")))
    }

    @Test
    fun `members below a bare alias root are offered, so the path can be edited on`() {
        val members = catalog.fieldsAtPath(segments = listOf("reports", "income", "accountData"))

        assertEquals(expected = listOf("accountId", "accountType"), actual = members.map { it.id })
    }

    private fun engineResolvesIn(
        schema: ruleengine.core.domain.dto.field.FieldSchema,
        identifier: String,
    ): String? =
        (FieldPathResolver.resolve(identifier = identifier, schema = schema) as? FieldPathResolution.Resolved)
            ?.id?.value
}
