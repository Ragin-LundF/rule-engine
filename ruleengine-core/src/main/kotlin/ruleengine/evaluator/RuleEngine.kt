package ruleengine.evaluator

import ruleengine.core.domain.EvaluationResult
import ruleengine.core.domain.RuleMatch
import ruleengine.evaluator.compiled.CompiledActionArgument
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.trace.NoopTraceCollector
import ruleengine.evaluator.trace.RecordingTraceCollector
import ruleengine.evaluator.trace.TraceCollector
import ruleengine.evaluator.trace.dto.DecisionTree
import ruleengine.evaluator.trace.dto.NodeMeta
import ruleengine.evaluator.trace.dto.NodeType

/**
 * Evaluates compiled rules against a prepared context.
 *
 * When [shortCircuitByOutput] is enabled the engine groups rules by every static output
 * they produce (`actionName:value`). It then iterates group by group and stops evaluating a
 * group as soon as one of its rules matches, since further rules in that group would only
 * reproduce the same already-settled output. Rules without any static output are evaluated
 * unconditionally. When disabled the engine evaluates every rule in declaration order.
 *
 * Ordering guarantee: with [shortCircuitByOutput] disabled (the default) rules are evaluated
 * and returned in `matches` in declaration order — the order of the [compiledRules] list, which
 * reflects manifest file order followed by in-file source order. With [shortCircuitByOutput]
 * enabled this guarantee does NOT hold: `matches` are ordered by output group, not declaration
 * order. Enable it only when the consumer does not depend on match order.
 */
class RuleEngine(
    private val compiledRules: List<CompiledRule>,
    private val shortCircuitByOutput: Boolean = false
) {
    private val outputGroups: Map<String, List<CompiledRule>>
    private val ungroupedRules: List<CompiledRule>

    init {
        if (shortCircuitByOutput) {
            val groups = mutableMapOf<String, MutableList<CompiledRule>>()
            val ungrouped = mutableListOf<CompiledRule>()
            for (rule in compiledRules) {
                val keys = staticOutputKeys(rule = rule)
                if (keys.isEmpty()) {
                    ungrouped += rule
                } else {
                    for (key in keys) {
                        groups.getOrPut(key) { mutableListOf() } += rule
                    }
                }
            }
            outputGroups = groups
            ungroupedRules = ungrouped
        } else {
            outputGroups = emptyMap()
            ungroupedRules = emptyList()
        }
    }

    fun evaluate(prepared: PreparedRuleContext, includeTrace: Boolean = false): EvaluationResult {
        if (shortCircuitByOutput) {
            return evaluateGrouped(prepared = prepared, includeTrace = includeTrace)
        }
        return evaluateAll(prepared = prepared, includeTrace = includeTrace)
    }

    private fun evaluateAll(prepared: PreparedRuleContext, includeTrace: Boolean): EvaluationResult {
        val matches = mutableListOf<RuleMatch>()
        val collector = traceCollector(includeTrace = includeTrace)
        val matchedRuleIds = mutableListOf<String>()
        for (rule in compiledRules) {
            if (evaluateRule(rule = rule, prepared = prepared, collector = collector, matches = matches)) {
                matchedRuleIds += rule.id
            }
        }
        return buildResult(
            matches = matches,
            collector = collector,
            matchedRuleIds = matchedRuleIds,
            includeTrace = includeTrace
        )
    }

    private fun evaluateGrouped(prepared: PreparedRuleContext, includeTrace: Boolean): EvaluationResult {
        val matches = mutableListOf<RuleMatch>()
        val collector = traceCollector(includeTrace = includeTrace)
        val matchedRuleIds = mutableSetOf<String>()
        for (group in outputGroups.values) {
            for (rule in group) {
                // A rule can belong to several groups; skip it once it has matched elsewhere.
                if (rule.id !in matchedRuleIds &&
                    evaluateRule(rule = rule, prepared = prepared, collector = collector, matches = matches)
                ) {
                    matchedRuleIds += rule.id
                    break
                }
            }
        }
        for (rule in ungroupedRules) {
            if (evaluateRule(rule = rule, prepared = prepared, collector = collector, matches = matches)) {
                matchedRuleIds += rule.id
            }
        }
        return buildResult(
            matches = matches,
            collector = collector,
            matchedRuleIds = matchedRuleIds.toList(),
            includeTrace = includeTrace
        )
    }

    private fun evaluateRule(
        rule: CompiledRule,
        prepared: PreparedRuleContext,
        collector: TraceCollector,
        matches: MutableList<RuleMatch>
    ): Boolean {
        collector.enter(meta = NodeMeta(type = NodeType.RULE, ruleId = rule.id))
        val matched = rule.expression.evaluate(context = prepared, trace = collector)
        collector.exit(result = matched)
        if (matched) {
            matches += RuleMatch(
                ruleId = rule.id,
                actions = rule.actions.map { action -> action.resolve(context = prepared) }
            )
        }
        return matched
    }

    private fun traceCollector(includeTrace: Boolean): TraceCollector {
        return if (includeTrace) RecordingTraceCollector() else NoopTraceCollector()
    }

    private fun buildResult(
        matches: List<RuleMatch>,
        collector: TraceCollector,
        matchedRuleIds: List<String>,
        includeTrace: Boolean
    ): EvaluationResult {
        val tree = if (includeTrace) {
            DecisionTree(root = collector.root(), matchedRules = matchedRuleIds)
        } else {
            null
        }
        return EvaluationResult(matches = matches, trace = tree)
    }

    private fun staticOutputKeys(rule: CompiledRule): Set<String> {
        if (rule.actions.isEmpty()) {
            return emptySet()
        }
        val keys = mutableSetOf<String>()
        for (action in rule.actions) {
            val firstStatic = action.arguments.firstOrNull() as? CompiledActionArgument.Static ?: continue
            keys += "${action.name}:${firstStatic.value}"
        }
        return keys
    }
}
