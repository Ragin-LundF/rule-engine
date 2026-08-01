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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ruleengine.dsl.ast.ActionAst
import ruleengine.dsl.ast.RuleAst

@Composable
internal fun RuleHeaderNode(rule: RuleAst) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape = RoundedCornerShape(size = 8.dp))
            .background(color = NodeBgRule)
            .border(width = 1.dp, color = BorderRule, shape = RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier            = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text  = "RULE",
                style = TextStyle(
                    fontSize      = 9.sp,
                    fontWeight    = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp,
                    color         = TextDesc,
                ),
            )
            Text(
                text      = "\"${rule.id}\"",
                style     = TextStyle(
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color      = LabelRule,
                    fontFamily = FontFamily.Monospace,
                ),
                textAlign = TextAlign.Center,
            )
            val description = rule.description
            if (!description.isNullOrBlank()) {
                Text(
                    text      = description,
                    style     = TextStyle(fontSize = 11.sp, color = TextDesc),
                    textAlign = TextAlign.Center,
                    maxLines  = 2,
                    overflow  = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun ActionsNode(rule: RuleAst) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(NodeBgActions)
            .border(width = 1.dp, color = BorderActions, shape = RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier            = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text  = "THEN",
                style = TextStyle(
                    fontSize      = 9.sp,
                    fontWeight    = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp,
                    color         = LabelActions.copy(alpha = 0.7f),
                ),
            )
            rule.actions.forEach { action ->
                ActionRow(action = action)
            }
            if (rule.actions.isEmpty()) {
                Text(
                    text  = "no actions",
                    style = TextStyle(fontSize = 11.sp, color = TextDesc),
                )
            }
        }
    }
}

@Composable
internal fun ActionRow(action: ActionAst) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .background(LabelActions.copy(alpha = 0.5f), RoundedCornerShape(50)),
        )
        Text(
            text  = action.name,
            style = TextStyle(
                fontSize   = 12.sp,
                fontWeight = FontWeight.Medium,
                color      = LabelActionName,
                fontFamily = FontFamily.Monospace,
            ),
        )
        if (action.arguments.isNotEmpty()) {
            Text(
                text     = "(${action.arguments.joinToString(", ") { formatLiteral(it) }})",
                style    = TextStyle(
                    fontSize   = 12.sp,
                    color      = LabelArg,
                    fontFamily = FontFamily.Monospace,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

