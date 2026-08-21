package ui.diagrams.model

import ruleengine.core.domain.dto.RuleBranch
import ruleengine.dsl.ast.RuleAst

/**
 * One rule, and the branch of it that produces an outcome.
 *
 * The outcome map groups rules by what they can decide, and a rule can decide different things from
 * different branches — `then assessment "GREEN"` and `else assessment "RED"` are two outcomes of one
 * rule. Grouping by rule alone would have to pick one branch and silently drop the rest, which is what
 * this view used to do: it read `then` only, so a bucket could claim "1 rule decides this" while another
 * rule's `else` decided it too.
 */
data class OutcomeSource(
    val rule: RuleAst,
    val branch: RuleBranch,
)
