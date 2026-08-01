package ruleengine.export

import ruleengine.core.errors.RuleEngineBuildException
import ruleengine.export.dto.CatalogOutcome
import ruleengine.export.dto.CatalogRule
import ruleengine.export.dto.ParsedRuleFile
import ruleengine.export.dto.PlainAll
import ruleengine.export.dto.PlainLeaf
import ruleengine.export.dto.RuleCatalog
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Builds a catalog from the `warehouse-shipments` sample the documentation points at, so the export
 * is exercised against a real rule set rather than a fixture shaped to suit it.
 */
class RuleCatalogBuilderTest {

    private val manifestPath: Path = Path.of("src/test/resources/warehouse-shipments/manifest.yaml")

    private fun catalog(): RuleCatalog {
        return RuleCatalogBuilder.fromManifest(manifestPath = manifestPath).single()
    }

    private fun rule(id: String): CatalogRule {
        return catalog().rules.single { rule -> rule.id == id }
    }

    @Test
    fun `carries the manifest metadata into the catalog`() {
        val catalog = catalog()

        assertEquals(expected = "warehouse-shipments", actual = catalog.projectName)
        assertEquals(expected = "shipment-assessment", actual = catalog.entryId)
        assertEquals(expected = "schema.yaml", actual = catalog.schemaPath)
    }

    @Test
    fun `keeps the rule files separate and in manifest order`() {
        assertEquals(
            expected = listOf(
                "rules/delivery-quality.rule",
                "rules/parcel-condition.rule",
                "rules/route-risk.rule",
            ),
            actual = catalog().files.map { file -> file.relativePath },
        )
    }

    @Test
    fun `lists rules in evaluation order across files`() {
        // File order first, then declaration order within each file — the order the engine runs them
        // in, so a reader following the document follows the engine.
        assertEquals(
            expected = listOf(
                "premium-service-promise",
                "transit-within-promise",
                "transit-over-promise",
                "insurance-required",
                "pickup-inside-planning-window",
                "all-parcels-intact",
                "damaged-parcels-reported",
                "two-person-lift",
                "fragile-load",
                "consolidate-at-hamburg-hub",
                "route-on-time",
                "route-delayed",
                "tracking-gap",
            ),
            actual = catalog().rules.map { rule -> rule.id },
        )
    }

    @Test
    fun `carries the description clause`() {
        assertEquals(
            expected = "A shipment delivered in two days or less meets the transit promise.",
            actual = rule(id = "transit-within-promise").description,
        )
    }

    @Test
    fun `renders the condition both ways`() {
        val rule = rule(id = "premium-service-promise")

        assertEquals(
            expected = "shipment.customer.tier equals \"gold\" and shipment.service contains \"express\"",
            actual = rule.technicalCondition,
        )

        val condition = rule.condition
        assertIs<PlainAll>(value = condition)
        assertEquals(
            expected = listOf(
                PlainLeaf(text = "Customer › Tier is \"gold\""),
                PlainLeaf(text = "Service contains \"express\""),
            ),
            actual = condition.children,
        )
    }

    @Test
    fun `renders a between condition as valid dsl`() {
        // `1000..25000` would not parse; the technical line is meant to be copy-pasteable.
        assertTrue(
            actual = rule(id = "insurance-required").technicalCondition
                .contains(other = "between 1000 25000"),
            message = rule(id = "insurance-required").technicalCondition,
        )
    }

    @Test
    fun `records the outcomes with unquoted arguments`() {
        assertEquals(
            expected = listOf(
                CatalogOutcome(action = "assessment", argument = "service:premium"),
                CatalogOutcome(action = "reason", argument = "gold-customer-on-express-service"),
            ),
            actual = rule(id = "premium-service-promise").outcomes,
        )
    }

    @Test
    fun `groups rules by the outcome they produce`() {
        val byOutcome = catalog().rulesByOutcome()

        assertEquals(
            expected = listOf("transit-within-promise"),
            actual = byOutcome
                .getValue(key = CatalogOutcome(action = "assessment", argument = "transit:green"))
                .map { rule -> rule.id },
        )
        assertNotNull(
            actual = byOutcome[CatalogOutcome(action = "assessment", argument = "condition:red")]
        )
    }

    @Test
    fun `an outcome is keyed by its action as well as its value`() {
        // Otherwise `service:premium` and the reason code explaining it appear as peer decisions.
        val byOutcome = catalog().rulesByOutcome()
        val actions = byOutcome.keys.map { outcome -> outcome.action }

        assertEquals(expected = setOf("assessment", "reason"), actual = actions.toSet())
        assertEquals(
            expected = actions.sorted(),
            actual = actions,
            message = "Outcomes must be grouped by action so one kind of output stays together",
        )
    }

    @Test
    fun `treats a blank description as absent`() {
        val catalog = RuleCatalogBuilder.build(
            projectName = "p",
            entryId = "e",
            files = listOf(
                ParsedRuleFile(
                    relativePath = "r.rule",
                    rules = ruleengine.dsl.parser.Parser(
                        input = """
                            rule "blank" {
                              description "   "
                              when
                                amount >= 1
                              then
                                label "a"
                            }
                        """.trimIndent()
                    ).parseRules(),
                )
            ),
        )

        assertNull(actual = catalog.rules.single().description)
    }

    @Test
    fun `builds only the requested entry`() {
        val catalogs = RuleCatalogBuilder.fromManifest(
            manifestPath = manifestPath,
            entryId = "shipment-assessment",
        )

        assertEquals(expected = 1, actual = catalogs.size)
    }

    @Test
    fun `rejects an unknown entry id naming the ones that exist`() {
        val failure = assertFailsWith<RuleEngineBuildException> {
            RuleCatalogBuilder.fromManifest(manifestPath = manifestPath, entryId = "nope")
        }

        assertTrue(
            actual = failure.message.orEmpty().contains(other = "shipment-assessment"),
            message = "Expected the known entries in the message, got: ${failure.message}",
        )
    }
}
