package ui.diagrams

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ruleengine.core.analysis.FieldUsage
import ruleengine.core.domain.dto.FieldSchema
import ruleengine.dsl.ast.RuleAst
import ui.diagrams.model.OutcomeKey
import ui.diagrams.model.SchemaLeaves

/**
 * What a rule depends on and what it produces, in three columns: schema field, rule, outcome family.
 *
 * The engine keeps no dependency index, so the edges are derived by walking the AST — see
 * [FieldUsage], which also resolves paths written inside a collection filter against the collection
 * they filter.
 *
 * Two questions this answers that no other view can. *What breaks if I change this field* — select a
 * field and everything not on a path through it dims. And *what is this schema declaring for nobody*
 * — a leaf no rule reads is drawn dashed and greyed, which is invisible everywhere else because every
 * other view starts from the rules.
 *
 * ponytail: the columns are linked by selection highlighting rather than drawn edges. Curves would
 * need every node measured through `onGloballyPositioned` and a `Canvas` overlay; if the crossings
 * turn out to matter more than the filtering does, that is the upgrade.
 */
@Composable
internal fun FieldFlowDiagram(rules: List<RuleAst>, schema: FieldSchema?, entryWide: Boolean = true) {
    val model = remember(rules, schema) { buildFlowModel(rules = rules, schema = schema) }
    var selected by remember(model) { mutableStateOf<String?>(value = null) }

    if (model.rules.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(all = 48.dp), contentAlignment = Alignment.Center) {
            DiagramPlaceholderContent(
                headline = "No rules to trace to fields",
                hint = "Open a rule file, or pick \"All files\" to see the whole entry",
            )
        }
        return
    }

    val lit = model.connectedTo(nodeId = selected)

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(space = 10.dp)) {
        DiagramNote(
            text = if (selected == null) {
                "Select a field, rule or outcome to isolate everything on a path through it."
            } else {
                "Showing everything connected to \"$selected\" — select it again to clear."
            },
        )
        if (!entryWide) {
            DiagramNote(
                text = "Scoped to the open file. A field marked \"not read here\" may still be read " +
                    "by another file in this entry — pick \"All files\" in the ☰ menu to find dead fields.",
                color = LabelOp,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(space = 16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            val onSelect: (String) -> Unit = { next -> selected = toggle(current = selected, next = next) }
            FieldColumn(
                model = model,
                lit = lit,
                onSelect = onSelect,
                entryWide = entryWide,
                modifier = Modifier.weight(weight = 1f),
            )
            RuleColumn(model = model, lit = lit, onSelect = onSelect, modifier = Modifier.weight(weight = 1f))
            OutcomeColumn(model = model, lit = lit, onSelect = onSelect, modifier = Modifier.weight(weight = 1f))
        }
    }
}

@Composable
private fun FieldColumn(
    model: FlowModel,
    lit: Set<String>?,
    onSelect: (String) -> Unit,
    entryWide: Boolean,
    modifier: Modifier = Modifier,
) {
    FlowColumn(title = "SCHEMA FIELD · ${model.fields.size}", modifier = modifier) {
        model.fields.forEach { field ->
            val readers = model.rulesByField[field.path].orEmpty()
            val unread = readers.isEmpty()
            FlowNode(
                label = field.path,
                // "unused" is a claim about the whole entry. Scoped to one file, all that is known
                // is that this file does not read it.
                detail = when {
                    !unread -> "→ ${readers.size}"
                    entryWide -> "unused"
                    else -> "not read here"
                },
                labelColor = if (unread) TextDesc else LabelField,
                onClick = { onSelect(field.path) },
                detailColor = if (unread && entryWide) LabelNot else TextDesc,
                muted = unread,
                dimmed = lit != null && field.path !in lit,
            )
        }
    }
}

@Composable
private fun RuleColumn(
    model: FlowModel,
    lit: Set<String>?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowColumn(title = "RULE · ${model.rules.size}", modifier = modifier) {
        model.rules.forEach { rule ->
            val reads = model.fieldsByRule[rule.id].orEmpty()
            val emits = model.familiesByRule[rule.id].orEmpty()
            FlowNode(
                label = rule.id,
                detail = "${reads.size} → ${emits.size}",
                labelColor = LabelRule,
                onClick = { onSelect(rule.id) },
                dimmed = lit != null && rule.id !in lit,
            )
        }
    }
}

@Composable
private fun OutcomeColumn(
    model: FlowModel,
    lit: Set<String>?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowColumn(title = "OUTCOME · ${model.families.size}", modifier = modifier) {
        model.families.forEach { family ->
            val values = model.valuesByFamily[family].orEmpty()
            FlowNode(
                label = family,
                detail = "${values.size} value${plural(count = values.size)}",
                labelColor = LabelActionName,
                onClick = { onSelect(family) },
                dimmed = lit != null && family !in lit,
            )
        }
    }
}

@Composable
private fun FlowColumn(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(space = 5.dp)) {
        DiagramEyebrow(text = title, modifier = Modifier.padding(bottom = 3.dp))
        content()
    }
}

@Suppress("LongParameterList")
@Composable
private fun FlowNode(
    label: String,
    detail: String,
    labelColor: Color,
    onClick: () -> Unit,
    detailColor: Color = TextDesc,
    muted: Boolean = false,
    dimmed: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha = if (dimmed) DIMMED_ALPHA else 1f)
            .clip(shape = RoundedCornerShape(size = 5.dp))
            .background(color = NodeBgCondition)
            .border(
                width = 1.dp,
                // A dashed stroke would need a Canvas; a muted border says "declared but unread"
                // clearly enough next to the greyed label and the red "unused" tag.
                color = if (muted) BorderCondition.copy(alpha = 0.5f) else BorderCondition,
                shape = RoundedCornerShape(size = 5.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DiagramIdentifier(
            text = label,
            color = labelColor,
            fontSize = 11,
            weight = FontWeight.Normal,
            modifier = Modifier.weight(weight = 1f),
        )
        DiagramNote(text = detail, color = detailColor)
    }
}

private const val DIMMED_ALPHA = 0.25f

private fun toggle(current: String?, next: String): String? {
    if (current == next) {
        return null
    }
    return next
}

/**
 * The bipartite edges, precomputed once per rule set.
 *
 * Fields are the schema's leaves, so a declared-but-unread field keeps its row. A path a rule reads
 * that is not a schema leaf — a bare collection such as `parcels` in `count(parcels[...])` — is
 * dropped, because it is a step on the way to a value rather than a value.
 */
private class FlowModel(
    val fields: List<FieldNode>,
    val rules: List<RuleAst>,
    val families: List<String>,
    val rulesByField: Map<String, List<String>>,
    val fieldsByRule: Map<String, List<String>>,
    val familiesByRule: Map<String, List<String>>,
    val valuesByFamily: Map<String, Set<String>>,
) {
    /** The node itself plus everything reachable from it in either direction, or null for no selection. */
    fun connectedTo(nodeId: String?): Set<String>? {
        if (nodeId == null) {
            return null
        }
        val reachedRules = when {
            rules.any { rule -> rule.id == nodeId } -> listOf(nodeId)
            else -> rules.map { rule -> rule.id }.filter { ruleId ->
                nodeId in fieldsByRule[ruleId].orEmpty() || nodeId in familiesByRule[ruleId].orEmpty()
            }
        }
        val lit = mutableSetOf(nodeId)
        reachedRules.forEach { ruleId ->
            lit += ruleId
            lit += fieldsByRule[ruleId].orEmpty()
            lit += familiesByRule[ruleId].orEmpty()
        }
        return lit
    }
}

private class FieldNode(val path: String)

private fun buildFlowModel(rules: List<RuleAst>, schema: FieldSchema?): FlowModel {
    val referencedByRule = rules.associate { rule -> rule.id to FieldUsage.fieldsOf(rule = rule) }
    val schemaPaths = schema?.let { loaded -> SchemaLeaves.pathsOf(schema = loaded) }.orEmpty()

    // Without a schema there are no declared leaves to compare against, so fall back to the paths the
    // rules mention — the view then cannot report unused fields, but it still shows the flow.
    val fieldPaths = schemaPaths.ifEmpty {
        referencedByRule.values.flatten().distinct().sorted()
    }

    val fieldsByRule = referencedByRule.mapValues { (_, referenced) ->
        fieldPaths.filter { path -> path in referenced }
    }
    val rulesByField = mutableMapOf<String, MutableList<String>>()
    fieldsByRule.forEach { (ruleId, paths) ->
        paths.forEach { path -> rulesByField.getOrPut(path) { mutableListOf() } += ruleId }
    }

    val familiesByRule = mutableMapOf<String, MutableList<String>>()
    val valuesByFamily = mutableMapOf<String, MutableSet<String>>()
    val families = mutableListOf<String>()
    rules.forEach { rule ->
        rule.actions.forEach { action ->
            val family = OutcomeKey.displayFamily(action = action) ?: return@forEach
            val key = OutcomeKey.staticOutputKey(action = action) ?: return@forEach
            if (family !in families) {
                families += family
            }
            val forRule = familiesByRule.getOrPut(rule.id) { mutableListOf() }
            if (family !in forRule) {
                forRule += family
            }
            valuesByFamily.getOrPut(family) { mutableSetOf() } += key
        }
    }

    return FlowModel(
        fields = fieldPaths.map { path -> FieldNode(path = path) },
        rules = rules,
        families = families,
        rulesByField = rulesByField,
        fieldsByRule = fieldsByRule,
        familiesByRule = familiesByRule,
        valuesByFamily = valuesByFamily,
    )
}
