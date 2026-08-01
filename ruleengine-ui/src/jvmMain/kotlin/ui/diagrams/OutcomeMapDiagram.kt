package ui.diagrams

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ruleengine.dsl.ast.RuleAst
import ruleengine.dsl.ast.ValueExpressionRenderer
import ui.diagrams.model.OutcomeKey
import ui.util.Plurals

/**
 * Rules grouped by what they produce instead of by the file they live in.
 *
 * Two layers are drawn and they are not the same thing, which is the point of the view:
 *
 * - The **family** heading is a reading aid with no runtime meaning — action name plus the
 *   `:`-separated prefix of the value, so `assessment:transit` reads as one decision.
 * - Each **bucket** row is a real `RuleEngine.staticOutputKeys` key, which uses the *whole* first
 *   argument. `assessment:transit:green` and `assessment:transit:red` are therefore separate buckets
 *   and those rules never compete, however much the family heading suggests they do.
 *
 * That distinction is worth the extra layer: with `shortCircuitByOutput` enabled the engine stops a
 * bucket at its first match, so a bucket holding one rule means the flag does nothing for it. On a
 * rule set where every rule emits a distinct value — the common case — the flag is inert, and the
 * only way to see that is to show the bucket sizes.
 */
@Composable
internal fun OutcomeMapDiagram(rules: List<RuleAst>) {
    val families = groupByFamily(rules = rules)
    val ungrouped = rules.filter { rule -> rule.actions.none { action -> OutcomeKey.staticOutputKey(action) != null } }

    if (families.isEmpty() && ungrouped.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(all = 48.dp), contentAlignment = Alignment.Center) {
            DiagramPlaceholderContent(
                headline = "No outcomes to group",
                hint = "Rules need at least one action with a literal first argument",
            )
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(space = 20.dp),
    ) {
        families.forEach { (family, buckets) ->
            FamilySection(family = family, buckets = buckets)
        }
        if (ungrouped.isNotEmpty()) {
            UngroupedSection(rules = ungrouped)
        }
    }
}

@Composable
private fun FamilySection(family: String, buckets: Map<String, List<RuleAst>>) {
    val ruleCount = buckets.values.flatten().distinctBy { rule -> rule.id }.size
    // Every bucket holding one rule while the family holds several is exactly the shape that looks
    // like competition and is not, so it gets called out rather than left for the reader to infer.
    val exclusive = ruleCount > 1 && buckets.values.all { bucket -> bucket.size == 1 }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(space = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(space = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DiagramIdentifier(text = family, color = LabelActions, fontSize = 13, weight = FontWeight.SemiBold)
            DiagramNote(
                text = "${buckets.size} bucket${Plurals.suffix(count = buckets.size)} · " +
                    "$ruleCount rule${Plurals.suffix(count = ruleCount)}",
            )
            if (exclusive) {
                DiagramChip(
                    text = "mutually exclusive — separate buckets, no short-circuit",
                    textColor = LabelOp,
                )
            }
        }
        buckets.forEach { (key, bucketRules) ->
            BucketRow(bucketKey = key, rules = bucketRules)
        }
    }
}

@Composable
private fun BucketRow(bucketKey: String, rules: List<RuleAst>) {
    val competing = rules.size > 1

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape = RoundedCornerShape(size = 6.dp))
            .background(color = NodeBgCondition)
            .border(width = 1.dp, color = BorderCondition, shape = RoundedCornerShape(size = 6.dp))
            .padding(all = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.width(width = 260.dp),
            verticalArrangement = Arrangement.spacedBy(space = 3.dp),
        ) {
            DiagramIdentifier(text = bucketKey, color = LabelValue)
            DiagramNote(
                text = if (competing) {
                    "${rules.size} rules compete · first match wins"
                } else {
                    "1 rule · no short-circuit effect"
                },
                color = if (competing) LabelOp else TextDesc.copy(alpha = 0.7f),
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(space = 5.dp),
        ) {
            rules.forEach { rule ->
                Column(verticalArrangement = Arrangement.spacedBy(space = 2.dp)) {
                    DiagramIdentifier(text = rule.id, color = LabelRule)
                    DiagramConditionLine(text = ValueExpressionRenderer.renderExpression(expr = rule.condition))
                }
            }
        }
    }
}

/**
 * Rules the engine can never bucket. It evaluates these unconditionally even with
 * `shortCircuitByOutput` on, so they belong in the view rather than being dropped as uninteresting.
 */
@Composable
private fun UngroupedSection(rules: List<RuleAst>) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(space = 8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(space = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DiagramIdentifier(text = "always evaluated", color = TextDesc, fontSize = 13, weight = FontWeight.SemiBold)
            DiagramNote(text = "no static output — never grouped, never short-circuited")
        }
        rules.forEach { rule ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape = RoundedCornerShape(size = 6.dp))
                    .background(color = NodeBgCondition)
                    .border(width = 1.dp, color = BorderCondition, shape = RoundedCornerShape(size = 6.dp))
                    .padding(all = 10.dp),
                verticalArrangement = Arrangement.spacedBy(space = 2.dp),
            ) {
                DiagramIdentifier(text = rule.id, color = LabelRule)
                DiagramConditionLine(text = ValueExpressionRenderer.renderExpression(expr = rule.condition))
            }
        }
    }
}

/**
 * Family to bucket to rules, keeping insertion order so the view follows declaration order rather
 * than an alphabetical shuffle. A rule with several actions appears under each of them, which is what
 * `RuleEngine.staticOutputKeys` does too.
 */
private fun groupByFamily(rules: List<RuleAst>): Map<String, Map<String, List<RuleAst>>> {
    val families = linkedMapOf<String, LinkedHashMap<String, MutableList<RuleAst>>>()
    rules.forEach { rule ->
        rule.actions.forEach { action ->
            val key = OutcomeKey.staticOutputKey(action = action) ?: return@forEach
            val family = OutcomeKey.displayFamily(action = action) ?: return@forEach
            families.getOrPut(family) { linkedMapOf() }.getOrPut(key) { mutableListOf() } += rule
        }
    }
    return families
}
