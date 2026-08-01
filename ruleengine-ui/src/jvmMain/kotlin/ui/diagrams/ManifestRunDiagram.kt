package ui.diagrams

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ruleengine.dsl.ast.RuleAst
import ruleengine.dsl.ast.ValueExpressionRenderer
import ui.diagrams.model.DiagramData
import ui.diagrams.model.RuleSource
import ui.util.Plurals

/**
 * A manifest entry drawn as the single connected unit the engine actually runs.
 *
 * `RuleEngineBuilder` flattens every rule file an entry lists into one ordered `List<RuleAst>`,
 * validates and compiles that list as one set, and evaluates all of it — the split into `.rule` files
 * exists for the author, not for the engine. Nothing in the editor showed that, so this view puts the
 * whole entry on one spine and demotes the file names to provenance bands.
 *
 * Order is information here, not decoration: manifest file order followed by in-file source order is
 * the evaluation order, and it is the order `matches` comes back in. Hence the running number down
 * the spine, continuous across file boundaries.
 */
@Composable
internal fun ManifestRunDiagram(data: DiagramData) {
    if (data.sources.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(all = 48.dp), contentAlignment = Alignment.Center) {
            DiagramPlaceholderContent(
                headline = "No manifest entry loaded",
                hint = "Load a manifest, then pick \"All files\" in the ☰ menu to see the whole entry",
            )
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(space = 4.dp),
    ) {
        EntryHeaderNode(data = data)
        SpineBody(sources = data.sources)
        ResultCollectorNode(ruleCount = data.sources.sumOf { source -> source.rules.size })
    }
}

@Composable
private fun EntryHeaderNode(data: DiagramData) {
    val fileCount = data.sources.size
    val ruleCount = data.sources.sumOf { source -> source.rules.size }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape = RoundedCornerShape(size = 8.dp))
            .background(color = NodeBgRule)
            .border(width = 1.dp, color = BorderRule, shape = RoundedCornerShape(size = 8.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(space = 6.dp)) {
            DiagramEyebrow(text = "MANIFEST ENTRY")
            DiagramIdentifier(
                text = data.entryId ?: "(no entry selected)",
                color = LabelRule,
                fontSize = 15,
                weight = FontWeight.Bold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(space = 6.dp)) {
                data.schemaPath?.let { path -> DiagramChip(text = path) }
                data.actionsPath?.let { path -> DiagramChip(text = path) }
                DiagramChip(text = "$fileCount file${Plurals.suffix(count = fileCount)} → $ruleCount rules")
            }
        }
    }
}

/**
 * The files and their rules on one continuous spine.
 *
 * The line is drawn behind the whole block rather than per row, because a line that restarted at each
 * file band would say the opposite of what this view is for.
 */
@Composable
private fun SpineBody(sources: List<RuleSource>) {
    var step = 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val x = SPINE_X.toPx()
                drawLine(
                    color = LineColor,
                    start = Offset(x = x, y = 0f),
                    end = Offset(x = x, y = size.height),
                    strokeWidth = ConnectorW.toPx(),
                )
            },
    ) {
        sources.forEach { source ->
            FileBand(source = source)
            source.rules.forEach { rule ->
                step += 1
                RuleStepRow(index = step, rule = rule)
            }
        }
    }
}

/**
 * A file name, deliberately quiet. Rules from two files are as connected as two rules in one file, so
 * the band is a caption on the spine rather than a container that breaks it.
 */
@Composable
private fun FileBand(source: RuleSource) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 34.dp, top = 14.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DiagramIdentifier(text = source.relativePath, color = TextDesc, fontSize = 11)
        DiagramNote(text = "${source.rules.size} rule${Plurals.suffix(count = source.rules.size)}")
        DiagramNote(
            text = "grouping only — no runtime boundary",
            color = TextDesc.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun RuleStepRow(index: Int, rule: RuleAst) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top,
    ) {
        StepBadge(index = index)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape = RoundedCornerShape(size = 6.dp))
                .background(color = NodeBgCondition)
                .border(width = 1.dp, color = BorderCondition, shape = RoundedCornerShape(size = 6.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(space = 4.dp),
        ) {
            DiagramIdentifier(text = rule.id, color = LabelRule)
            DiagramConditionLine(text = ValueExpressionRenderer.renderExpression(expr = rule.condition))
            if (rule.actions.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(space = 6.dp)) {
                    rule.actions.forEach { action ->
                        DiagramChip(
                            text = "${action.name} ${action.arguments.joinToString(separator = ", ") { literal ->
                                formatLiteral(literal)
                            }}".trim(),
                            textColor = LabelActionName,
                            borderColor = BorderActions,
                            background = NodeBgActions,
                        )
                    }
                }
            }
        }
    }
}

/** The running evaluation position, sitting on the spine so the numbering reads as one sequence. */
@Composable
private fun StepBadge(index: Int) {
    Box(
        modifier = Modifier
            .padding(end = 10.dp)
            .size(size = 24.dp)
            .clip(shape = RoundedCornerShape(percent = 50))
            .background(color = DiagramBg)
            .border(width = 1.dp, color = BorderCondition, shape = RoundedCornerShape(percent = 50)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = index.toString(),
            style = TextStyle(fontSize = 10.sp, color = TextDesc, fontFamily = FontFamily.Monospace),
        )
    }
}

/**
 * Where the entry ends up: one result carrying every match.
 *
 * Spelled out because the engine's behaviour is easy to assume wrong — there is no priority and no
 * stop-first, so this is a collection point, not a winner.
 */
@Composable
private fun ResultCollectorNode(ruleCount: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .clip(shape = RoundedCornerShape(size = 8.dp))
            .background(color = NodeBgActions)
            .border(width = 1.dp, color = BorderActions, shape = RoundedCornerShape(size = 8.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(space = 5.dp)) {
            DiagramEyebrow(text = "RESULT", color = LabelActions)
            DiagramIdentifier(
                text = "EvaluationResult(matches = List<RuleMatch>, trace)",
                color = LabelArg,
                fontSize = 12,
            )
            DiagramNote(
                text = "All $ruleCount rules are evaluated and every match is returned, in the order " +
                    "numbered above. There is no priority and no stop-first.",
            )
        }
    }
}

private val SPINE_X = 12.dp
