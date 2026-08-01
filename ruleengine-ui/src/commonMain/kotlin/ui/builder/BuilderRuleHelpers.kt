package ui.builder

/**
 * Small facts about a [BuilderRule] and its editor state, used by the screen that hosts the builder.
 *
 * Pure functions with no Compose in them, which is what makes `RuleEditorHelpersTest` able to pin
 * them — they were `private` inside the editor screen and unreachable from a test.
 */

/**
 * Generates a unique rule ID that does not clash with any of the [existingIds].
 */
internal fun generateUniqueRuleId(existingIds: Set<String>): String {
    var counter = existingIds.size + 1
    var candidate = "rule-$counter"
    while (candidate in existingIds) {
        counter++
        candidate = "rule-$counter"
    }
    return candidate
}

internal fun BuilderRule.isLocked(): Boolean = when (this) {
    is BuilderRule.Supported -> false
    is BuilderRule.Unsupported -> true
    BuilderRule.None -> true
}

internal fun BuilderRule.ruleId(): String = when (this) {
    is BuilderRule.Supported -> id
    is BuilderRule.Unsupported -> id
    BuilderRule.None -> ""
}

/**
 * Returns true when the existing [BuilderEditorState] no longer matches the
 * current DSL text for its rule. This detects externally-applied edits (e.g.,
 * modifications in code mode) so the builder state is rebuilt from the current
 * parse rather than showing stale conditions.
 *
 * The check compares what [BuilderToRuleDsl.generate] would produce from the
 * cached state against the actual rule text. If they differ, the state is stale.
 */
internal fun isBuilderStateStale(
    existing: BuilderEditorState?,
    currentFullText: String,
): Boolean {
    if (existing == null || existing.isLocked) return false
    val generated = BuilderToRuleDsl.generate(state = existing) ?: return true
    // Normalize both to compare: trim whitespace, unify line endings
    val generatedNorm = generated.trim().replace(oldValue = "\r\n", newValue = "\n")
    val fullNorm = currentFullText.trim().replace(oldValue = "\r\n", newValue = "\n")
    return !fullNorm.contains(other = generatedNorm)
}
