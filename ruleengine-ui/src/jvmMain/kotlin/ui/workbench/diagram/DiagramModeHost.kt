package ui.workbench.diagram

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.dp
import ui.diagrams.DiagramBg
import ui.diagrams.FieldFlowDiagram
import ui.diagrams.ManifestRunDiagram
import ui.diagrams.OutcomeMapDiagram
import ui.diagrams.model.DiagramData
import ui.diagrams.model.DiagramViewKind
import ui.diagrams.render.DiagramSurface
import ui.diagrams.render.EmptyDiagramPlaceholderContent
import ui.diagrams.render.SingleRuleDiagram

/**
 * Host for Diagram mode: one scrolling canvas, one of several views inside it.
 *
 * The views are selected in the toolbar rather than being separate `RuleMode` entries. A new
 * `RuleMode` has to be added to two enums, a hand-unrolled tab strip, five exhaustive `when`s, the
 * manifest file picker and the editor state — four times over for four views, to give the reader
 * nothing a selector does not.
 *
 * How many rules a view sees is the existing scope control, the `☰` picker's "All files" option and
 * the `showAllRules` flag behind it. A second scope selector here could disagree with that one.
 *
 * The capture layer is recorded on the full-height content column rather than on the clipped
 * viewport, so Export PNG captures the whole diagram whichever view is showing.
 */
@Composable
fun DiagramModeHost(
    view: DiagramViewKind,
    data: DiagramData,
    captureLayer: GraphicsLayer? = null,
) {
    val captureModifier = if (captureLayer != null) {
        Modifier.drawWithContent {
            captureLayer.record { this@drawWithContent.drawContent() }
            drawContent()
        }
    } else {
        Modifier
    }

    if (isEmpty(view = view, data = data)) {
        Box(
            modifier = Modifier.fillMaxSize().background(color = DiagramBg).then(other = captureModifier),
            contentAlignment = Alignment.Center,
        ) {
            EmptyDiagramPlaceholderContent()
        }
        return
    }

    DiagramSurface {
        Column(modifier = Modifier.fillMaxWidth().then(other = captureModifier)) {
            when (view) {
                DiagramViewKind.TREE -> RuleTrees(data = data)
                DiagramViewKind.RUN -> ManifestRunDiagram(data = data)
                DiagramViewKind.OUTCOMES -> OutcomeMapDiagram(rules = data.rules)
                DiagramViewKind.FIELDS -> FieldFlowDiagram(
                    rules = data.rules,
                    schema = data.schema,
                    entryWide = data.entryWide,
                )
            }
        }
    }
}

/** The original view: one condition tree per rule, unchanged. */
@Composable
private fun RuleTrees(data: DiagramData) {
    Column(modifier = Modifier.fillMaxWidth()) {
        data.rules.forEachIndexed { index, rule ->
            if (index > 0) {
                Box(modifier = Modifier.padding(top = 48.dp))
            }
            SingleRuleDiagram(rule = rule)
        }
    }
}

/**
 * Only the fully empty case falls back to the generic placeholder. The run view is empty for its own
 * reason — no manifest entry loaded, rather than no rules parsed — and says so itself.
 */
private fun isEmpty(view: DiagramViewKind, data: DiagramData): Boolean {
    if (view == DiagramViewKind.RUN) {
        return false
    }
    return data.rules.isEmpty()
}
