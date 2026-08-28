package ui.builder.model.mutable

import ui.builder.model.BuilderConditionNode

/**
 * The immutable node the mappers produce, as the editable node the canvases hold.
 *
 * A package-level function rather than a private member of [BuilderEditorState]'s companion, because two
 * callers need it now: loading a rule, and the formula bar replacing one row with a freshly parsed node.
 * A conversion the whole package can reach is also the honest description — it belongs to the pair of
 * models, not to the state class that happened to need it first.
 *
 * The id and the join come across unchanged. A caller that needs to keep the *replaced* row's identity
 * overrides them afterwards; see `replaceNodeFromFormula`, which has to.
 */
internal fun BuilderConditionNode.toMutable(): MutableConditionNode = when (this) {
    is BuilderConditionNode.Condition -> MutableConditionNode.Leaf(
        MutableBuilderCondition(
            id = nodeId,
            field = field,
            operator = operator,
            value = value,
            valueTo = valueTo,
            listItems = listItems,
            ignoreCase = ignoreCase,
            negated = negated,
            joinToPrevious = joinToPrevious,
        )
    )

    is BuilderConditionNode.Comparison -> MutableConditionNode.ComparisonLeaf(
        MutableBuilderComparison(
            id = nodeId,
            left = left,
            operator = operator,
            right = right,
            ignoreCase = ignoreCase,
            negated = negated,
            joinToPrevious = joinToPrevious,
        )
    )

    is BuilderConditionNode.Group -> MutableConditionNode.Group(
        id = nodeId,
        nodes = nodes.map { it.toMutable() },
        joinToPrevious = joinToPrevious,
        negated = negated,
    )
}
