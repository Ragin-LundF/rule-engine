package ruleengine.core.domain.dto

/**
 * Which half of a rule produced an output.
 *
 * A rule's `when` verdict selects exactly one branch: [THEN] when the condition held, [ELSE] when it
 * did not and the rule declares an `else` block. A rule without an `else` block only ever produces
 * [THEN].
 *
 * Lives in `ruleengine-model` rather than beside
 * [ruleengine.core.domain.dto.RuleMatch] so the UI's `commonMain` can name the branch without
 * depending on `ruleengine-core`.
 */
enum class RuleBranch { THEN, ELSE }
