package ui.diagrams

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ruleengine.dsl.ast.AndAst
import ruleengine.dsl.ast.ConditionAst
import ruleengine.dsl.ast.ExpressionAst
import ruleengine.dsl.ast.NotAst
import ruleengine.dsl.ast.OrAst

/** Recursively renders an [ExpressionAst] node. */
@Composable
internal fun ExpressionContainerNode(expr: ExpressionAst) {
    when (expr) {
        is ConditionAst -> ConditionLeafRow(expr = expr)

        is AndAst -> LogicContainerBox(
            label       = "AND",
            borderColor = BorderAnd,
            bg          = NodeBgAnd,
            labelColor  = LabelAnd,
        ) {
            expr.children.forEach { child ->
                ExpressionContainerNode(expr = child)
            }
        }

        is OrAst -> LogicContainerBox(
            label       = "OR",
            borderColor = BorderOr,
            bg          = NodeBgOr,
            labelColor  = LabelOr,
        ) {
            expr.children.forEach { child ->
                ExpressionContainerNode(expr = child)
            }
        }

        is NotAst -> LogicContainerBox(
            label       = "NOT",
            borderColor = BorderNot,
            bg          = NodeBgNot,
            labelColor  = LabelNot,
        ) {
            ExpressionContainerNode(expr = expr.child)
        }
    }
}

/** A styled container card used for AND, OR, and NOT nodes. */
@Composable
internal fun LogicContainerBox(
    label       : String,
    borderColor : androidx.compose.ui.graphics.Color,
    bg          : androidx.compose.ui.graphics.Color,
    labelColor  : androidx.compose.ui.graphics.Color,
    content     : @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(labelColor.copy(alpha = 0.15f))
                    .border(1.dp, labelColor.copy(alpha = 0.4f), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text  = label,
                    style = TextStyle(
                        fontSize      = 11.sp,
                        fontWeight    = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color         = labelColor,
                    ),
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(borderColor.copy(alpha = 0.4f)),
            )
        }
        content()
    }
}

@Composable
internal fun ConditionLeafRow(expr: ConditionAst) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(NodeBgCondition)
            .border(width = 1.dp, color = BorderCondition, shape = RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text     = expr.field,
            style    = TextStyle(
                fontSize   = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color      = LabelField,
                fontFamily = FontFamily.Monospace,
            ),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            )

        OperatorBadge(operator = expr.operator, ignoreCase = expr.ignoreCase)

        Text(
            text     = formatLiteral(expr.value),
            style    = TextStyle(
                fontSize   = 12.sp,
                color      = LabelValue,
                fontFamily = FontFamily.Monospace,
            ),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
@Composable
internal fun OperatorBadge(operator: String, ignoreCase: Boolean) {
    val label = if (ignoreCase) "$operator (i)" else operator
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF1F2D3D))
            .border(1.dp, LabelOp.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text  = label,
            style = TextStyle(
                fontSize   = 11.sp,
                fontWeight = FontWeight.Medium,
                color      = LabelOp,
                fontFamily = FontFamily.Monospace,
            ),
        )
    }
}





