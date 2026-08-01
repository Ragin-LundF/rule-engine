package ui.diagrams

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import ruleengine.evaluator.trace.dto.NodeType
import ui.tester.RuleResult
import ui.tester.TraceNode

/**
 * A run's decision trees, drawn with the same nesting as the condition diagram.
 *
 * The results list already shows a flat row per condition, which answers *what held*. It cannot show
 * *where evaluation stopped*, because that is a property of the shape: `AndExpression` sorts its
 * children by [ruleengine.evaluator.compiled.EvaluationCost] and returns on the first false without
 * ever calling the trace collector for the rest.
 *
 * So a node that was not evaluated is **absent** here, not greyed — there is nothing recorded to grey.
 * A false `AND` is therefore annotated with where it stopped, which is the honest version of the
 * three-state colouring: it says evaluation ended here without claiming to know what the untaken
 * branch would have decided.
 *
 * For the same reason the drawn order of an `AND`'s children is evaluation order, not source order.
 */
@Composable
internal fun TraceDiagram(results: List<RuleResult>) {
    val traced = results.filter { result -> result.traceTree != null }

    if (traced.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(all = 48.dp), contentAlignment = Alignment.Center) {
            DiagramPlaceholderContent(
                headline = "No run to show",
                hint = "Run the rules against input JSON and the recorded decisions appear here",
            )
        }
        return
    }

    // Matches first: with a rule set of mutually exclusive pairs most rules legitimately do not fire,
    // and scrolling past ten misses to find the one that hit is the wrong default.
    val ordered = traced.sortedByDescending { result -> result.matched }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(space = 14.dp)) {
        TraceSummary(results = traced)
        ordered.forEach { result ->
            TracedRule(result = result)
        }
    }
}

@Composable
private fun TraceSummary(results: List<RuleResult>) {
    val matched = results.count { result -> result.matched }
    val trueNodes = results.sumOf { result -> countLeaves(node = result.traceTree, wanted = true) }
    val falseNodes = results.sumOf { result -> countLeaves(node = result.traceTree, wanted = false) }

    Row(horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
        DiagramChip(text = "$matched matched", textColor = LabelValue, borderColor = BorderOr)
        DiagramChip(text = "${results.size - matched} no match", textColor = LabelNot, borderColor = BorderNot)
        DiagramChip(text = "$trueNodes condition${plural(count = trueNodes)} true")
        DiagramChip(text = "$falseNodes condition${plural(count = falseNodes)} false")
    }
}

@Composable
private fun TracedRule(result: RuleResult) {
    val tree = result.traceTree ?: return

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .background(color = if (result.matched) NodeBgOr else NodeBgRule)
                .border(
                    width = 1.dp,
                    color = if (result.matched) BorderOr else BorderRule,
                    shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                )
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(space = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DiagramEyebrow(text = "RULE")
            DiagramIdentifier(text = result.ruleId, color = LabelRule, weight = FontWeight.SemiBold)
            Box(modifier = Modifier.weight(weight = 1f))
            DiagramIdentifier(
                text = if (result.matched) "✓ MATCH" else "✗ NO MATCH",
                color = if (result.matched) LabelValue else LabelNot,
                fontSize = 11,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(color = DiagramBg)
                .border(width = 1.dp, color = BorderCondition, shape = RoundedCornerShape(size = 0.dp))
                .padding(all = 12.dp),
            verticalArrangement = Arrangement.spacedBy(space = 6.dp),
        ) {
            tree.children.forEach { child -> TraceNodeView(node = child) }
            if (tree.children.isEmpty()) {
                DiagramNote(text = "Nothing was evaluated for this rule.")
            }
        }
    }
}

@Composable
private fun TraceNodeView(node: TraceNode) {
    when (node.type) {
        NodeType.CONDITION -> TraceLeafRow(node = node)
        NodeType.AND ->
            TraceLogicBox(node = node, label = "AND", bg = NodeBgAnd, labelColor = LabelAnd)

        NodeType.OR ->
            TraceLogicBox(node = node, label = "OR", bg = NodeBgOr, labelColor = LabelOr)

        NodeType.NOT ->
            TraceLogicBox(node = node, label = "NOT", bg = NodeBgNot, labelColor = LabelNot)

        // A rule or evaluation node never appears below a rule's own root.
        NodeType.RULE, NodeType.EVALUATION -> node.children.forEach { child -> TraceNodeView(node = child) }
    }
}

/**
 * The border colour comes from the recorded result rather than from the node kind: in a trace, whether
 * a branch held is the thing worth seeing at a glance, and the kind is already named by the pill.
 */
@Composable
private fun TraceLogicBox(node: TraceNode, label: String, bg: Color, labelColor: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape = RoundedCornerShape(size = 6.dp))
            .background(color = bg)
            .border(width = 1.dp, color = resultColor(result = node.result), shape = RoundedCornerShape(size = 6.dp))
            .padding(all = 10.dp),
        verticalArrangement = Arrangement.spacedBy(space = 6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DiagramEyebrow(text = label, color = labelColor)
            DiagramNote(
                text = if (node.result) "held" else "did not hold",
                color = resultColor(result = node.result),
            )
        }
        node.children.forEach { child -> TraceNodeView(node = child) }
        if (!node.result && node.type == NodeType.AND) {
            DiagramNote(
                text = "⤵ stopped here — any remaining conditions were never evaluated, " +
                    "so they are not recorded. Children are shown in evaluation order, which " +
                    "EvaluationCost may have reordered.",
                color = LabelOp,
            )
        }
        if (node.children.isEmpty()) {
            DiagramNote(text = "no recorded children", color = TextDesc.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun TraceLeafRow(node: TraceNode) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape = RoundedCornerShape(size = 5.dp))
            .background(color = NodeBgCondition)
            .border(width = 1.dp, color = resultColor(result = node.result), shape = RoundedCornerShape(size = 5.dp))
            .padding(horizontal = 11.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DiagramConditionLine(
            text = node.label,
            color = LabelArg,
            modifier = Modifier.weight(weight = 1f),
        )
        val actual = node.actual
        if (actual != null) {
            DiagramNote(text = "actual $actual", color = resultColor(result = node.result))
        }
        DiagramIdentifier(
            text = if (node.result) "✓" else "✗",
            color = resultColor(result = node.result),
            fontSize = 11,
        )
    }
}

private fun resultColor(result: Boolean): Color {
    if (result) {
        return LabelValue
    }
    return LabelNot
}

private fun countLeaves(node: TraceNode?, wanted: Boolean): Int {
    if (node == null) {
        return 0
    }
    if (node.type == NodeType.CONDITION) {
        return if (node.result == wanted) 1 else 0
    }
    return node.children.sumOf { child -> countLeaves(node = child, wanted = wanted) }
}
