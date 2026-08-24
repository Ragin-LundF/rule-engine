package ruleengine.export.markdown

import ruleengine.dsl.parser.Parser
import ruleengine.export.RuleCatalogBuilder
import ruleengine.export.dto.ParsedRuleFile
import ruleengine.export.dto.RuleCatalog
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MarkdownCatalogRendererTest {

    private val manifestPath: Path = Path.of("src/test/resources/warehouse-shipments/manifest.yaml")

    private fun warehouse(): String {
        val catalog = RuleCatalogBuilder.fromManifest(manifestPath = manifestPath).single()

        return MarkdownCatalogRenderer.render(catalog = catalog)
    }

    /** Renders a catalog built from one inline rule file, for the cases the sample does not cover. */
    private fun renderRules(rules: String, projectName: String? = "p"): String {
        val catalog = RuleCatalogBuilder.build(
            projectName = projectName,
            entryId = "e",
            files = listOf(
                ParsedRuleFile(
                    relativePath = "r.rule",
                    rules = Parser(input = rules).parseRules(),
                )
            ),
        )

        return MarkdownCatalogRenderer.render(catalog = catalog)
    }

    private fun oneRule(condition: String, description: String? = null): String {
        val clause = description?.let { text -> "  description \"$text\"\n" }.orEmpty()

        return renderRules(
            rules = """
                |rule "r" {
                |$clause  when
                |    $condition
                |  then
                |    label "a"
                |}
            """.trimMargin()
        )
    }

    // ── document frame ────────────────────────────────────────────────────────

    @Test
    fun `opens with the project name and the entry facts`() {
        val markdown = warehouse()

        assertTrue(actual = markdown.startsWith(prefix = "# Rule overview — warehouse-shipments"))
        assertTrue(
            actual = markdown.contains(other = "_Entry `shipment-assessment` · 16 rules · 4 rule files"),
            message = markdown.lines().take(n = 5).joinToString(separator = "\n"),
        )
    }

    @Test
    fun `states that every matching rule contributes and that order is fixed`() {
        // Most rule engines a reader has met are first-match-wins; without the first sentence they will
        // assume the same here and misread every rule that follows. The second is what makes the `set`
        // and `stop` notes mean anything: they are claims about position, so the order has to be stated.
        val markdown = warehouse()

        assertTrue(
            actual = markdown.contains(other = "a later rule never overrides an earlier one"),
            message = markdown,
        )
        assertTrue(
            actual = markdown.contains(other = "in the order they are listed below"),
            message = markdown,
        )
        assertTrue(
            actual = markdown.contains(other = "rule-file order, then the order the rules appear inside each file"),
            message = markdown,
        )
    }

    @Test
    fun `singularises the counts`() {
        val markdown = oneRule(condition = "amount >= 1")

        assertTrue(actual = markdown.contains(other = "1 rule · 1 rule file"), message = markdown)
    }

    // ── index ─────────────────────────────────────────────────────────────────

    @Test
    fun `links every index row to the rule heading`() {
        val markdown = warehouse()

        assertTrue(
            actual = markdown.contains(
                other = "| [`premium-service-promise`](#premium-service-promise) |"
            ),
            message = markdown,
        )
        assertTrue(actual = markdown.contains(other = "### premium-service-promise"))
    }

    @Test
    fun `falls back to the condition when a rule has no description`() {
        val markdown = oneRule(condition = "amount >= 1")

        assertTrue(
            actual = markdown.contains(other = "| Amount is at least 1 |"),
            message = markdown,
        )
    }

    @Test
    fun `escapes a pipe so it cannot break the table`() {
        val markdown = oneRule(condition = "amount >= 1", description = "Either a | or b.")

        assertTrue(actual = markdown.contains(other = "Either a \\| or b."), message = markdown)
        val indexRow = markdown.lines().single { line -> line.startsWith(prefix = "| [`r`]") }
        val cellSeparators = Regex(pattern = "(?<!\\\\)\\|").findAll(input = indexRow).count()
        assertEquals(
            expected = 4,
            actual = cellSeparators,
            message = "An escaped pipe must not add a column:\n$indexRow",
        )
    }

    // ── outcomes ──────────────────────────────────────────────────────────────

    @Test
    fun `separates the action from its value in the outcome table`() {
        val markdown = warehouse()

        assertTrue(
            actual = markdown.contains(other = "| Output | Value | Produced by |"),
            message = markdown,
        )
        assertTrue(
            actual = markdown.contains(
                other = "| `assessment` | `service:premium` | " +
                    "[`premium-service-promise`](#premium-service-promise) |"
            ),
            message = markdown,
        )
    }

    // ── one rule ──────────────────────────────────────────────────────────────

    @Test
    fun `writes the description, the sentences and the dsl for a rule`() {
        val markdown = warehouse()
        val section = markdown.substringAfter(delimiter = "### premium-service-promise")
            .substringBefore(delimiter = "###")

        assertEquals(
            expected = """
                Gold-tier customers shipping on an express service get the premium service assessment.

                **Applies when all of the following are true:**

                - Customer › Tier is "gold"
                - Service contains "express"

                **Then:** `assessment service:premium`, `reason gold-customer-on-express-service`

                In the rule language: `shipment.customer.tier equals "gold" and shipment.service contains "express"`
            """.trimIndent(),
            actual = section.trim(),
        )
    }

    @Test
    fun `a single condition gets a plain lead-in and one bullet`() {
        val markdown = oneRule(condition = "amount >= 1")

        assertTrue(
            actual = markdown.contains(other = "**Applies when:**\n\n- Amount is at least 1\n"),
            message = markdown,
        )
    }

    @Test
    fun `an or reads as any of the following`() {
        val markdown = oneRule(condition = "amount >= 1\n    or amount <= 0")

        assertTrue(
            actual = markdown.contains(other = "**Applies when any of the following is true:**"),
            message = markdown,
        )
    }

    @Test
    fun `a nested group is indented under its own lead-in`() {
        val markdown = oneRule(condition = "(amount >= 1 or amount <= 0)\n    and purpose contains \"x\"")

        assertTrue(
            actual = markdown.contains(
                other = "- Any of the following is true:\n" +
                    "  - Amount is at least 1\n" +
                    "  - Amount is at most 0\n"
            ),
            message = markdown,
        )
    }

    @Test
    fun `a negation says so rather than inverting the sentence`() {
        // `not (a and b)` is not `not a and not b`, so the negation has to stay visible as its own line.
        val markdown = oneRule(condition = "not (amount >= 1 and purpose contains \"x\")")

        assertTrue(
            actual = markdown.contains(other = "**Applies when the following is not true:**"),
            message = markdown,
        )
    }

    // ── stability ─────────────────────────────────────────────────────────────

    @Test
    fun `rendering the same catalog twice produces identical text`() {
        // The wiki page is regenerated on every change; a document that differs run to run would
        // show an edit every time and make the real changes impossible to spot in the history.
        assertEquals(expected = warehouse(), actual = warehouse())
    }

    // ── else branch ───────────────────────────────────────────────────────────

    @Test
    fun `an else branch is rendered as an Otherwise section`() {
        val markdown = renderRules(rules = TIERED_RULE)

        assertTrue(actual = markdown.contains(other = "**Then:** `label high`"), message = markdown)
        assertTrue(actual = markdown.contains(other = "**Otherwise:** `label low`"), message = markdown)
        assertTrue(
            actual = markdown.contains(other = "**Publishes for later rules otherwise:** `tierLevel`"),
            message = markdown,
        )
    }

    @Test
    fun `the glance row shows the else outcome as an alternative, not a peer`() {
        val markdown = renderRules(rules = TIERED_RULE)

        assertTrue(
            actual = markdown.contains(other = "`label high`; otherwise `label low`"),
            message = markdown,
        )
    }

    @Test
    fun `an else outcome appears in the outcome summary`() {
        val markdown = renderRules(rules = TIERED_RULE)
        val outcomes = markdown.substringAfter(delimiter = "## Outcomes this rule set can produce")

        assertTrue(actual = outcomes.contains(other = "low"), message = outcomes)
    }

    @Test
    fun `the branching note is only written for a rule set that has one`() {
        assertTrue(actual = renderRules(rules = TIERED_RULE).contains(other = "do *not* match"))
        assertFalse(actual = oneRule(condition = "weightKg >= 1").contains(other = "do *not* match"))
    }

    @Test
    fun `a rule without an else branch gets no Otherwise section`() {
        assertFalse(actual = oneRule(condition = "weightKg >= 1").contains(other = "**Otherwise:**"))
    }

    // ── not_exists branch ─────────────────────────────────────────────────────

    @Test
    fun `a not_exists branch is rendered as its own prose section`() {
        val markdown = renderRules(rules = THREE_BRANCH_RULE)

        assertTrue(actual = markdown.contains(other = "**Then:** `label high`"), message = markdown)
        assertTrue(actual = markdown.contains(other = "**Otherwise:** `label low`"), message = markdown)
        assertTrue(
            actual = markdown.contains(other = "**When the data is missing:** `label unknown`"),
            message = markdown,
        )
    }

    @Test
    fun `the missing-data note is only written for a rule set that has such a branch`() {
        assertTrue(
            actual = renderRules(rules = THREE_BRANCH_RULE).contains(other = "does not carry the data"),
            message = "expected the caveat for a rule set that uses the branch",
        )
        assertFalse(actual = renderRules(rules = TIERED_RULE).contains(other = "does not carry the data"))
    }

    @Test
    fun `a rule without a not_exists branch gets no missing-data section`() {
        assertFalse(actual = renderRules(rules = TIERED_RULE).contains(other = "**When the data is missing:**"))
    }

    @Test
    fun `a not_exists outcome appears in the outcome summary`() {
        val outcomes = renderRules(rules = THREE_BRANCH_RULE)
            .substringAfter(delimiter = "## Outcomes this rule set can produce")

        assertTrue(actual = outcomes.contains(other = "unknown"), message = outcomes)
    }

    @Test
    fun `an entry with no rules says so instead of rendering an empty table`() {
        val catalog = RuleCatalog(
            projectName = "empty",
            entryId = "e",
            schemaPath = null,
            files = emptyList(),
        )
        val markdown = MarkdownCatalogRenderer.render(catalog = catalog)

        assertTrue(actual = markdown.contains(other = "No rules are defined in this entry."))
        assertFalse(actual = markdown.contains(other = "| Rule | What it does |"))
    }

    private companion object {
        val THREE_BRANCH_RULE: String = """
            rule "tier" {
              description "A heavy shipment is high tier, a light one low, an unweighed one neither."
              when
                weightKg >= 1000
              then
                label "high"
              else
                label "low"
              not_exists
                label "unknown"
            }
        """.trimIndent()

        val TIERED_RULE: String = """
            rule "tier" {
              description "A heavy shipment is high tier, anything else is low tier."
              when
                weightKg >= 1000
              then
                label "high"
              else
                label "low"
                set tierLevel = 1
            }
        """.trimIndent()
    }
}
