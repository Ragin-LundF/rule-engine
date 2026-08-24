package ruleengine.core.analysis

import ruleengine.core.io.FileInputSupport
import ruleengine.dsl.ast.RuleAst
import ruleengine.dsl.parser.Parser
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `FieldUsage` walks a parsed rule and reports the schema paths it reads.
 *
 * The fixture is the real `warehouse-shipments` bundle rather than an inlined copy: it is the widest
 * rule set in the suite and already exercises every AST shape this walker has to handle — a nested
 * dotted path, an `and` of two conditions, an aggregate over a filtered collection, arithmetic on
 * both sides of a comparison, and a filter that reads a nested member of the element it filters.
 */
class FieldUsageTest {

    private val rulesDir: Path = Path.of("src/test/resources/warehouse-shipments/rules")

    private val rules: List<RuleAst> = FileInputSupport.walkRuleFiles(root = rulesDir)
        .flatMap { path ->
            Parser(input = FileInputSupport.readBoundedText(path = path, kind = "rule")).parseRules()
        }

    private fun ruleNamed(id: String): RuleAst {
        return rules.single { rule -> rule.id == id }
    }

    @Test
    fun `an and of two plain conditions reports both nested paths`() {
        assertEquals(
            expected = setOf("shipment.customer.tier", "shipment.service"),
            actual = FieldUsage.fieldsOf(rule = ruleNamed(id = "premium-service-promise")),
        )
    }

    @Test
    fun `a single condition reports its path`() {
        assertEquals(
            expected = setOf("shipment.transitDays"),
            actual = FieldUsage.fieldsOf(rule = ruleNamed(id = "transit-within-promise")),
        )
    }

    /**
     * `count(parcels[damaged == true]) == 0` — the filter predicate is written relative to a parcel,
     * so the dependency is on `parcels.damaged`. The bare collection path is reported too; the field
     * flow view drops it because it is not a schema leaf.
     */
    @Test
    fun `a filter predicate resolves against the collection it filters`() {
        assertEquals(
            expected = setOf("parcels", "parcels.damaged"),
            actual = FieldUsage.fieldsOf(rule = ruleNamed(id = "all-parcels-intact")),
        )
    }

    /**
     * The case that makes the recursion worth having: `parcels[origin.hub == "HAM"]` reads a nested
     * member of the filtered element, which naive extraction would report as the top-level
     * `origin.hub` and so never match anything in the schema.
     */
    @Test
    fun `a filter reading a nested member of its element is prefixed with the collection`() {
        val fields = FieldUsage.fieldsOf(rule = ruleNamed(id = "consolidate-at-hamburg-hub"))

        assertTrue(
            actual = "parcels.origin.hub" in fields,
            message = "Expected the filter path to resolve against 'parcels', got: $fields",
        )
        assertTrue(actual = "origin.hub" !in fields, message = "The unprefixed path must not leak: $fields")
    }

    @Test
    fun `both operands of a comparison and both sides of arithmetic are walked`() {
        // `$fragileWeightKg > $totalWeightKg * 0.25` — both sides are variables, which are produced
        // by another rule rather than read from the input, so this rule reads no field at all.
        assertEquals(
            expected = emptySet(),
            actual = FieldUsage.fieldsOf(rule = ruleNamed(id = "fragile-load")),
        )
    }

    /**
     * A `set` expression reads fields the same way a condition does. Without walking assignments the
     * two weight paths would look unreferenced, because the only rules that touch them now read them
     * through `$totalWeightKg` and `$fragileWeightKg`.
     */
    @Test
    fun `fields read by a set expression are reported`() {
        assertEquals(
            expected = setOf("parcels", "parcels.category", "parcels.weightKg"),
            actual = FieldUsage.fieldsOf(rule = ruleNamed(id = "shipment-totals")),
        )
    }

    @Test
    fun `a filter over a second collection is prefixed with that collection`() {
        assertEquals(
            expected = setOf("checkpoints", "checkpoints.scanned"),
            actual = FieldUsage.fieldsOf(rule = ruleNamed(id = "tracking-gap")),
        )
    }

    /**
     * Restricting the referenced paths to the schema's leaves is what the field flow view does to find
     * dead schema surface. Across the whole bundle three declared leaves are read by no rule at all.
     */
    @Test
    fun `intersecting with schema leaves leaves the unreferenced fields behind`() {
        val schemaLeaves = setOf(
            "shipment.reference",
            "shipment.service",
            "shipment.transitDays",
            "shipment.declaredValue",
            "shipment.pickedUpAt",
            "shipment.customer.tier",
            "shipment.customer.country",
            "parcels.code",
            "parcels.weightKg",
            "parcels.category",
            "parcels.damaged",
            "parcels.origin.hub",
            "checkpoints.site",
            "checkpoints.delayMinutes",
            "checkpoints.scanned",
        )
        val referenced = rules.flatMap { rule -> FieldUsage.fieldsOf(rule = rule) }.toSet()

        assertEquals(
            expected = setOf("shipment.reference", "parcels.code", "checkpoints.site"),
            actual = schemaLeaves - referenced,
        )
    }

    @Test
    fun `the fixture bundle parses to the full rule set`() {
        assertEquals(expected = 16, actual = rules.size)
    }

    /**
     * An ordering names a member, and reading it is a dependency like any other — a rule that only
     * ever mentions `weightKg` inside a `sortBy` still stops working when that field disappears.
     */
    @Test
    fun `an ordering reports the member it orders by`() {
        // The bare collection comes along the way every other intermediate path does — see the
        // class comment. What matters here is that the member is not lost.
        assertEquals(
            expected = setOf("parcels", "parcels.weightKg"),
            actual = FieldUsage.fieldsOf(
                rule = inlineRule(condition = """count(sortBy(parcels, "weightKg", desc)) > 0"""),
            ),
        )
    }

    @Test
    fun `an ordering over values reports only the collection`() {
        assertEquals(
            expected = setOf("topics"),
            actual = FieldUsage.fieldsOf(rule = inlineRule(condition = "count(sortBy(topics, asc)) > 0")),
        )
    }

    private fun inlineRule(condition: String): RuleAst {
        return Parser(
            input = """
                rule "inline" {
                  description "d"
                  when
                    $condition
                  then
                    flag "ok"
                }
            """.trimIndent()
        ).parseRules().single()
    }
}
