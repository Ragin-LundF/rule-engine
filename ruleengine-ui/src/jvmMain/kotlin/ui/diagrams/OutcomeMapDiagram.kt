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
import ui.diagrams.render.DiagramChip
import ui.diagrams.render.DiagramConditionLine
import ui.diagrams.render.DiagramIdentifier
import ui.diagrams.render.DiagramNote
import ui.diagrams.render.DiagramPlaceholderContent
import ui.util.Plurals

/**
 * Rules grouped by what they produce instead of by the file they live in.
 *
 * Two layers are drawn and they are not the same thing, which is the point of the view:
 *
 * - The **family** heading is a reading aid with no runtime meaning — action name plus the
 *   `:`-separated prefix of the value, so `assessment:transit` reads as one decision.
 * - Each **bucket** row keys on the *whole* first argument. `assessment:transit:green` and
 *   `assessment:transit:red` are therefore separate buckets, and those rules never compete for the
 *   same value however much the family heading suggests they do.
 *
 * That distinction is worth the extra layer, because the question the view answers is "which rules can
 * decide the same thing". A bucket holding several rules is where a record can pick up the same output
 * twice, or where two rules disagree about one decision; a bucket holding one rule cannot. Reading it
 * off the file layout is impossible, which is why this view exists.
 *
 * Note that an `else` outcome is deliberately absent: this groups what a rule produces when it matches.
 * A rule's false branch is shown in the rule tree and in the exported overview.
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
                    text = "mutually exclusive — one rule per value",
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
                    // Every matching rule fires, so a record can pick this value up more than once.
                    "${rules.size} rules can decide this"
                } else {
                    "1 rule decides this"
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
 * Rules whose output cannot be read off the rule text — an extracted or variable argument is only known
 * at evaluation time. They belong in the view rather than being dropped: a reader comparing the buckets
 * against the rule set would otherwise find rules missing with no explanation.
 */
@Composable
private fun UngroupedSection(rules: List<RuleAst>) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(space = 8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(space = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DiagramIdentifier(text = "not groupable", color = TextDesc, fontSize = 13, weight = FontWeight.SemiBold)
            DiagramNote(text = "output only known at evaluation time — cannot be grouped")
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
 * than an alphabetical shuffle. A rule with several actions appears under each of them, because it can
 * decide each of those outputs.
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
