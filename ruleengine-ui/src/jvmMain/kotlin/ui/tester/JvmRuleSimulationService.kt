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
        val tracesByRule = runCatching { traceRowsByRule(trace = evalResult.trace) }
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
        tracesByRule: Map<String, List<TraceRow>>,
    ): List<RuleResult> {
        val matchesById = matches.associateBy { match -> match.ruleId }
        return ruleIds.map { id ->
            val match = matchesById[id]
            RuleResult(
                ruleId = id,
                matched = match != null,
                actions = match?.actions?.map { action -> formatAction(action = action) }.orEmpty(),
                traceRows = tracesByRule[id].orEmpty(),
            )
        }
    }

    private fun formatAction(action: RuleAction): String {
        val args = action.arguments.joinToString(separator = ", ") { argument -> "\"$argument\"" }
        return "${action.name} $args".trim()
    }

    // ── trace helpers ─────────────────────────────────────────────────────────

    /**
     * Condition rows grouped by the rule that produced them.
     *
     * The engine already wraps each rule in a [NodeType.RULE] node carrying its `ruleId`, so the
     * attribution is present in the tree. Flattening the whole tree into a single list threw it away,
     * which made an unrelated rule's failing condition look like part of the selected rule's verdict.
     */
    private fun traceRowsByRule(trace: Any?): Map<String, List<TraceRow>> {
        val root = (trace as? DecisionTree)?.root ?: return emptyMap()
        val rowsByRule = mutableMapOf<String, MutableList<TraceRow>>()
        collectRuleNodes(node = root, rowsByRule = rowsByRule)
        return rowsByRule
    }

    private fun collectRuleNodes(
        node: DecisionNode,
        rowsByRule: MutableMap<String, MutableList<TraceRow>>,
    ) {
        val ruleId = node.ruleId
        if (node.type == NodeType.RULE && ruleId != null) {
            val rows = rowsByRule.getOrPut(key = ruleId) { mutableListOf() }
            collectConditionRows(node = node, rows = rows)
            return
        }
        node.children.forEach { child -> collectRuleNodes(node = child, rowsByRule = rowsByRule) }
    }

    private fun collectConditionRows(node: DecisionNode, rows: MutableList<TraceRow>) {
        when (node.type) {
            NodeType.CONDITION -> {
                val label = buildConditionLabel(node)
                rows += TraceRow(label = label, result = node.result)
            }
            else -> node.children.forEach { collectConditionRows(node = it, rows = rows) }
        }
    }

    private fun buildConditionLabel(node: DecisionNode): String {
        val field = node.field ?: "?"
        val op = node.operator ?: "?"
        val expected = node.expected?.toString() ?: "?"
        return "$field $op $expected"
    }
}
