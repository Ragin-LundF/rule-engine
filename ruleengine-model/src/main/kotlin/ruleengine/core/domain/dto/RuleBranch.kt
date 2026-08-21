package ruleengine.core.domain.dto

/**
 * Which part of a rule produced an output.
 *
 * A rule's `when` verdict selects exactly one branch: [THEN] when the condition held, [ELSE] when it
 * did not, [NOT_EXISTS] when it could not be decided because the data it reads is missing. A rule
 * declares the branches it wants; one it does not declare is simply empty, and a rule with only a
 * `then` block can only ever produce [THEN].
 *
 * Lives in `ruleengine-model` rather than beside
 * [ruleengine.core.domain.dto.RuleMatch] so the UI's `commonMain` can name the branch without
 * depending on `ruleengine-core`.
 */
enum class RuleBranch {
    THEN,
    ELSE,

    /**
     * The `not_exists` block: the condition read something the record does not carry.
     *
     * Reached only by a rule that declares the block. Without it a
     * [ruleengine.core.domain.dto.ConditionVerdict.UNKNOWN] verdict collapses to false and the rule
     * takes [ELSE], which is what every rule written before this branch existed does.
     */
    NOT_EXISTS,
}
