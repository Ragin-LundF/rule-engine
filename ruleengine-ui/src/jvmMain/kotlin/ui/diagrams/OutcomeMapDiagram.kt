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
import ruleengine.core.domain.dto.RuleBranch
import ruleengine.dsl.ast.ActionAst
import ruleengine.dsl.ast.RuleAst
import ruleengine.dsl.ast.ValueExpressionRenderer
import ui.diagrams.model.OutcomeSource
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
 * **Every branch counts.** An `else` or `not_exists` outcome is as real as a `then` one — the rule
 * produces it, just under a different verdict — so leaving them out made the central claim wrong: a
 * bucket could say "1 rule decides this" while another rule's `else` decided it too. Entries from a
 * non-`then` branch carry its name; an unlabelled entry is a `then`, which is the common case and reads
 * better without a badge on every row.
 *
 * A rule appearing twice in one bucket through two of its own branches is **not** competition: exactly
 * one branch of a rule ever runs. So the counts here are over distinct rules, not over entries.
 */
@Composable
internal fun OutcomeMapDiagram(rules: List<RuleAst>) {
    val families = groupByFamily(rules = rules)
    val ungrouped = rules.filter { rule -> outcomeSourcesOf(rule = rule).none { source -> source.hasKey() } }

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
private fun FamilySection(family: String, buckets: Map<String, List<OutcomeSource>>) {
    val ruleCount = buckets.values.flatten().distinctBy { source -> source.rule.id }.size
    // Every bucket holding one rule while the family holds several is exactly the shape that looks
    // like competition and is not, so it gets called out rather than left for the reader to infer.
    // Counted over distinct rules: a rule reaching one bucket from two of its own branches decides that
    // value once, because only one branch runs.
    val exclusive = ruleCount > 1 && buckets.values.all { bucket -> bucket.distinctRuleCount() == 1 }

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
        buckets.forEach { (key, bucketSources) ->
            BucketRow(bucketKey = key, sources = bucketSources)
        }
    }
}

@Composable
private fun BucketRow(bucketKey: String, sources: List<OutcomeSource>) {
    val ruleCount = sources.distinctRuleCount()
    val competing = ruleCount > 1

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
                    "$ruleCount rules can decide this"
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
            sources.forEach { source ->
                Column(verticalArrangement = Arrangement.spacedBy(space = 2.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DiagramIdentifier(text = source.rule.id, color = LabelRule)
                        // Only for a branch that is not the obvious one. A badge on every row would
                        // cost more attention than it pays back, since most outcomes are `then`.
                        branchLabel(branch = source.branch)?.let { label ->
                            DiagramChip(text = label, textColor = LabelOp)
                        }
                    }
                    DiagramConditionLine(
                        text = ValueExpressionRenderer.renderExpression(expr = source.rule.condition),
                    )
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
 * Family to bucket to the rule-and-branch pairs that produce it, keeping insertion order so the view
 * follows declaration order rather than an alphabetical shuffle.
 *
 * A rule with several actions appears under each of them, because it can decide each of those outputs —
 * and the same holds across its branches, in branch order, so `then` entries read before the `else` and
 * `not_exists` ones they alternate with.
 *
 * `internal` rather than private so a test can assert on the grouping itself. What this function claims
 * — "these N rules can decide this value" — is the whole content of the view, and a test that rebuilds
 * the grouping by hand cannot catch it going wrong.
 */
internal fun groupByFamily(rules: List<RuleAst>): Map<String, Map<String, List<OutcomeSource>>> {
    val families = linkedMapOf<String, LinkedHashMap<String, MutableList<OutcomeSource>>>()
    rules.forEach { rule ->
        outcomeSourcesOf(rule = rule).forEach { source ->
            source.actions().forEach { action ->
                val key = OutcomeKey.staticOutputKey(action = action) ?: return@forEach
                val family = OutcomeKey.displayFamily(action = action) ?: return@forEach
                families.getOrPut(family) { linkedMapOf() }.getOrPut(key) { mutableListOf() } += source
            }
        }
    }
    return families
}

/**
 * Every branch of [rule] that declares actions, in the order the DSL writes them.
 *
 * A branch with no actions contributes nothing and is skipped, so a rule with only a `then` block yields
 * exactly what it did before this view learned about the others.
 */
internal fun outcomeSourcesOf(rule: RuleAst): List<OutcomeSource> {
    return RuleBranch.entries
        .map { branch -> OutcomeSource(rule = rule, branch = branch) }
        .filter { source -> source.actions().isNotEmpty() }
}

internal fun OutcomeSource.actions(): List<ActionAst> {
    return when (branch) {
        RuleBranch.THEN -> rule.actions
        RuleBranch.ELSE -> rule.elseActions
        RuleBranch.NOT_EXISTS -> rule.notExistsActions
    }
}

internal fun OutcomeSource.hasKey(): Boolean {
    return actions().any { action -> OutcomeKey.staticOutputKey(action = action) != null }
}

/** Distinct rules, not entries: two branches of one rule are alternatives, not competitors. */
internal fun List<OutcomeSource>.distinctRuleCount(): Int {
    return distinctBy { source -> source.rule.id }.size
}

/** The badge text for a branch, or null for `then`, which needs none. */
private fun branchLabel(branch: RuleBranch): String? {
    return when (branch) {
        RuleBranch.THEN -> null
        RuleBranch.ELSE -> "else"
        RuleBranch.NOT_EXISTS -> "not_exists"
    }
}
