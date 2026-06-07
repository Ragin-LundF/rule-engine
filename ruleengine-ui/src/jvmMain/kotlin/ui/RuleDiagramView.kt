package ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ruleengine.dsl.ast.ActionAst
import ruleengine.dsl.ast.AndAst
import ruleengine.dsl.ast.BetweenLiteral
import ruleengine.dsl.ast.ConditionAst
import ruleengine.dsl.ast.ExpressionAst
import ruleengine.dsl.ast.ListLiteral
import ruleengine.dsl.ast.LiteralAst
import ruleengine.dsl.ast.NotAst
import ruleengine.dsl.ast.NumberLiteral
import ruleengine.dsl.ast.OrAst
import ruleengine.dsl.ast.RuleAst
import ruleengine.dsl.ast.StringLiteral

// ── Colours used only in the diagram ─────────────────────────────────────────

private val DiagramBg       = Color(0xFF0D1117)
private val NodeBgRule      = Color(0xFF1C2333)
private val NodeBgAnd       = Color(0xFF1A2035)
private val NodeBgOr        = Color(0xFF1A2D1A)
private val NodeBgNot       = Color(0xFF2D1A1A)
private val NodeBgCondition = Color(0xFF161B22)
private val NodeBgActions   = Color(0xFF1A2233)
private val BorderRule      = Color(0xFF3B4A6B)
private val BorderAnd       = Color(0xFF2B5086)
private val BorderOr        = Color(0xFF2B6B2B)
private val BorderNot       = Color(0xFF7B2B2B)
private val BorderCondition = Color(0xFF30363D)
private val BorderActions   = Color(0xFF3B5A8B)
private val LineColor       = Color(0xFF3D4450)
private val LabelRule       = Color(0xFF79C0FF)
private val LabelAnd        = Color(0xFF58A6FF)
private val LabelOr         = Color(0xFF3FB950)
private val LabelNot        = Color(0xFFF85149)
private val LabelActions    = Color(0xFFA78BFA)
private val LabelField      = Color(0xFF79C0FF)
private val LabelOp         = Color(0xFFD29922)
private val LabelValue      = Color(0xFF3FB950)
private val LabelActionName = Color(0xFFA78BFA)
private val LabelArg        = Color(0xFFE6EDF3)
private val TextDesc        = Color(0xFF8B949E)

private val ConnectorW = 1.5.dp

// ── Entry-point composable ────────────────────────────────────────────────────

/**
 * Displays a vertically scrollable diagram for each rule in [rules].
 *
 * Each rule is rendered as a top-down pipeline:
 *  1. Rule header node
 *  2. Condition container (AND / OR / NOT become nested container boxes;
 *     leaf conditions are horizontal rows)
 *  3. Actions node
 *
 * [captureLayer] — when provided, the full-height content column records itself
 * into this layer before drawing to the screen. Call [GraphicsLayer.toImageBitmap]
 * to export the complete diagram, including content below the visible viewport.
 * Attaching the layer here (instead of on the outer viewport box) is essential:
 * the inner content Column is measured at its natural height by the scroll
 * layout pass, so the recording captures the entire diagram, not just the
 * currently visible portion.
 */
@Composable
fun RuleDiagramView(
    rules        : List<RuleAst>,
    captureLayer : GraphicsLayer? = null,
) {
    val scrollState = rememberScrollState()

    // Build the capture modifier once; reuse for both empty and normal states.
    val captureModifier = if (captureLayer != null) {
        Modifier.drawWithContent {
            // Inside record{} the receiver is a plain DrawScope, so the
            // explicit outer receiver is needed to reach ContentDrawScope.drawContent().
            captureLayer.record { this@drawWithContent.drawContent() }
            drawContent()
        }
    } else {
        Modifier
    }

    if (rules.isEmpty()) {
        // Empty state — still participates in capture so the placeholder is exported.
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

    // ── Scroll viewport (fixed to available size, clips content) ─────────────
    // The Box with verticalScroll measures its child WITHOUT a max-height
    // constraint, so the inner Column lays out at its full natural height.
    // The capture layer is attached to the inner Column, not to this Box,
    // which is why the recording is never clipped to the viewport.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DiagramBg)
            .verticalScroll(scrollState),
    ) {
        // ── Full-height content column (what actually gets captured) ──────────
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

// ── Single-rule diagram ───────────────────────────────────────────────────────

/**
 * Renders one rule as a vertical pipeline:
 *  RuleHeaderNode → VerticalConnector → ExpressionContainerNode → VerticalConnector → ActionsNode
 */
@Composable
private fun SingleRuleDiagram(rule: RuleAst) {
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

// ── Recursive expression renderer ────────────────────────────────────────────

/**
 * Recursively renders an [ExpressionAst] node:
 *  - [ConditionAst] → a single-line [ConditionLeafRow]
 *  - [AndAst]       → a [LogicContainerBox] labelled "AND" with children stacked vertically
 *  - [OrAst]        → a [LogicContainerBox] labelled "OR"  with children stacked vertically
 *  - [NotAst]       → a [LogicContainerBox] labelled "NOT" wrapping its single child
 *
 * Nesting depth is bounded by the AST depth, which is finite by construction.
 */
@Composable
private fun ExpressionContainerNode(expr: ExpressionAst) {
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

// ── Logic container box (AND / OR / NOT) ─────────────────────────────────────

/**
 * A styled container card used for AND, OR, and NOT nodes.
 *
 * Renders a label pill at the top (with a faint horizontal divider line),
 * followed by all [content] children stacked vertically with 6 dp spacing.
 * The container fills the available width and shows a coloured border.
 */
@Composable
private fun LogicContainerBox(
    label       : String,
    borderColor : Color,
    bg          : Color,
    labelColor  : Color,
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
        // ── Label header row ──────────────────────────────────────────────────
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Pill badge
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
            // Faint horizontal rule extending to the right of the pill
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(borderColor.copy(alpha = 0.4f)),
            )
        }
        // ── Children ──────────────────────────────────────────────────────────
        content()
    }
}

// ── Condition leaf row ────────────────────────────────────────────────────────

/**
 * Renders a single [ConditionAst] as a horizontal row:
 *   field-name  [operator badge]  value
 *
 * The row fills the available width; each text segment clips with ellipsis on overflow.
 */
@Composable
private fun ConditionLeafRow(expr: ConditionAst) {
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
        // Field name
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
        // Operator badge
        OperatorBadge(operator = expr.operator, ignoreCase = expr.ignoreCase)
        // Value
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

// ── Operator badge ────────────────────────────────────────────────────────────

@Composable
private fun OperatorBadge(operator: String, ignoreCase: Boolean) {
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

// ── Rule header node ──────────────────────────────────────────────────────────

@Composable
private fun RuleHeaderNode(rule: RuleAst) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(NodeBgRule)
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

// ── Actions node ──────────────────────────────────────────────────────────────

@Composable
private fun ActionsNode(rule: RuleAst) {
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
private fun ActionRow(action: ActionAst) {
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

// ── Vertical connector (arrow between pipeline steps) ────────────────────────

/**
 * Draws a short vertical line with an arrowhead to visually connect
 * the rule header → condition block → THEN block.
 */
@Composable
private fun VerticalConnector() {
    Box(
        modifier         = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.width(20.dp).height(28.dp)) {
            val cx        = size.width / 2f
            val arrowTip  = size.height - 4f
            val arrowSize = 5f

            drawLine(
                color       = LineColor,
                start       = Offset(x = cx, y = 0f),
                end         = Offset(x = cx, y = arrowTip),
                strokeWidth = ConnectorW.toPx(),
                cap         = StrokeCap.Round,
            )
            drawLine(
                color       = LineColor,
                start       = Offset(x = cx - arrowSize, y = arrowTip - arrowSize),
                end         = Offset(x = cx, y = arrowTip),
                strokeWidth = ConnectorW.toPx(),
                cap         = StrokeCap.Round,
            )
            drawLine(
                color       = LineColor,
                start       = Offset(x = cx + arrowSize, y = arrowTip - arrowSize),
                end         = Offset(x = cx, y = arrowTip),
                strokeWidth = ConnectorW.toPx(),
                cap         = StrokeCap.Round,
            )
        }
    }
}

// ── AST helpers ───────────────────────────────────────────────────────────────

/** Formats a [LiteralAst] for display inside a condition row. */
private fun formatLiteral(lit: LiteralAst): String {
    return when (lit) {
        is StringLiteral  -> "\"${lit.value}\""
        is NumberLiteral  -> lit.value
        is ListLiteral    -> "[${lit.items.joinToString(", ") { formatLiteral(it) }}]"
        is BetweenLiteral -> "${lit.low}..${lit.high}"
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

/**
 * Content shown when no rules are available. Extracted so the outer Box
 * (with the optional capture modifier) lives in [RuleDiagramView].
 */
@Composable
private fun EmptyDiagramPlaceholderContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text  = "⬡",
            style = TextStyle(fontSize = 40.sp, color = BorderColor),
        )
        Text(
            text  = "No valid rules to display",
            style = MaterialTheme.typography.body1,
            color = TextDesc,
        )
        Text(
            text  = "Write a rule in the Code tab and it will appear here",
            style = MaterialTheme.typography.caption,
            color = TextDesc.copy(alpha = 0.6f),
        )
    }
}

