package ui.diagrams

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.dp
import ruleengine.dsl.ast.RuleAst

/** Entry-point composable for rendering a set of rules as diagrams. */
@Composable
fun RuleDiagramView(
    rules        : List<RuleAst>,
    captureLayer : GraphicsLayer? = null,
) {
    val scrollState = rememberScrollState()

    val captureModifier = if (captureLayer != null) {
        Modifier.drawWithContent {
            captureLayer.record { this@drawWithContent.drawContent() }
            drawContent()
        }
    } else {
        Modifier
    }

    if (rules.isEmpty()) {
        Box(
            modifier         = Modifier
                .fillMaxSize()
                .background(DiagramBg)
                .then(captureModifier),
            contentAlignment = Alignment.Center,
        ) {
            EmptyDiagramPlaceholderContent()
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DiagramBg)
            .verticalScroll(scrollState),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(DiagramBg)
                .then(captureModifier)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(48.dp),
        ) {
            rules.forEach { rule ->
                SingleRuleDiagram(rule = rule)
            }
        }
    }
}

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


