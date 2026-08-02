package ui.tester

import ruleengine.compiler.Compiler
import ruleengine.compiler.Validator
import ruleengine.core.domain.dto.RuleAction
import ruleengine.core.domain.dto.RuleBranch
import ruleengine.core.domain.dto.RuleMatch
import ruleengine.core.domain.dto.action.ActionSchema
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.errors.Severity
import ruleengine.dsl.ast.RuleAst
import ruleengine.dsl.parser.Parser
import ruleengine.evaluator.CompiledRule
import ruleengine.evaluator.RuleEngine
import ruleengine.evaluator.ScopedEvaluation
import ruleengine.evaluator.context.MapRuleContext
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.trace.dto.DecisionNode
import ruleengine.evaluator.trace.dto.DecisionTree
import ruleengine.evaluator.trace.dto.NodeType
import ruleengine.jackson.JacksonUtil
import ruleengine.schema.ActionSchemaLoader
import ruleengine.schema.FieldSchemaLoader
import ui.tester.model.RuleResult
import ui.tester.model.SimulationOutcome
import ui.tester.model.SimulationResult
import ui.tester.model.TraceNode
import ui.tester.model.TraceRow

/**
 * JVM implementation of [RuleSimulationService].
 *
 * Runs the full core pipeline:
 *   JSON parse → DSL parse → validate → compile → PreparedRuleContext → RuleEngine.evaluate()
 *
 * Never throws — all errors are captured and returned as [SimulationOutcome] variants.
 */
class JvmRuleSimulationService : RuleSimulationService {

    override fun simulate(
        schemaText: String,
        actionsText: String,
        ruleText: String,
        ruleId: String,
        inputJson: String,
        scope: String,
    ): SimulationResult = try {
        SimulationResult(
            outcome = runPipeline(
                schemaText = schemaText,
                actionsText = actionsText,
                ruleText = ruleText,
                ruleId = ruleId,
                inputJson = inputJson,
                scope = scope,
            ),
        )
    } catch (stopped: PipelineStopped) {
        SimulationResult(outcome = stopped.outcome)
    }

    /**
     * The pipeline, in the order the core runs it.
     *
     * Each stage reports its own failure through [stop] rather than returning one, so this reads as
     * the straight line it is. The order is load-bearing: the rule text is only parsed once a schema
     * exists to validate it against.
     */
    @Suppress("LongParameterList")
    private fun runPipeline(
        schemaText: String,
        actionsText: String,
        ruleText: String,
        ruleId: String,
        inputJson: String,
        scope: String,
    ): SimulationOutcome {
        val factMap = parseFacts(inputJson = inputJson)
        val documentSchema = loadSchema(schemaText = schemaText)
        // A scoped entry's rules name the member's fields, so everything from validation onwards has
        // to see the member's schema — otherwise every member field reads as an unknown field.
        val memberSchema = scope.takeIf { it.isNotBlank() }
            ?.let { path -> ScopedEvaluation.memberSchema(schema = documentSchema, scope = path) }
        val schema = memberSchema ?: documentSchema
        val actionSchema = loadActionSchema(actionsText = actionsText)
        val asts = parseAndValidate(ruleText = ruleText, schema = schema, actionSchema = actionSchema)
        val targetAsts = selectTarget(asts = asts, ruleId = ruleId)
        val compiledRules = compile(targetAsts = targetAsts, schema = schema)
        return evaluate(
            compiledRules = compiledRules,
            documentSchema = documentSchema,
            memberSchema = memberSchema,
            scope = scope,
            factMap = factMap,
            targetAsts = targetAsts,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseFacts(inputJson: String): Map<String, Any?> = runCatching {
        val mapType = JacksonUtil.jsonMapper.typeFactory
            .constructMapType(Map::class.java, String::class.java, Any::class.java)
        JacksonUtil.jsonMapper.readValue(inputJson, mapType) as Map<String, Any?>
    }.getOrElse { e ->
        stop(SimulationOutcome.InvalidJson(reason = e.message ?: "Invalid JSON"))
    }

    private fun loadSchema(schemaText: String): FieldSchema {
        if (schemaText.isBlank()) {
            stop(SimulationOutcome.ValidationFailed(reason = "No field schema loaded"))
        }
        return runCatching {
            FieldSchemaLoader.loadFromString(content = schemaText, nameHint = "schema")
        }.getOrElse { e ->
            stop(SimulationOutcome.ValidationFailed(reason = "Field schema error: ${e.message}"))
        }
    }

    /** Optional, and deliberately silent on failure: a rule set may use no actions at all. */
    private fun loadActionSchema(actionsText: String): ActionSchema? =
        if (actionsText.isBlank()) {
            null
        } else {
            runCatching { ActionSchemaLoader.loadFromString(content = actionsText) }.getOrNull()
        }

    private fun parseAndValidate(ruleText: String, schema: FieldSchema, actionSchema: ActionSchema?): List<RuleAst> {
        val asts = runCatching {
            Parser(input = ruleText).parseRules()
        }.getOrElse { e ->
            stop(SimulationOutcome.ValidationFailed(reason = "Parse error: ${e.message}"))
        }
        if (asts.isEmpty()) {
            stop(SimulationOutcome.ValidationFailed(reason = "No rules found in rule text"))
        }

        val validationResult = runCatching {
            Validator.validate(asts = asts, schema = schema, actions = actionSchema)
        }.getOrElse { e ->
            stop(SimulationOutcome.ValidationFailed(reason = "Validation error: ${e.message}"))
        }
        val errors = validationResult.diagnostics.filter { it.severity == Severity.ERROR }
        if (errors.isNotEmpty()) {
            stop(SimulationOutcome.ValidationFailed(reason = errors.joinToString("; ") { it.message }))
        }
        return asts
    }

    /** A blank [ruleId] means "all rules" — that is how the panel's All-files mode reaches here. */
    private fun selectTarget(asts: List<RuleAst>, ruleId: String): List<RuleAst> {
        val targetAsts = if (ruleId.isBlank()) asts else asts.filter { it.id == ruleId }
        if (targetAsts.isEmpty()) {
            stop(SimulationOutcome.ValidationFailed(reason = "Rule \"$ruleId\" not found"))
        }
        return targetAsts
    }

    private fun compile(targetAsts: List<RuleAst>, schema: FieldSchema) = runCatching {
        Compiler.compileRules(asts = targetAsts, schema = schema)
    }.getOrElse { e ->
        stop(SimulationOutcome.ValidationFailed(reason = "Compile error: ${e.message}"))
    }

    @Suppress("LongParameterList")
    private fun evaluate(
        compiledRules: List<CompiledRule>,
        documentSchema: FieldSchema,
        memberSchema: FieldSchema?,
        scope: String,
        factMap: Map<String, Any?>,
        targetAsts: List<RuleAst>,
    ): SimulationOutcome {
        val ctx = MapRuleContext(map = factMap)
        val engine = RuleEngine(compiledRules = compiledRules)

        val evalResult = runCatching {
            if (memberSchema != null) {
                ScopedEvaluation.evaluate(
                    engine = engine,
                    document = ctx,
                    schema = documentSchema,
                    memberSchema = memberSchema,
                    scope = scope,
                    includeTrace = true,
                )
            } else {
                engine.evaluate(
                    prepared = PreparedRuleContext.prepare(ctx = ctx, schema = documentSchema),
                    includeTrace = true,
                )
            }
        }.getOrElse { e ->
            stop(SimulationOutcome.ValidationFailed(reason = "Evaluation error: ${e.message}"))
        }

        val ruleIds = targetAsts.map { ast -> ast.id }
        if (evalResult.members.isNotEmpty()) {
            return SimulationOutcome.Completed(
                ruleResults = evalResult.members.flatMap { member ->
                    resultsFor(ruleIds = ruleIds, result = member.result, member = member.key)
                },
            )
        }
        return SimulationOutcome.Completed(
            ruleResults = resultsFor(ruleIds = ruleIds, result = evalResult, member = null),
        )
    }

    /** One row per rule, for a whole-document run or for a single member of a scoped one. */
    private fun resultsFor(
        ruleIds: List<String>,
        result: ruleengine.core.domain.dto.EvaluationResult,
        member: String?,
    ): List<RuleResult> {
        // Report what every evaluated rule decided. A trace we cannot read must not cost the
        // caller its result.
        val tracesByRule = runCatching { traceTreesByRule(trace = result.trace) }.getOrElse { emptyMap() }
        return buildRuleResults(
            ruleIds = ruleIds,
            matches = result.matches,
            tracesByRule = tracesByRule,
            stoppedBy = result.stoppedBy,
        ).map { ruleResult -> ruleResult.copy(scopeMember = member) }
    }

    /**
     * Ends the pipeline with [outcome].
     *
     * Carries no stack trace: it is a result, not a fault, and it is caught one frame up.
     */
    private class PipelineStopped(val outcome: SimulationOutcome) :
        Exception(null, null, false, false)

    private fun stop(outcome: SimulationOutcome): Nothing = throw PipelineStopped(outcome = outcome)

    // ── result helpers ────────────────────────────────────────────────────────

    /**
     * One [RuleResult] per evaluated rule, driven by the compiled rule ids rather than by the engine's
     * match list: only matched rules appear in `matches`, so iterating that would silently drop every
     * rule that legitimately did not fire — most of a rule set built from mutually exclusive pairs.
     * Iterating [ruleIds] also keeps DSL declaration order.
     */
    private fun buildRuleResults(
        ruleIds: List<String>,
        matches: List<RuleMatch>,
        tracesByRule: Map<String, TraceNode>,
        stoppedBy: String?,
    ): List<RuleResult> {
        val matchesById = matches.associateBy { match -> match.ruleId }
        // Everything after the halting rule was never reached. Derived from position rather than from an
        // absent trace, because a rule that ran and short-circuited before its first condition also
        // records nothing — the two are indistinguishable from the trace alone.
        val stoppedAt = stoppedBy?.let { id -> ruleIds.indexOf(element = id) } ?: -1
        return ruleIds.mapIndexed { index, id ->
            val match = matchesById[id]
            val tree = tracesByRule[id]
            RuleResult(
                notEvaluated = stoppedAt >= 0 && index > stoppedAt,
                ruleId = id,
                // A rule in `matches` produced output, which is not the same as its condition having
                // held: an `else` branch produces output precisely when it did not.
                matched = match?.branch == RuleBranch.THEN,
                branch = match?.branch,
                actions = match?.actions?.map { action -> formatAction(action = action) }.orEmpty(),
                assignments = match?.assignments?.map { (name, value) -> "$name = $value" }.orEmpty(),
                traceRows = tree?.let { root -> conditionRows(node = root) }.orEmpty(),
                traceTree = tree,
            )
        }
    }

    private fun formatAction(action: RuleAction): String {
        val args = action.arguments.joinToString(separator = ", ") { argument -> "\"$argument\"" }
        return "${action.name} $args".trim()
    }

    // ── trace helpers ─────────────────────────────────────────────────────────

    /**
     * Each rule's decision tree, keyed by the rule that produced it.
     *
     * The engine already wraps each rule in a [NodeType.RULE] node carrying its `ruleId`, so the
     * attribution is present in the tree. Flattening the whole tree into a single list threw it away,
     * which made an unrelated rule's failing condition look like part of the selected rule's verdict.
     */
    private fun traceTreesByRule(trace: Any?): Map<String, TraceNode> {
        val root = (trace as? DecisionTree)?.root ?: return emptyMap()
        val treesByRule = mutableMapOf<String, TraceNode>()
        collectRuleNodes(node = root, treesByRule = treesByRule)
        return treesByRule
    }

    private fun collectRuleNodes(node: DecisionNode, treesByRule: MutableMap<String, TraceNode>) {
        val ruleId = node.ruleId
        if (node.type == NodeType.RULE && ruleId != null) {
            treesByRule[ruleId] = toTraceNode(node = node)
            return
        }
        node.children.forEach { child -> collectRuleNodes(node = child, treesByRule = treesByRule) }
    }

    private fun toTraceNode(node: DecisionNode): TraceNode {
        return TraceNode(
            type = node.type,
            label = buildLabel(node = node),
            result = node.result,
            actual = node.actual?.toString(),
            children = node.children.map { child -> toTraceNode(node = child) },
        )
    }

    private fun buildLabel(node: DecisionNode): String {
        return when (node.type) {
            NodeType.CONDITION -> {
                val field = node.field ?: "?"
                val op = node.operator ?: "?"
                val expected = node.expected?.toString() ?: "?"
                "$field $op $expected"
            }

            NodeType.RULE -> node.ruleId.orEmpty()
            // A logical node's meaning is its type; giving it "? ? ?" would only look like missing data.
            else -> ""
        }
    }

    /**
     * The flat condition list the results view shows, derived from the tree so the two can never
     * disagree about what was evaluated.
     */
    private fun conditionRows(node: TraceNode): List<TraceRow> {
        if (node.type == NodeType.CONDITION) {
            return listOf(TraceRow(label = node.label, result = node.result, actual = node.actual))
        }
        return node.children.flatMap { child -> conditionRows(node = child) }
    }
}
