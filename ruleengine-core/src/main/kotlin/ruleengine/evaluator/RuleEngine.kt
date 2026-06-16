package ruleengine.evaluator

import ruleengine.core.domain.EvaluationResult
import ruleengine.core.domain.FieldSchema
import ruleengine.core.domain.RuleMatch
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.trace.DecisionTree
import ruleengine.evaluator.trace.NodeMeta
import ruleengine.evaluator.trace.NodeType
import ruleengine.evaluator.trace.NoopTraceCollector
import ruleengine.evaluator.trace.RecordingTraceCollector

class RuleEngine(
    private val compiledRules: List<CompiledRule>,
    private val schema: FieldSchema
) {
    fun evaluate(prepared: PreparedRuleContext, includeTrace: Boolean = false): EvaluationResult {
        val matches = mutableListOf<RuleMatch>()
        val collector = if (includeTrace) RecordingTraceCollector() else NoopTraceCollector()
        val matchedRuleIds = mutableListOf<String>()

        for (r in compiledRules) {
            // enter rule node with rule id
            collector.enter(
                NodeMeta(
                    type = NodeType.RULE,
                    ruleId = r.id
                )
            )
            val ok = r.expression.evaluate(context = prepared, trace = collector)
            collector.exit(ok)
            if (ok) {
                val resolvedActions = r.actions.map { compiledAction ->
                    compiledAction.resolve(context = prepared)
                }
                matches += RuleMatch(ruleId = r.id, actions = resolvedActions)
                matchedRuleIds += r.id
            }
        }

        val tree = if (includeTrace) {
            DecisionTree(root = collector.root(), matchedRules = matchedRuleIds)
        } else {
            null
        }
        return EvaluationResult(matches = matches, trace = tree)
    }
}

