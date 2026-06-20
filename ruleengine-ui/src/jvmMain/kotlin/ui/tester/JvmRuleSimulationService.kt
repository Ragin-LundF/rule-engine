package ui.tester

import ruleengine.jackson.JacksonUtil
import ruleengine.compiler.Compiler
import ruleengine.compiler.Validator
import ruleengine.core.errors.Severity
import ruleengine.dsl.parser.Parser
import ruleengine.evaluator.RuleEngine
import ruleengine.evaluator.context.MapRuleContext
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.trace.dto.DecisionNode
import ruleengine.evaluator.trace.dto.NodeType
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
        val mapType = JacksonUtil.jsonMapper.typeFactory
            .constructMapType(Map::class.java, String::class.java, Any::class.java)
        @Suppress("UNCHECKED_CAST")
        val factMap: Map<String, Any?> = runCatching {
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
        val engine = RuleEngine(compiledRules = compiledRules, schema = schema)

        val evalResult = runCatching {
            engine.evaluate(prepared = prepared, includeTrace = true)
        }.getOrElse { e ->
            return SimulationResult(
                outcome = SimulationOutcome.ValidationFailed(reason = "Evaluation error: ${e.message}"),
            )
        }

        // 8. Extract trace rows
        val traceRows = extractTraceRows(evalResult.trace)

        // 9. Build outcome
        val targetRuleId = targetAsts.first().id
        val match = evalResult.matches.firstOrNull { it.ruleId == targetRuleId }
            ?: evalResult.matches.firstOrNull()

        return if (match != null) {
            val actionStrings = match.actions.map { action ->
                val args = action.arguments.joinToString(", ") { "\"$it\"" }
                "${action.name} $args".trim()
            }
            SimulationResult(
                outcome = SimulationOutcome.Matched(actions = actionStrings),
                traceRows = traceRows,
            )
        } else {
            SimulationResult(
                outcome = SimulationOutcome.NotMatched,
                traceRows = traceRows,
            )
        }
    }

    // ── trace helpers ─────────────────────────────────────────────────────────

    private fun extractTraceRows(trace: Any?): List<TraceRow> {
        val tree = trace as? ruleengine.evaluator.trace.dto.DecisionTree ?: return emptyList()
        val root = tree.root ?: return emptyList()
        val rows = mutableListOf<TraceRow>()
        collectConditionRows(node = root, rows = rows)
        return rows
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
