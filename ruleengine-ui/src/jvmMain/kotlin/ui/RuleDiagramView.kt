package ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.layer.GraphicsLayer
import ruleengine.dsl.ast.RuleAst
import ui.diagrams.RuleDiagramView as DiagramsRuleDiagramView

/**
 * Small compatibility wrapper so callers in `ui` can continue to import
 * `ui.RuleDiagramView`. The real implementation lives in `ui.diagrams`.
 */
@Composable
fun RuleDiagramView(
    rules        : List<RuleAst>,
    captureLayer : GraphicsLayer? = null,
) {
    DiagramsRuleDiagramView(rules = rules, captureLayer = captureLayer)
}
