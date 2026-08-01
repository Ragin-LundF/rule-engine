package ruleengine.performance

import ruleengine.builder.LoadedRuleEngine
import ruleengine.builder.RuleEngineBuilder
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.RuleContext
import java.util.Locale
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Measures the end-to-end cost of the shape a library user actually runs: a manifest of
 * [BenchmarkProject.RULE_COUNT] rules built once, then evaluated [RUNS] times against a
 * [BenchmarkProject.FIELD_COUNT]-field record.
 *
 * The timing assertions are deliberately loose. A wall-clock number depends on the machine, and a
 * tight ceiling on a shared CI runner fails for reasons that have nothing to do with the engine —
 * so the ceilings here catch an order-of-magnitude regression and nothing subtler. What *is*
 * asserted strictly is that every one of the [RUNS] evaluations produced the same, correct result;
 * that is the part a change can genuinely break.
 *
 * The printed report is the point of the test as much as the assertions are: it is the source of the
 * figures in `docs/performance.md`.
 */
class RuleEnginePerformanceTest {

    private companion object {
        const val RUNS = 100

        /**
         * Enough to reach steady state. With only a couple of dozen warmup runs the reported median
         * roughly triples when the test runs alone, because the evaluator is still interpreted —
         * the report would then say something different depending on what else ran before it.
         */
        const val WARMUP_RUNS = 2_000
        const val NANOS_PER_MILLI = 1_000_000.0
        const val NANOS_PER_MICRO = 1_000.0
        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val P95 = 0.95

        /** Roughly 50x the median measured on a developer laptop — a regression gate, not a target. */
        const val MEDIAN_CEILING_MICROS = 20_000.0
        const val BUILD_CEILING_MILLIS = 10_000.0
    }

    @Test
    fun `a manifest of 20 rules over a 30 field record evaluates 100 times`() {
        val dir = createTempDirectory(prefix = "ruleengine-benchmark")
        try {
            val manifestPath = BenchmarkProject.write(dir = dir)

            // The first build in a fresh JVM also pays for loading and JIT-compiling the lexer,
            // parser, validator and compiler; a second one does not. Both are worth reporting: the
            // first is what application startup costs, the second what recompiling a rule set costs
            // in a warm process — a hot reload, for instance.
            val coldBuildStart = System.nanoTime()
            RuleEngineBuilder.fromManifestEntry(manifestPath = manifestPath, entryId = "benchmark")
            val coldBuildNanos = System.nanoTime() - coldBuildStart

            val warmBuildStart = System.nanoTime()
            val loaded = RuleEngineBuilder.fromManifestEntry(manifestPath = manifestPath, entryId = "benchmark")
            val warmBuildNanos = System.nanoTime() - warmBuildStart

            val input = BenchmarkProject.input(seed = 1)
            val expectedMatches = BenchmarkProject.MATCHING_RULE_COUNT

            assertEquals(
                expected = expectedMatches,
                actual = loaded.evaluate(input = input).matches.size,
                message = "the generated project should match $expectedMatches of ${BenchmarkProject.RULE_COUNT} rules",
            )

            repeat(times = WARMUP_RUNS) { loaded.evaluate(input = input) }

            var correctRuns = 0
            val fullPath = LongArray(RUNS)
            repeat(times = RUNS) { run ->
                val start = System.nanoTime()
                val result = loaded.evaluate(input = input)
                fullPath[run] = System.nanoTime() - start
                if (result.matches.size == expectedMatches) {
                    correctRuns++
                }
            }

            val reusedContext = measureReusedContext(loaded = loaded, input = input)

            printReport(
                coldBuildNanos = coldBuildNanos,
                warmBuildNanos = warmBuildNanos,
                fullPath = fullPath,
                reusedContext = reusedContext,
            )

            assertEquals(
                expected = RUNS,
                actual = correctRuns,
                message = "every evaluation must produce the same $expectedMatches matches",
            )

            val medianMicros = median(timings = fullPath) / NANOS_PER_MICRO
            assertTrue(
                actual = medianMicros < MEDIAN_CEILING_MICROS,
                message = "median evaluation took ${medianMicros}us, expected < ${MEDIAN_CEILING_MICROS}us",
            )
            val buildMillis = coldBuildNanos / NANOS_PER_MILLI
            assertTrue(
                actual = buildMillis < BUILD_CEILING_MILLIS,
                message = "building the manifest took ${buildMillis}ms, expected < ${BUILD_CEILING_MILLIS}ms",
            )
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    /**
     * Times the same rules against a [PreparedRuleContext] built once and reused, which is what
     * separates the cost of preparing a record (normalising text, coercing numbers, parsing dates)
     * from the cost of running the rules over it.
     */
    private fun measureReusedContext(
        loaded: LoadedRuleEngine,
        input: Map<String, Any?>,
    ): LongArray {
        val context = RuleContext.of(entries = input.entries.map { it.key to it.value }.toTypedArray())
        val prepared = PreparedRuleContext.prepare(ctx = context, schema = loaded.schema)

        repeat(times = WARMUP_RUNS) { loaded.engine.evaluate(prepared = prepared) }

        val timings = LongArray(RUNS)
        repeat(times = RUNS) { run ->
            val start = System.nanoTime()
            loaded.engine.evaluate(prepared = prepared)
            timings[run] = System.nanoTime() - start
        }
        return timings
    }

    private fun printReport(
        coldBuildNanos: Long,
        warmBuildNanos: Long,
        fullPath: LongArray,
        reusedContext: LongArray,
    ) {
        println("")
        println("Rule engine benchmark")
        println("  rules:            ${BenchmarkProject.RULE_COUNT}")
        println("  fields:           ${BenchmarkProject.FIELD_COUNT}")
        println("  collection items: ${BenchmarkProject.ITEM_COUNT}")
        println("  evaluations:      $RUNS (after $WARMUP_RUNS warmup runs)")
        println("")
        println("  build (parse + validate + compile)")
        printLine(format = "    first build (cold JVM): %.1f ms", value = coldBuildNanos / NANOS_PER_MILLI)
        printLine(format = "    second build (warm):    %.1f ms", value = warmBuildNanos / NANOS_PER_MILLI)
        println("")
        printTimings(label = "evaluate (prepare + run)", timings = fullPath)
        printTimings(label = "evaluate (context reused)", timings = reusedContext)
        println("")
    }

    private fun printTimings(label: String, timings: LongArray) {
        val mean = timings.average()
        println("  $label")
        printLine(format = "    min:        %.1f us", value = timings.min() / NANOS_PER_MICRO)
        printLine(format = "    median:     %.1f us", value = median(timings = timings) / NANOS_PER_MICRO)
        printLine(format = "    p95:        %.1f us", value = p95(timings = timings) / NANOS_PER_MICRO)
        printLine(format = "    mean:       %.1f us", value = mean / NANOS_PER_MICRO)
        printLine(format = "    throughput: %.0f evaluations/s", value = NANOS_PER_SECOND / mean)
    }

    /** Formats with [Locale.ROOT] so the report reads the same on a machine with a comma decimal separator. */
    private fun printLine(format: String, value: Double) {
        println(String.format(Locale.ROOT, format, value))
    }

    private fun median(timings: LongArray): Double = percentile(timings = timings, fraction = 0.5)

    private fun p95(timings: LongArray): Double = percentile(timings = timings, fraction = P95)

    private fun percentile(timings: LongArray, fraction: Double): Double {
        val sorted = timings.sortedArray()
        val index = (sorted.size * fraction).toInt().coerceIn(minimumValue = 0, maximumValue = sorted.size - 1)
        return sorted[index].toDouble()
    }
}
