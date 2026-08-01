package ruleengine.builder

import ruleengine.performance.BenchmarkProject
import java.math.BigDecimal
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins down the thread-safety contract `docs/integration-guide.md` states for the build and
 * evaluate phases.
 *
 * [RuleEngineBuilder] is an `object` without a single field and [RuleEngine] never writes an instance
 * field after construction, so both are safe to share. The interesting case is `set` variables: they
 * are the one feature where a rule publishes a value another rule reads, which makes them look like
 * shared state. They are not — [LoadedRuleEngine.evaluate] builds a fresh `PreparedRuleContext`, and
 * with it a fresh variable map, for every call. These tests fail if that ever stops being true.
 *
 * What is deliberately *not* tested is sharing one `PreparedRuleContext` across threads. That is
 * unsafe by design and documented as per-call; a test for it could only assert on a race, which
 * would pass or fail for reasons unrelated to the code.
 */
class RuleEngineConcurrencyTest {

    private companion object {
        const val THREAD_COUNT = 8
        const val ITERATIONS_PER_THREAD = 50
        const val SHUTDOWN_TIMEOUT_SECONDS = 60L
        const val ENTRY_ID = "benchmark"
    }

    @Test
    fun `concurrent builds of the same manifest produce independent, equivalent engines`() {
        withBenchmarkProject { manifestPath ->
            val engines = runConcurrently { _ ->
                RuleEngineBuilder.fromManifestEntry(manifestPath = manifestPath, entryId = ENTRY_ID)
            }

            assertEquals(
                expected = THREAD_COUNT,
                actual = engines.map { engine -> System.identityHashCode(engine) }.distinct().size,
                message = "each build must return its own engine instance",
            )

            val input = BenchmarkProject.input(seed = 1)
            val results = engines.map { engine -> engine.evaluate(input = input) }
            results.forEach { result ->
                assertEquals(
                    expected = BenchmarkProject.MATCHING_RULE_COUNT,
                    actual = result.matches.size,
                    message = "every concurrently built engine must reach the same verdict",
                )
                assertEquals(
                    expected = BenchmarkProject.expectedTotalValue(seed = 1),
                    actual = totalValueOf(variables = result.variables),
                    message = "every concurrently built engine must publish the same variable",
                )
            }
        }
    }

    @Test
    fun `variables stay isolated when one engine is evaluated from many threads`() {
        withBenchmarkProject { manifestPath ->
            val shared = RuleEngineBuilder.fromManifestEntry(manifestPath = manifestPath, entryId = ENTRY_ID)

            // Each thread feeds a different record, so the `set` variable it publishes is unique to
            // it. If one thread's variables leaked into another's evaluation, the totals would cross.
            val expectedTotals = (0 until THREAD_COUNT).map { seed -> BenchmarkProject.expectedTotalValue(seed = seed) }
            assertEquals(
                expected = THREAD_COUNT,
                actual = expectedTotals.distinct().size,
                message = "the seeds must drive distinct variables or the test proves nothing",
            )

            val failures = ConcurrentLinkedQueue<String>()
            runConcurrently { seed -> evaluateRepeatedly(engine = shared, seed = seed, failures = failures) }

            assertTrue(
                actual = failures.isEmpty(),
                message = "concurrent evaluation leaked state: ${failures.take(n = 5)}",
            )
        }
    }

    private fun evaluateRepeatedly(engine: LoadedRuleEngine, seed: Int, failures: ConcurrentLinkedQueue<String>) {
        val input = BenchmarkProject.input(seed = seed)
        val expectedTotal = BenchmarkProject.expectedTotalValue(seed = seed)
        repeat(times = ITERATIONS_PER_THREAD) { iteration ->
            val result = engine.evaluate(input = input)
            val actualTotal = totalValueOf(variables = result.variables)
            if (actualTotal != expectedTotal) {
                failures += "seed $seed iteration $iteration: totalValue was $actualTotal, expected $expectedTotal"
            }
            if (result.matches.size != BenchmarkProject.MATCHING_RULE_COUNT) {
                failures += "seed $seed iteration $iteration: ${result.matches.size} matches, " +
                        "expected ${BenchmarkProject.MATCHING_RULE_COUNT}"
            }
        }
    }

    /**
     * Runs [work] on [THREAD_COUNT] threads released together by a start gate, so the calls actually
     * overlap instead of running one after another on a warm pool.
     */
    private fun <T> runConcurrently(work: (Int) -> T): List<T> {
        val pool = Executors.newFixedThreadPool(THREAD_COUNT)
        try {
            val startGate = CountDownLatch(1)
            val futures = (0 until THREAD_COUNT).map { index ->
                pool.submit<T> {
                    startGate.await()
                    work(index)
                }
            }
            startGate.countDown()
            return futures.map { future -> future.get(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS) }
        } finally {
            pool.shutdown()
            pool.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }
    }

    private fun totalValueOf(variables: Map<String, Any?>): Int = (variables["totalValue"] as BigDecimal).toInt()

    private fun withBenchmarkProject(block: (Path) -> Unit) {
        val dir = createTempDirectory(prefix = "ruleengine-concurrency")
        try {
            block(BenchmarkProject.write(dir = dir))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }
}
