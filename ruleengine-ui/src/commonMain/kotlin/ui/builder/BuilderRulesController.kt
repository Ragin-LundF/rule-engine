package ui.builder

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import ui.replaceRuleDslBlock

/**
 * The Builder's per-rule editor states, which rule is selected, and the edits that change both.
 *
 * Text is reached through [ruleText] / [onRuleTextChange] rather than held here: the buffer belongs
 * to the editor screen, and renaming or adding a rule has to rewrite it.
 *
 * State is exposed as Compose [MutableState] rather than flows for the same reason as
 * `RuleTestController`: the caller reads it during composition, and a coroutine hop would show one
 * frame of the previous selection.
 *
 * **The two sync entry points must stay two separate effects at the call site, in the order
 * [syncSelection] then [rebuildStateMap].** Compose applies effects in declaration order, and that
 * order decides whether [activeState] resolves against the old or the new map on the first frame
 * after a parse. Merging them into one call changes what the Builder shows.
 */
internal class BuilderRulesController(
    private val ruleText: () -> String,
    private val onRuleTextChange: (String) -> Unit,
) {

    val stateMap: MutableState<Map<String, BuilderEditorState>> = mutableStateOf(value = emptyMap())

    val selectedId: MutableState<String> = mutableStateOf(value = "")

    /**
     * A rule that has been added or renamed but whose DSL has not been re-parsed yet.
     *
     * Without it, the selection sync would run against the old parse and snap the selection back to
     * whatever was selected before the rule was added.
     */
    val pendingId: MutableState<String> = mutableStateOf(value = "")

    /**
     * The state for the selected rule, or a fresh empty one.
     *
     * Deliberately not memoised: the fallback is rebuilt on every read so edits made against a
     * missing selection cannot accumulate in a shared object.
     */
    fun activeState(): BuilderEditorState {
        return stateMap.value[selectedId.value] ?: BuilderEditorState.fromBuilderRule(rule = BuilderRule.None)
    }

    /** Keeps the selection pointing at something that still exists after a re-parse. */
    fun syncSelection(rules: List<BuilderRule>, preferredId: String?) {
        val available = rules.mapNotNull { it.ruleId().takeIf { id -> id.isNotBlank() } }
        selectedId.value = when {
            pendingId.value.isNotBlank() && pendingId.value in available -> {
                val id = pendingId.value
                pendingId.value = ""
                id
            }

            pendingId.value.isNotBlank() -> pendingId.value // not yet parsed, keep waiting
            selectedId.value in available -> selectedId.value // keep current selection
            preferredId != null && preferredId in available -> preferredId
            available.isNotEmpty() -> available.first()
            else -> selectedId.value // don't clear when parse temporarily fails
        }
    }

    /**
     * Rebuilds the per-rule states from a fresh parse.
     *
     * A state is kept only when it still matches the rule it was built from; it is rebuilt when the
     * rule appeared, changed lock kind, or the buffer no longer contains the DSL the state would
     * generate — which is how an edit made in Code mode reaches the Builder. States for rules that
     * are not in [rules] yet are carried over, so a just-added rule survives until its text parses.
     */
    fun rebuildStateMap(rules: List<BuilderRule>) {
        val newMap = mutableMapOf<String, BuilderEditorState>()
        val currentFullText = ruleText()

        rules.forEach { rule ->
            val ruleId = rule.ruleId()
            val existing = stateMap.value[ruleId]
            val shouldReset = existing == null ||
                    existing.isLocked != rule.isLocked() ||
                    isBuilderStateStale(existing = existing, currentFullText = currentFullText)
            newMap[ruleId] = if (shouldReset) BuilderEditorState.fromBuilderRule(rule = rule) else existing
        }
        stateMap.value.forEach { (id, existingState) ->
            if (id !in newMap) newMap[id] = existingState
        }
        stateMap.value = newMap
    }

    fun select(ruleId: String) {
        selectedId.value = ruleId
    }

    /** Marks [ruleId] as the selection to adopt once the buffer parses again. */
    fun selectWhenParsed(ruleId: String) {
        pendingId.value = ruleId
    }

    /**
     * Renames a rule, carrying its editor state over and rewriting the buffer.
     *
     * Does nothing when the new id is blank or already taken — the caller is a text field, so both
     * happen while someone is still typing.
     */
    fun rename(oldId: String, newId: String) {
        if (newId.isBlank() || newId in stateMap.value) {
            return
        }
        val oldState = stateMap.value[oldId] ?: return

        val renamed = BuilderEditorState.fromBuilderRule(
            rule = BuilderRule.Supported(
                id = newId,
                conditionNodes = oldState.conditionNodes.map { it.toImmutable() },
                actions = oldState.actions.map { it.toImmutable() },
            ),
        )
        stateMap.value = stateMap.value.toMutableMap().apply {
            remove(oldId)
            put(newId, renamed)
        }
        selectedId.value = newId
        pendingId.value = newId
        onRuleTextChange(ruleText().replace(oldValue = "rule \"$oldId\"", newValue = "rule \"$newId\""))
    }

    /** Appends an empty rule and selects it. */
    fun add() {
        val newId = generateUniqueRuleId(existingIds = stateMap.value.keys)
        val newState = BuilderEditorState.fromBuilderRule(
            rule = BuilderRule.Supported(id = newId, conditionNodes = emptyList(), actions = emptyList()),
        )
        stateMap.value = stateMap.value + mapOf(newId to newState)
        selectedId.value = newId
        pendingId.value = newId

        val skeleton = "\nrule \"$newId\" {\n  when\n  then\n}"
        val current = ruleText()
        onRuleTextChange(if (current.isBlank()) skeleton.trimStart() else current + skeleton)
    }

    /** Replaces only the selected rule's block in the buffer, leaving the other rules intact. */
    fun applyDsl(ruleId: String, newDsl: String) {
        onRuleTextChange(replaceRuleDslBlock(fullText = ruleText(), ruleId = ruleId, newRuleDsl = newDsl))
    }
}
