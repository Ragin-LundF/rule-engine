package ui.tester

import ruleengine.compiler.Compiler
import ruleengine.compiler.Validator
import ruleengine.core.domain.dto.RuleAction
import ruleengine.core.domain.dto.RuleMatch
import ruleengine.core.errors.Severity
import ruleengine.dsl.parser.Parser
import ruleengine.evaluator.RuleEngine
import ruleengine.evaluator.context.MapRuleContext
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.trace.dto.DecisionNode
import ruleengine.evaluator.trace.dto.DecisionTree
import ruleengine.evaluator.trace.dto.NodeType
import ruleengine.jackson.JacksonUtil
import ruleengine.schema.ActionSchemaLoader
import ruleengine.schema.FieldSchemaLoader

/**
 * JVM implementation of [RuleSimulationService].
 *
 * Runs the full core pipeline:
 *   JSON parse → DSL parse → validate → compile → PreparedRuleContext → RuleEngine.evaluate()
 *
 * Never throws — all errors are captured and returned as [SimulationOutcome] variants.
 */
class JvmRuleSimulationService : RuleSimulationService {

    @Suppress("ReturnCount")
    override fun simulate(
        schemaText: String,
        actionsText: String,
        ruleText: String,
        ruleId: String,
        inputJson: String,
    ): SimulationResult {
        // 1. Parse input JSON
        @Suppress("UNCHECKED_CAST")
        val factMap: Map<String, Any?> = runCatching {
            val mapType = JacksonUtil.jsonMapper.typeFactory
                .constructMapType(Map::class.java, String::class.java, Any::class.java)
            JacksonUtil.jsonMapper.readValue(inputJson, mapType) as Map<String, Any?>
        }.getOrElse { e ->
            return SimulationResult(
                outcome = SimulationOutcome.InvalidJson(reason = e.message ?: "Invalid JSON"),
            )
        }

        // 2. Load field schema
        val schema = if (schemaText.isBlank()) {
            return SimulationResult(
                outcome = SimulationOutcome.ValidationFailed(reason = "No field schema loaded"),
            )
        } else {
            runCatching {
                FieldSchemaLoader.loadFromString(content = schemaText, nameHint = "schema")
            }.getOrElse { e ->
                return SimulationResult(
                    outcome = SimulationOutcome.ValidationFailed(
                        reason = "Field schema error: ${e.message}",
                    ),
                )
            }
        }

        // 3. Load action schema (optional)
        val actionSchema = if (actionsText.isBlank()) null else {
            runCatching { ActionSchemaLoader.loadFromString(content = actionsText) }.getOrNull()
        }

        // 4. Parse rule DSL
        val asts = runCatching {
            Parser(input = ruleText).parseRules()
        }.getOrElse { e ->
            return SimulationResult(
                outcome = SimulationOutcome.ValidationFailed(reason = "Parse error: ${e.message}"),
            )
        }

        if (asts.isEmpty()) {
            return SimulationResult(
                outcome = SimulationOutcome.ValidationFailed(reason = "No rules found in rule text"),
            )
        }

        // 5. Semantic validation
        val validationResult = runCatching {
            Validator.validate(asts = asts, schema = schema, actions = actionSchema)
        }.getOrElse { e ->
            return SimulationResult(
                outcome = SimulationOutcome.ValidationFailed(reason = "Validation error: ${e.message}"),
            )
        }

        val errors = validationResult.diagnostics.filter { it.severity == Severity.ERROR }
        if (errors.isNotEmpty()) {
            return SimulationResult(
                outcome = SimulationOutcome.ValidationFailed(
                    reason = errors.joinToString("; ") { it.message },
                ),
            )
        }

        // 6. Compile — filter to the target rule only (or all if ruleId is blank)
        val targetAsts = if (ruleId.isBlank()) asts else asts.filter { it.id == ruleId }
        if (targetAsts.isEmpty()) {
            return SimulationResult(
                outcome = SimulationOutcome.ValidationFailed(
                    reason = "Rule \"$ruleId\" not found",
                ),
            )
        }

        val compiledRules = runCatching {
            Compiler.compileRules(asts = targetAsts, schema = schema)
        }.getOrElse { e ->
            return SimulationResult(
                outcome = SimulationOutcome.ValidationFailed(reason = "Compile error: ${e.message}"),
            )
        }

        // 7. Prepare context and evaluate
        val ctx = MapRuleContext(map = factMap)
        val prepared = PreparedRuleContext.prepare(ctx = ctx, schema = schema)
        val engine = RuleEngine(compiledRules = compiledRules)

        val evalResult = runCatching {
            engine.evaluate(prepared = prepared, includeTrace = true)
        }.getOrElse { e ->
            return SimulationResult(
                outcome = SimulationOutcome.ValidationFailed(reason = "Evaluation error: ${e.message}"),
            )
        }

        // 8. Report what every evaluated rule decided.
        //    A trace we cannot read must not cost the caller its result.
        val tracesByRule = runCatching { traceTreesByRule(trace = evalResult.trace) }
            .getOrElse { emptyMap() }

        return SimulationResult(
            outcome = SimulationOutcome.Completed(
                ruleResults = buildRuleResults(
                    ruleIds = targetAsts.map { ast -> ast.id },
                    matches = evalResult.matches,
                    tracesByRule = tracesByRule,
                ),
            ),
        )
    }

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
    ): List<RuleResult> {
        val matchesById = matches.associateBy { match -> match.ruleId }
        return ruleIds.map { id ->
            val match = matchesById[id]
            val tree = tracesByRule[id]
            RuleResult(
                ruleId = id,
                matched = match != null,
                actions = match?.actions?.map { action -> formatAction(action = action) }.orEmpty(),
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
