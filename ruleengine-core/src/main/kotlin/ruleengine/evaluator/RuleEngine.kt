package ruleengine.evaluator

import ruleengine.core.domain.dto.ConditionVerdict
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
     * Variables and memoized values left behind by a previous evaluation are discarded first. A
     * [PreparedRuleContext] holds one record, so re-evaluating it means running the same record
     * again — and it must start from the same clean slate as the first run rather than seeing what
     * that run published. The cache matters as much as the variables: an aggregate over a list
     * variable is memoized against the value the previous run built up.
     *
     * [prepared] is written to during evaluation and must not be shared between threads. Evaluating
     * one engine concurrently is safe as long as each thread brings its own context, which is what
     * [ruleengine.builder.LoadedRuleEngine.evaluate] does.
     */
    fun evaluate(prepared: PreparedRuleContext, includeTrace: Boolean = false): EvaluationResult {
        prepared.clearVariables()
        prepared.cache.clear()
        return evaluateAll(prepared = prepared, includeTrace = includeTrace)
    }

    private fun evaluateAll(prepared: PreparedRuleContext, includeTrace: Boolean): EvaluationResult {
        val matches = mutableListOf<RuleMatch>()
        val collector = traceCollector(includeTrace = includeTrace)
        val matchedRuleIds = mutableListOf<String>()
        var stoppedBy: String? = null
        for (rule in compiledRules) {
            val branch = evaluateRule(rule = rule, prepared = prepared, collector = collector, matches = matches)
            if (branch == RuleBranch.THEN) {
                matchedRuleIds += rule.id
            }
            // Checked after the rule's own output has been collected: `stop` halts what comes after it,
            // not the branch it sits in.
            if (stopsAfter(rule = rule, branch = branch)) {
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
     * The verdict picks the branch, not whether there is one: a block the rule does not declare is
     * simply empty and produces nothing. Returns the branch, which is what the caller reports as a
     * match — only [RuleBranch.THEN] means the condition held, so neither an `else` nor a `not_exists`
     * block producing output makes the rule a match.
     */
    private fun evaluateRule(
        rule: CompiledRule,
        prepared: PreparedRuleContext,
        collector: TraceCollector,
        matches: MutableList<RuleMatch>
    ): RuleBranch {
        collector.enter(meta = NodeMeta(type = NodeType.RULE, ruleId = rule.id))
        val verdict = rule.expression.evaluate(context = prepared, trace = collector)
        val branch = branchFor(rule = rule, verdict = verdict)
        collector.exit(verdict = verdict, branch = branch)

        val actions = actionsOf(rule = rule, branch = branch)
        val assignments = assignmentsOf(rule = rule, branch = branch)
        if (actions.isNotEmpty() || assignments.isNotEmpty()) {
            matches += RuleMatch(
                ruleId = rule.id,
                // Assignments run before the actions resolve, so an action of this same rule can
                // read a variable this rule just published.
                assignments = applyAssignments(assignments = assignments, prepared = prepared),
                actions = actions.map { action -> action.resolve(context = prepared) },
                branch = branch
            )
        }
        return branch
    }

    /**
     * The block [verdict] selects.
     *
     * [ConditionVerdict.UNKNOWN] reaches `not_exists` only when the rule declares it. Otherwise it
     * collapses to false and the rule takes `else`, which is what every rule did before the branch
     * existed and is why adding the branch to the engine changed no existing rule set.
     */
    private fun branchFor(rule: CompiledRule, verdict: ConditionVerdict): RuleBranch {
        return when (verdict) {
            ConditionVerdict.TRUE -> RuleBranch.THEN
            ConditionVerdict.FALSE -> RuleBranch.ELSE
            ConditionVerdict.UNKNOWN -> if (rule.hasNotExistsBranch) RuleBranch.NOT_EXISTS else RuleBranch.ELSE
        }
    }

    private fun actionsOf(rule: CompiledRule, branch: RuleBranch): List<CompiledAction> {
        return when (branch) {
            RuleBranch.THEN -> rule.actions
            RuleBranch.ELSE -> rule.elseActions
            RuleBranch.NOT_EXISTS -> rule.notExistsActions
        }
    }

    private fun assignmentsOf(rule: CompiledRule, branch: RuleBranch): List<CompiledAssignment> {
        return when (branch) {
            RuleBranch.THEN -> rule.assignments
            RuleBranch.ELSE -> rule.elseAssignments
            RuleBranch.NOT_EXISTS -> rule.notExistsAssignments
        }
    }

    private fun stopsAfter(rule: CompiledRule, branch: RuleBranch): Boolean {
        return when (branch) {
            RuleBranch.THEN -> rule.stopOnThen
            RuleBranch.ELSE -> rule.stopOnElse
            RuleBranch.NOT_EXISTS -> rule.stopOnNotExists
        }
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
