package ui.tester

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ui.editor.rules.RuleEditorState
import ui.editor.rules.model.StatusKind
import ui.pickInputJsonFile
import ui.tester.model.TestInputState

/**
 * Runs a test and owns what the test panels show while it runs.
 *
 * One controller serves both surfaces — the centre Test mode and the right panel's Simulate tab —
 * because they run the same thing and must agree about whether a run is in progress.
 *
 * [input] is a Compose [MutableState] rather than a `StateFlow` on purpose. A flow would put a
 * coroutine hop between setting `isRunning` and the recomposition that draws "Running…", so the
 * button would keep its idle look for a frame after being pressed. That is a deliberate deviation
 * from the "expose StateFlow" guidance in `ruleengine-ui.md`, and it is about frame timing.
 */
internal class RuleTestController(
    private val state: RuleEditorState,
    private val scope: CoroutineScope,
    private val service: RuleSimulationService = JvmRuleSimulationService(),
    private val pickJson: suspend () -> String? = { pickInputJsonFile() },
) {

    val input: MutableState<TestInputState> = mutableStateOf(value = TestInputState.Empty)

    /**
     * Runs [ruleText] against the current input JSON.
     *
     * `simulateOrFailure` is what keeps a thrown simulation from stranding the button on "Running…",
     * and the status message means a run always leaves a mark outside the panel's own scroll area.
     */
    fun run(ruleText: String) {
        scope.launch {
            input.value = input.value.copy(isRunning = true)
            val result = service.simulateOrFailure(
                schemaText = state.schemaText.value,
                actionsText = state.actionSchemaText.value,
                ruleText = ruleText,
                // All-files mode disables the rule selector without clearing its value, so a rule
                // picked earlier would keep filtering the run while the panel reads "All rules". The
                // rule text and the rule filter have to agree, and this is the one place both are read.
                ruleId = if (state.showAllRules.value) "" else input.value.selectedRuleId,
                inputJson = input.value.inputJson,
                scope = state.activeScope.orEmpty(),
            )
            input.value = input.value.copy(isRunning = false, outcome = result.outcome)
            state.setStatus(
                msg = runStatusMessage(outcome = result.outcome),
                kind = runStatusKind(outcome = result.outcome),
            )
        }
    }

    /** Asks for an input JSON file and loads it, reporting a cancelled dialog as such. */
    fun loadInputJson() {
        scope.launch {
            val content = pickJson()
            if (content == null) {
                state.setStatus(msg = "Input JSON load cancelled", kind = StatusKind.IDLE)
                return@launch
            }
            input.value = input.value.copy(inputJson = content)
            state.setStatus(msg = "Input JSON loaded", kind = StatusKind.SUCCESS)
        }
    }
}
