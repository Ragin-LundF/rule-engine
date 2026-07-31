package ruleengine.evaluator.trace.dto

fun MutableNode.toDecisionNode(): DecisionNode {
    return DecisionNode(
        id = id,
        type = type,
        field = field,
        operator = operator,
        expected = expected,
        actual = actual,
        result = result ?: false,
        evaluationTimeMs = elapsedMs,
        ruleId = ruleId,
        children = children.map { it.toDecisionNode() }
    )
}
