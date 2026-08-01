package ui.diagrams

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ruleengine.dsl.ast.RuleAst

/**
 * One rule as a condition tree: header, condition, actions.
 *
 * The scrolling canvas, the capture layer for PNG export and the choice of which diagram to draw all
 * live in `ui.workbench.DiagramModeHost`, which is shared by every view.
 */
@Composable
internal fun SingleRuleDiagram(rule: RuleAst) {
    Column(
        modifier            = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RuleHeaderNode(rule = rule)
        VerticalConnector()
        ExpressionContainerNode(expr = rule.condition)
        VerticalConnector()
        ActionsNode(rule = rule)
    }
}
