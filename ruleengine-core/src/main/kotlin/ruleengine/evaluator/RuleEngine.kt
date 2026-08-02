package ruleengine.evaluator

import ruleengine.core.domain.dto.EvaluationResult
import ruleengine.core.domain.dto.RuleBranch
import ruleengine.core.domain.dto.RuleMatch
import ruleengine.evaluator.compiled.value.result.ExpressionValues
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
 * Every rule is evaluated, in the order of the [compiledRules] list — manifest file order followed by
 * in-file source order — and `matches` is returned in that same order. That ordering is a guarantee, not
 * an accident of the implementation: two constructs depend on it. A `set` clause publishes a value that
 * only the rules after it can read, and a branch ending in `stop` ends the run at its own position.
 *
 * A branch ending in `stop` ends the run once it has fired: the rule's own output is collected, then the
 * rules declared after it are not evaluated at all. [EvaluationResult.stoppedBy] names the rule that
 * halted, so a consumer can tell the rules that did not match from the rules that never ran.
 */
class RuleEngine(
    private val compiledRules: List<CompiledRule>
) {

    /**
     * Evaluates every rule against [prepared].
     *
     * Variables left behind by a previous evaluation are discarded first. A [PreparedRuleContext]
     * holds one record, so re-evaluating it means running the same record again — and it must start
     * from the same clean slate as the first run rather than seeing the variables that run published.
     *
     * [prepared] is written to during evaluation and must not be shared between threads. Evaluating
     * one engine concurrently is safe as long as each thread brings its own context, which is what
     * [ruleengine.builder.LoadedRuleEngine.evaluate] does.
     */
    fun evaluate(prepared: PreparedRuleContext, includeTrace: Boolean = false): EvaluationResult {
        prepared.clearVariables()
        return evaluateAll(prepared = prepared, includeTrace = includeTrace)
    }

    private fun evaluateAll(prepared: PreparedRuleContext, includeTrace: Boolean): EvaluationResult {
        val matches = mutableListOf<RuleMatch>()
        val collector = traceCollector(includeTrace = includeTrace)
        val matchedRuleIds = mutableListOf<String>()
        var stoppedBy: String? = null
        for (rule in compiledRules) {
            val matched = evaluateRule(rule = rule, prepared = prepared, collector = collector, matches = matches)
            if (matched) {
                matchedRuleIds += rule.id
            }
            // Checked after the rule's own output has been collected: `stop` halts what comes after it,
            // not the branch it sits in.
            if (if (matched) rule.stopOnThen else rule.stopOnElse) {
                stoppedBy = rule.id
                break
            }
        }
        return buildResult(
            matches = matches,
            collector = collector,
            matchedRuleIds = matchedRuleIds,
            includeTrace = includeTrace,
            prepared = prepared,
            stoppedBy = stoppedBy
        )
    }

    /**
     * Evaluates one rule and records whatever the selected branch produced.
     *
     * The verdict picks the branch, not whether there is one: a false condition resolves the `else`
     * block, which is the [RuleBranch.ELSE] counterpart of the `then` block and is empty for a rule
     * that declares none. Returns the condition's verdict, which is what the caller reports as a
     * match — an `else` branch producing output does not make the condition true.
     */
    private fun evaluateRule(
        rule: CompiledRule,
        prepared: PreparedRuleContext,
        collector: TraceCollector,
        matches: MutableList<RuleMatch>
    ): Boolean {
        collector.enter(meta = NodeMeta(type = NodeType.RULE, ruleId = rule.id))
        val matched = rule.expression.evaluate(context = prepared, trace = collector)
        collector.exit(result = matched)

        val actions = if (matched) rule.actions else rule.elseActions
        val assignments = if (matched) rule.assignments else rule.elseAssignments
        if (actions.isNotEmpty() || assignments.isNotEmpty()) {
            matches += RuleMatch(
                ruleId = rule.id,
                // Assignments run before the actions resolve, so an action of this same rule can
                // read a variable this rule just published.
                assignments = applyAssignments(assignments = assignments, prepared = prepared),
                actions = actions.map { action -> action.resolve(context = prepared) },
                branch = if (matched) RuleBranch.THEN else RuleBranch.ELSE
            )
        }
        return matched
    }

    private fun applyAssignments(
        assignments: List<CompiledAssignment>,
        prepared: PreparedRuleContext
    ): Map<String, Any?> {
        if (assignments.isEmpty()) {
            return emptyMap()
        }
        val applied = LinkedHashMap<String, Any?>(assignments.size)
        for (assignment in assignments) {
            assignment.apply(context = prepared)
            applied[assignment.name] = prepared.variables[assignment.name]
                ?.let { value -> ExpressionValues.unwrap(value = value) }
        }
        return applied
    }

    private fun traceCollector(includeTrace: Boolean): TraceCollector {
        return if (includeTrace) RecordingTraceCollector() else NoopTraceCollector()
    }

    @Suppress("LongParameterList")
    private fun buildResult(
        matches: List<RuleMatch>,
        collector: TraceCollector,
        matchedRuleIds: List<String>,
        includeTrace: Boolean,
        prepared: PreparedRuleContext,
        stoppedBy: String? = null
    ): EvaluationResult {
        val tree = if (includeTrace) {
            DecisionTree(root = collector.root(), matchedRules = matchedRuleIds)
        } else {
            null
        }
        return EvaluationResult(
            matches = matches,
            trace = tree,
            variables = prepared.variables.mapValues { (_, value) -> ExpressionValues.unwrap(value = value) },
            stoppedBy = stoppedBy
        )
    }

}
