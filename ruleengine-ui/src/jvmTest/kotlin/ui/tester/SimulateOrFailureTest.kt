package ui.tester

import ui.tester.model.RuleResult
import ui.tester.model.SimulationOutcome
import ui.tester.model.SimulationResult
import ui.tester.model.TraceRow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The guard around a simulation run. A service that throws must still produce a result, because the
 * caller flips `isRunning` back from it — a propagated throw cancels the coroutine and leaves the Run
 * button disabled forever with nothing on screen to explain why.
 */
class SimulateOrFailureTest {

    @Test
    fun `a thrown failure becomes a validation-failed result carrying its message`() {
        val service = ThrowingSimulationService(failure = IllegalStateException("trace blew up"))

        val result = service.simulateOrFailure(
            schemaText = "",
            actionsText = "",
            ruleText = "",
            ruleId = "",
            inputJson = "{}",
        )

        val outcome = assertIs<SimulationOutcome.ValidationFailed>(value = result.outcome)
        assertEquals(expected = "trace blew up", actual = outcome.reason)
    }

    @Test
    fun `a failure without a message still reports something usable`() {
        val service = ThrowingSimulationService(failure = NullPointerException())

        val result = service.simulateOrFailure(
            schemaText = "",
            actionsText = "",
            ruleText = "",
            ruleId = "",
            inputJson = "{}",
        )

        val outcome = assertIs<SimulationOutcome.ValidationFailed>(value = result.outcome)
        assertTrue(actual = outcome.reason.isNotBlank())
    }

    @Test
    fun `a successful run is passed through untouched`() {
        val expected = SimulationResult(
            outcome = SimulationOutcome.Completed(
                ruleResults = listOf(
                    RuleResult(
                        ruleId = "history-at-least-three-months",
                        matched = true,
                        actions = listOf("""assessment "green""""),
                        traceRows = listOf(
                            TraceRow(label = "reports.income.daysOfReport GTE 90", result = true),
                        ),
                    ),
                ),
            ),
        )

        val result = FixedSimulationService(result = expected).simulateOrFailure(
            schemaText = "",
            actionsText = "",
            ruleText = "",
            ruleId = "",
            inputJson = "{}",
        )

        assertEquals(expected = expected, actual = result)
    }
}

private class ThrowingSimulationService(private val failure: Throwable) : RuleSimulationService {
    override fun simulate(
        schemaText: String,
        actionsText: String,
        ruleText: String,
        ruleId: String,
        inputJson: String,
    ): SimulationResult {
        throw failure
    }
}

private class FixedSimulationService(private val result: SimulationResult) : RuleSimulationService {
    override fun simulate(
        schemaText: String,
        actionsText: String,
        ruleText: String,
        ruleId: String,
        inputJson: String,
    ): SimulationResult {
        return result
    }
}
