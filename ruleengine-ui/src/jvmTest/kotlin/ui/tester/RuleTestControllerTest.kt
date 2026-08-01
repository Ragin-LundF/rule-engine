package ui.tester

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import ui.editor.rules.RuleEditorState
import ui.editor.rules.StatusKind
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Test-run orchestration, previously two local functions inside the editor screen.
 *
 * The controller takes its simulation service and JSON picker as constructor parameters precisely so
 * this can drive it without a composition or a file dialog.
 */
class RuleTestControllerTest {

    private class RecordingService(
        private val outcome: SimulationOutcome = SimulationOutcome.Completed(ruleResults = emptyList()),
    ) : RuleSimulationService {
        var lastRuleId: String? = null
        var lastRuleText: String? = null

        override fun simulate(
            schemaText: String,
            actionsText: String,
            ruleText: String,
            ruleId: String,
            inputJson: String,
        ): SimulationResult {
            lastRuleId = ruleId
            lastRuleText = ruleText
            return SimulationResult(outcome = outcome)
        }
    }

    private fun controller(
        service: RuleSimulationService,
        state: RuleEditorState = RuleEditorState(scope = CoroutineScope(EmptyCoroutineContext)),
        pickJson: suspend () -> String? = { null },
    ) = Pair(state, RuleTestController(state = state, scope = this.scope, service = service, pickJson = pickJson))

    private lateinit var scope: CoroutineScope

    private fun test(block: suspend () -> Unit) = runBlocking {
        scope = this
        block()
    }

    /**
     * Waits for the coroutines `run`/`loadInputJson` launched.
     *
     * They are fire-and-forget by design — the caller is a click handler — so a test has to join
     * them explicitly or it asserts on state the run has not reached yet.
     */
    private suspend fun settle() {
        scope.coroutineContext.job.children.toList().joinAll()
    }

    @Test
    fun `a finished run clears isRunning and records the outcome`() = test {
        val (_, controller) = controller(service = RecordingService())

        controller.run(ruleText = "rule \"r\" { }")

        settle()

        assertFalse(actual = controller.input.value.isRunning)
        assertTrue(actual = controller.input.value.outcome is SimulationOutcome.Completed)
    }

    @Test
    fun `a run reports its verdict on the status line`() = test {
        val (state, controller) = controller(service = RecordingService())

        controller.run(ruleText = "rule \"r\" { }")

        settle()

        assertEquals(expected = StatusKind.IDLE, actual = state.statusKind.value, message = "nothing matched")
        assertEquals(expected = "0 of 0 rules matched — 0 action(s)", actual = state.status.value)
    }

    /**
     * All-files mode disables the rule selector without clearing it, so a rule picked earlier would
     * otherwise keep filtering a run the panel describes as "All rules".
     */
    @Test
    fun `all-files mode blanks the rule filter even when one is still selected`() = test {
        val service = RecordingService()
        val (state, controller) = controller(service = service)
        controller.input.value = controller.input.value.copy(selectedRuleId = "vip")

        state.showAllRules.value = false
        controller.run(ruleText = "x")
        settle()
        assertEquals(expected = "vip", actual = service.lastRuleId)

        state.showAllRules.value = true
        controller.run(ruleText = "x")
        settle()
        assertEquals(expected = "", actual = service.lastRuleId)
    }

    @Test
    fun `a thrown simulation is reported rather than stranding the run`() = test {
        val throwing = object : RuleSimulationService {
            override fun simulate(
                schemaText: String,
                actionsText: String,
                ruleText: String,
                ruleId: String,
                inputJson: String,
            ): SimulationResult = throw IllegalStateException("boom")
        }
        val (state, controller) = controller(service = throwing)

        controller.run(ruleText = "x")

        settle()

        assertFalse(actual = controller.input.value.isRunning, message = "the button must not stay on Running…")
        assertTrue(actual = controller.input.value.outcome is SimulationOutcome.ValidationFailed)
        assertEquals(expected = StatusKind.ERROR, actual = state.statusKind.value)
    }

    // ── loading input JSON ────────────────────────────────────────────────────

    @Test
    fun `a chosen file becomes the input json`() = test {
        val (state, controller) = controller(service = RecordingService(), pickJson = { """{"a":1}""" })

        controller.loadInputJson()

        settle()

        assertEquals(expected = """{"a":1}""", actual = controller.input.value.inputJson)
        assertEquals(expected = StatusKind.SUCCESS, actual = state.statusKind.value)
    }

    @Test
    fun `a cancelled dialog leaves the input alone and says so`() = test {
        val (state, controller) = controller(service = RecordingService(), pickJson = { null })
        controller.input.value = controller.input.value.copy(inputJson = "kept")

        controller.loadInputJson()

        settle()

        assertEquals(expected = "kept", actual = controller.input.value.inputJson)
        assertEquals(expected = "Input JSON load cancelled", actual = state.status.value)
    }
}
