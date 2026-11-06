package ruleengine.intellij.diagram.model

import ruleengine.dsl.ast.rule.RuleAst

/** What the diagram tool window shows: a rule, a message, or a rule with a message above it. */
data class DiagramViewState(
    val rule: RuleAst? = null,
    val message: String? = null,
)
