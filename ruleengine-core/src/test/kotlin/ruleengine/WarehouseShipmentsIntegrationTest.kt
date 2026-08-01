package ruleengine

import ruleengine.builder.RuleEngineBuilder
import ruleengine.core.domain.dto.EvaluationResult
import ruleengine.jackson.JacksonUtil
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The complex worked example referenced from the documentation: a shipment record with a nested customer,
 * a list of parcels and a list of scan checkpoints.
 *
 * It is deliberately the widest bundle in the suite — plain conditions on nested object paths (including
 * normalizers, `between` and a formatted date), aggregates and filters over two collections, a filter that
 * reads a nested member of its element, and an arithmetic comparison between two aggregates. Both inputs are
 * asserted on their full outcome, so a rule that stops matching *or* starts matching fails the test.
 */
class WarehouseShipmentsIntegrationTest {

    private val manifestPath: Path = Path.of("src/test/resources/warehouse-shipments/manifest.yaml")

    private val loaded = RuleEngineBuilder.fromManifestEntry(
        manifestPath = manifestPath,
        entryId = "shipment-assessment"
    )

    private fun evaluate(inputFile: String): EvaluationResult {
        val inputPath = manifestPath.parent.resolve("inputs/$inputFile")

        @Suppress("UNCHECKED_CAST")
        val input = JacksonUtil.jsonMapper.readValue(
            Files.readString(inputPath),
            Map::class.java
        ) as Map<String, Any?>

        return loaded.evaluate(input = input)
    }

    private fun assessmentsFor(inputFile: String): Set<String> {
        return evaluate(inputFile = inputFile).matches
            .flatMap { match -> match.actions }
            .filter { action -> action.name == "assessment" }
            .map { action -> action.arguments.first().toString() }
            .toSet()
    }

    @Test
    fun `bundle loads without diagnostics`() {
        assertEquals(
            expected = emptyList(),
            actual = loaded.warnings,
            message = "The example bundle must load cleanly, it is what the documentation points at"
        )
    }

    @Test
    fun `a clean shipment is assessed positively`() {
        assertEquals(
            expected = setOf(
                "service:premium",
                "transit:green",
                "insurance:required",
                "pickup:in-window",
                "condition:green",
                "route:on-time"
            ),
            actual = assessmentsFor(inputFile = "clean-shipment.json")
        )
    }

    @Test
    fun `a problem shipment triggers every risk assessment`() {
        assertEquals(
            expected = setOf(
                "transit:red",
                "condition:red",
                "handling:two-person-lift",
                "handling:fragile-load",
                "consolidation:hub-ham",
                "route:delayed",
                "tracking:gap"
            ),
            actual = assessmentsFor(inputFile = "problem-shipment.json")
        )
    }

    @Test
    fun `every matched rule reports a reason next to its assessment`() {
        val matches = run {
            @Suppress("UNCHECKED_CAST")
            val input = JacksonUtil.jsonMapper.readValue(
                Files.readString(manifestPath.parent.resolve("inputs/problem-shipment.json")),
                Map::class.java
            ) as Map<String, Any?>
            loaded.evaluate(input = input).matches
        }

        assertTrue(actual = matches.isNotEmpty(), message = "Expected the problem shipment to match rules")
        // A rule whose `then` block only publishes variables emits no action by design, so it is
        // excluded rather than being made to carry an assessment it has no opinion about.
        for (match in matches.filter { it.assignments.isEmpty() }) {
            assertEquals(
                expected = listOf("assessment", "reason"),
                actual = match.actions.map { it.name },
                message = "Rule '${match.ruleId}' should emit an assessment and a reason"
            )
        }
    }

    /**
     * The weight rules read `$totalWeightKg` / `$fragileWeightKg` rather than aggregating again, so
     * this pins the values the totals rule publishes — the handling assessments above depend on them.
     */
    @Test
    fun `the totals rule publishes the weights the handling rules read`() {
        val result = evaluate(inputFile = "problem-shipment.json")

        val totals = result.matches.single { match -> match.ruleId == "shipment-totals" }
        assertEquals(expected = setOf("totalWeightKg", "fragileWeightKg"), actual = totals.assignments.keys)
        assertEquals(expected = totals.assignments, actual = result.variables)
        assertTrue(
            actual = (result.variables["totalWeightKg"] as BigDecimal) > BigDecimal("100"),
            message = "two-person-lift only fires above 100 kg, but got ${result.variables["totalWeightKg"]}"
        )
    }
}
