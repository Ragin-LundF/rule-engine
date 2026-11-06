package ruleengine.intellij.diagram

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ruleengine.intellij.diagram.model.DiagramViewState
import ui.diagrams.TextDesc
import ui.diagrams.render.DiagramSurface
import ui.diagrams.render.SingleRuleDiagram

/** The rule as the UI's Tree view draws it, on the UI's own diagram surface. */
@Suppress("FunctionNaming")
@Composable
fun RuleDiagramView(state: DiagramViewState) {
    DiagramSurface {
        Column {
            state.message?.let { message ->
                BasicText(
                    text = message,
                    modifier = Modifier.padding(bottom = 16.dp),
                    style = TextStyle(color = TextDesc, fontSize = 12.sp),
                )
            }
            state.rule?.let { rule -> SingleRuleDiagram(rule = rule) }
        }
    }
}
