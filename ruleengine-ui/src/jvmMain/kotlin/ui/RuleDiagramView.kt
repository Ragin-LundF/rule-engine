package ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ruleengine.dsl.ast.*

// ── Colours used only in the diagram ─────────────────────────────────────────

private val DiagramBg          = Color(0xFF0D1117)
private val NodeBgRule         = Color(0xFF1C2333)
private val NodeBgAnd          = Color(0xFF1A2035)
private val NodeBgOr           = Color(0xFF1A2D1A)
private val NodeBgNot          = Color(0xFF2D1A1A)
private val NodeBgCondition    = Color(0xFF161B22)
private val NodeBgActions      = Color(0xFF1A2233)
private val BorderRule         = Color(0xFF3B4A6B)
private val BorderAnd          = Color(0xFF2B5086)
private val BorderOr           = Color(0xFF2B6B2B)
private val BorderNot          = Color(0xFF7B2B2B)
private val BorderCondition    = Color(0xFF30363D)
private val BorderActions      = Color(0xFF3B5A8B)
private val LineColor          = Color(0xFF3D4450)
private val LabelRule          = Color(0xFF79C0FF)
private val LabelAnd           = Color(0xFF58A6FF)
private val LabelOr            = Color(0xFF3FB950)
private val LabelNot           = Color(0xFFF85149)
private val LabelActions       = Color(0xFFA78BFA)
private val LabelField         = Color(0xFF79C0FF)
private val LabelOp            = Color(0xFFD29922)
private val LabelValue         = Color(0xFF3FB950)
private val LabelActionName    = Color(0xFFA78BFA)
private val LabelArg           = Color(0xFFE6EDF3)
private val TextDesc           = Color(0xFF8B949E)

// ── Node geometry constants ───────────────────────────────────────────────────

private val HorizGap    = 20.dp   // horizontal space between sibling subtrees
private val VertGap     = 44.dp   // vertical space between node bottom and child top
private val ConnectorW  = 1.5.dp  // connector line stroke width

// ── Internal tree model ───────────────────────────────────────────────────────

/**
 * Lightweight intermediate representation of a diagram node.
 * Holds the composable content, its measured size, and child references.
 * The layout algorithm fills [x] and [y] (top-left, in dp) after measuring.
 */
private data class DiagramNode(
    val kind: NodeKind,
    val children: List<DiagramNode> = emptyList(),
    /** Filled by measure pass */
    var widthDp: Float = 0f,
    var heightDp: Float = 0f,
    /** Filled by layout pass — top-left corner, in dp, relative to the root canvas */
    var x: Float = 0f,
    var y: Float = 0f,
)

private enum class NodeKind { RULE, AND, OR, NOT, CONDITION, ACTIONS }

/** Converts a [RuleAst] into a [DiagramNode] tree. */
private fun ruleAstToTree(rule: RuleAst): DiagramNode {
    val conditionNode = expressionToTree(rule.condition)
    val actionsNode   = DiagramNode(kind = NodeKind.ACTIONS)
    return DiagramNode(
        kind     = NodeKind.RULE,
        children = listOf(conditionNode, actionsNode),
    )
}

private fun expressionToTree(expr: ExpressionAst): DiagramNode {
    return when (expr) {
        is AndAst       -> DiagramNode(kind = NodeKind.AND, children = expr.children.map { expressionToTree(it) })
        is OrAst        -> DiagramNode(kind = NodeKind.OR,  children = expr.children.map { expressionToTree(it) })
        is NotAst       -> DiagramNode(kind = NodeKind.NOT, children = listOf(expressionToTree(expr.child)))
        is ConditionAst -> DiagramNode(kind = NodeKind.CONDITION)
    }
}

// ── Entry-point composable ────────────────────────────────────────────────────

/**
 * Displays a scrollable diagram for each rule in [rules].
 * Each rule is rendered as a top-down tree with:
 *  - A rule header node
 *  - A condition subtree (AND / OR / NOT / leaf conditions)
 *  - An actions box
 * Connector lines are drawn on a Canvas layer behind the nodes.
 */
@Composable
fun RuleDiagramView(rules: List<RuleAst>) {
    val scrollV = rememberScrollState()
    val scrollH = rememberScrollState()

    if (rules.isEmpty()) {
        EmptyDiagramPlaceholder()
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DiagramBg)
            .verticalScroll(scrollV)
            .horizontalScroll(scrollH)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(48.dp),
    ) {
        rules.forEach { rule ->
            SingleRuleDiagram(rule = rule)
        }
    }
}

// ── Single-rule diagram ───────────────────────────────────────────────────────

@Composable
private fun SingleRuleDiagram(rule: RuleAst) {
    // Each diagram is drawn as a series of overlapping layers:
    //  1. The Canvas (connector lines, drawn in background)
    //  2. The node composables (boxes, drawn in foreground)
    // We use a custom Layout to position the nodes after measuring them,
    // then pass their positions to the Canvas so lines can be drawn.

    // Pre-compute a list of all nodes so we can assign stable keys.
    val nodes = remember(rule) { collectAllNodes(rule) }

    // Measured sizes are remembered per-rule by node key → (w, h) pairs.
    val measuredSizes = remember(rule) { mutableStateMapOf<Int, Pair<Float, Float>>() }

    // Final positions, filled by layout pass.
    val positions = remember(rule) { mutableStateMapOf<Int, Pair<Float, Float>>() }

    // Trigger layout whenever all sizes are available.
    val allMeasured = remember(measuredSizes.size, rule) {
        nodes.size > 0 && measuredSizes.size >= nodes.size
    }

    // Total canvas size determined after layout.
    var canvasWidthDp  by remember(rule) { mutableStateOf(800f) }
    var canvasHeightDp by remember(rule) { mutableStateOf(400f) }

    Box(modifier = Modifier.wrapContentSize()) {
        // ── Connector layer ───────────────────────────────────────────────────
        if (allMeasured && positions.isNotEmpty()) {
            Canvas(
                modifier = Modifier
                    .width(canvasWidthDp.dp)
                    .height(canvasHeightDp.dp),
            ) {
                drawConnectors(
                    rule      = rule,
                    nodes     = nodes,
                    positions = positions,
                    sizes     = measuredSizes,
                )
            }
        }

        // ── Node layer ────────────────────────────────────────────────────────
        TreeNodeLayout(
            rule          = rule,
            nodes         = nodes,
            measuredSizes = measuredSizes,
            onLayout      = { posMap, totalW, totalH ->
                posMap.forEach { (k, v) -> positions[k] = v }
                canvasWidthDp  = totalW
                canvasHeightDp = totalH
            },
        ) {
            nodes.forEachIndexed { index, node ->
                NodeComposable(
                    nodeIndex = index,
                    node      = node,
                    rule      = rule,
                    onMeasure = { w, h -> measuredSizes[index] = Pair(w, h) },
                )
            }
        }
    }
}

// ── Node collection ───────────────────────────────────────────────────────────

/** Returns all nodes in pre-order (parent before children) so index == stable key. */
private fun collectAllNodes(rule: RuleAst): List<DiagramNode> {
    val result = mutableListOf<DiagramNode>()
    val root   = buildDiagramTree(rule)
    fun visit(n: DiagramNode) {
        result.add(n)
        n.children.forEach { visit(it) }
    }
    visit(root)
    return result
}

/**
 * Builds the full [DiagramNode] tree for a rule, including condition subtree
 * and an actions leaf node.
 */
private fun buildDiagramTree(rule: RuleAst): DiagramNode {
    val conditionNode = buildExpressionTree(rule.condition)
    val actionsNode   = DiagramNode(kind = NodeKind.ACTIONS)
    return DiagramNode(
        kind     = NodeKind.RULE,
        children = listOf(conditionNode, actionsNode),
    )
}

private fun buildExpressionTree(expr: ExpressionAst): DiagramNode {
    return when (expr) {
        is AndAst       -> DiagramNode(kind = NodeKind.AND, children = expr.children.map { buildExpressionTree(it) })
        is OrAst        -> DiagramNode(kind = NodeKind.OR,  children = expr.children.map { buildExpressionTree(it) })
        is NotAst       -> DiagramNode(kind = NodeKind.NOT, children = listOf(buildExpressionTree(expr.child)))
        is ConditionAst -> DiagramNode(kind = NodeKind.CONDITION)
    }
}

// ── Custom layout ─────────────────────────────────────────────────────────────

/**
 * A custom layout that:
 *  1. Measures all child composables (each is a node box)
 *  2. Runs the tree-layout algorithm to compute (x, y) positions
 *  3. Places each child at its computed position
 *  4. Reports positions back via [onLayout] so the Canvas can draw connectors
 */
@Composable
private fun TreeNodeLayout(
    rule          : RuleAst,
    nodes         : List<DiagramNode>,
    measuredSizes : Map<Int, Pair<Float, Float>>,
    onLayout      : (Map<Int, Pair<Float, Float>>, Float, Float) -> Unit,
    content       : @Composable () -> Unit,
) {
    Layout(
        content     = content,
        measurePolicy = object : MeasurePolicy {
            override fun MeasureScope.measure(
                measurables : List<Measurable>,
                constraints : Constraints,
            ): MeasureResult {
                // Measure every child with loose constraints.
                val placeables = measurables.map { m ->
                    m.measure(Constraints())
                }

                if (placeables.isEmpty()) {
                    return layout(width = 1, height = 1) {}
                }

                // Build the tree with measured sizes.
                val root   = buildDiagramTree(rule)
                val allNodes = mutableListOf<DiagramNode>()
                fun collect(n: DiagramNode) { allNodes.add(n); n.children.forEach { collect(it) } }
                collect(root)

                val hGapPx = HorizGap.toPx()
                val vGapPx = VertGap.toPx()

                // Assign sizes from placeables.
                allNodes.forEachIndexed { idx, n ->
                    val p = placeables.getOrNull(idx) ?: return@forEachIndexed
                    n.widthDp  = p.measuredWidth.toFloat()
                    n.heightDp = p.measuredHeight.toFloat()
                }

                // Recursive subtree width computation.
                fun subtreeWidth(n: DiagramNode): Float {
                    if (n.children.isEmpty()) return n.widthDp
                    val childrenWidth = n.children.sumOf { subtreeWidth(it).toDouble() }.toFloat() +
                            hGapPx * (n.children.size - 1)
                    return maxOf(n.widthDp, childrenWidth)
                }

                // Recursive layout: fills n.x, n.y.
                fun layoutNode(n: DiagramNode, left: Float, top: Float) {
                    val sw = subtreeWidth(n)
                    // Centre this node over its subtree.
                    n.x = left + (sw - n.widthDp) / 2f
                    n.y = top
                    if (n.children.isEmpty()) return
                    var cx = left
                    n.children.forEach { child ->
                        layoutNode(child, cx, top + n.heightDp + vGapPx)
                        cx += subtreeWidth(child) + hGapPx
                    }
                }

                layoutNode(root, left = 0f, top = 0f)

                // Compute total canvas size.
                fun totalW(n: DiagramNode): Float = maxOf(n.x + n.widthDp, n.children.maxOfOrNull { totalW(it) } ?: 0f)
                fun totalH(n: DiagramNode): Float = maxOf(n.y + n.heightDp, n.children.maxOfOrNull { totalH(it) } ?: 0f)
                val totalWidth  = (totalW(root) + 48f).coerceAtLeast(200f)
                val totalHeight = (totalH(root) + 48f).coerceAtLeast(100f)

                // Report positions.
                val posMap = mutableMapOf<Int, Pair<Float, Float>>()
                allNodes.forEachIndexed { idx, n -> posMap[idx] = Pair(n.x, n.y) }
                onLayout(posMap, totalWidth / density, totalHeight / density)

                return layout(
                    width  = totalWidth.toInt(),
                    height = totalHeight.toInt(),
                ) {
                    placeables.forEachIndexed { idx, p ->
                        val pos = allNodes.getOrNull(idx) ?: return@forEachIndexed
                        p.placeRelative(x = pos.x.toInt(), y = pos.y.toInt())
                    }
                }
            }
        },
    )
}

// ── Connector drawing ─────────────────────────────────────────────────────────

private fun DrawScope.drawConnectors(
    rule      : RuleAst,
    nodes     : List<DiagramNode>,
    positions : Map<Int, Pair<Float, Float>>,
    sizes     : Map<Int, Pair<Float, Float>>,
) {
    val root     = buildDiagramTree(rule)
    val allNodes = mutableListOf<DiagramNode>()
    fun collect(n: DiagramNode) { allNodes.add(n); n.children.forEach { collect(it) } }
    collect(root)

    allNodes.forEachIndexed { parentIdx, parent ->
        val (px, py) = positions[parentIdx] ?: return@forEachIndexed
        val (pw, ph) = sizes[parentIdx] ?: return@forEachIndexed
        val parentBottom = Offset(x = px + pw / 2f, y = py + ph)

        parent.children.forEachIndexed { childOffset, _ ->
            // Find child index in the flat list.
            val childIdx = allNodes.indexOfFirst { child ->
                child === parent.children[childOffset]
            }
            if (childIdx < 0) return@forEachIndexed
            val (cx, cy) = positions[childIdx] ?: return@forEachIndexed
            val (cw, _)  = sizes[childIdx] ?: return@forEachIndexed
            val childTop = Offset(x = cx + cw / 2f, y = cy)

            // Draw a smooth cubic bezier from parent-bottom to child-top.
            val midY = (parentBottom.y + childTop.y) / 2f
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(parentBottom.x, parentBottom.y)
                cubicTo(
                    x1 = parentBottom.x, y1 = midY,
                    x2 = childTop.x,     y2 = midY,
                    x3 = childTop.x,     y3 = childTop.y,
                )
            }
            drawPath(
                path  = path,
                color = LineColor,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width     = ConnectorW.toPx(),
                    cap       = StrokeCap.Round,
                    pathEffect = null,
                ),
            )

            // Small arrowhead at child-top.
            val arrowSize = 5f
            drawLine(
                color       = LineColor,
                start       = Offset(childTop.x - arrowSize, childTop.y - arrowSize),
                end         = Offset(childTop.x, childTop.y),
                strokeWidth = ConnectorW.toPx(),
                cap         = StrokeCap.Round,
            )
            drawLine(
                color       = LineColor,
                start       = Offset(childTop.x + arrowSize, childTop.y - arrowSize),
                end         = Offset(childTop.x, childTop.y),
                strokeWidth = ConnectorW.toPx(),
                cap         = StrokeCap.Round,
            )
        }
    }
}

// ── Node composables ──────────────────────────────────────────────────────────

@Composable
private fun NodeComposable(
    nodeIndex : Int,
    node      : DiagramNode,
    rule      : RuleAst,
    onMeasure : (Float, Float) -> Unit,
) {
    // Collect the corresponding AST node from rule by walking the tree.
    val expression = remember(rule, nodeIndex) {
        findExpression(rule, nodeIndex)
    }

    BoxWithConstraints(
        modifier = Modifier.wrapContentSize(),
    ) {
        val measuredW = constraints.maxWidth.toFloat()
        val measuredH = constraints.maxHeight.toFloat()

        when (node.kind) {
            NodeKind.RULE      -> RuleHeaderNode(rule = rule, onMeasure = onMeasure)
            NodeKind.AND       -> LogicOperatorNode(label = "AND", bg = NodeBgAnd, border = BorderAnd, labelColor = LabelAnd, onMeasure = onMeasure)
            NodeKind.OR        -> LogicOperatorNode(label = "OR",  bg = NodeBgOr,  border = BorderOr,  labelColor = LabelOr,  onMeasure = onMeasure)
            NodeKind.NOT       -> LogicOperatorNode(label = "NOT", bg = NodeBgNot, border = BorderNot, labelColor = LabelNot, onMeasure = onMeasure)
            NodeKind.CONDITION -> ConditionNode(expr = expression as? ConditionAst, onMeasure = onMeasure)
            NodeKind.ACTIONS   -> ActionsNode(rule = rule, onMeasure = onMeasure)
        }
    }
}

// ── Rule header node ──────────────────────────────────────────────────────────

@Composable
private fun RuleHeaderNode(
    rule      : RuleAst,
    onMeasure : (Float, Float) -> Unit,
) {
    NodeBox(
        bg          = NodeBgRule,
        borderColor = BorderRule,
        minWidth    = 200.dp,
        onMeasure   = onMeasure,
    ) {
        Column(
            modifier            = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Small label
            Text(
                text  = "RULE",
                style = TextStyle(
                    fontSize     = 9.sp,
                    fontWeight   = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp,
                    color        = TextDesc,
                ),
            )
            // Rule id
            Text(
                text  = "\"${rule.id}\"",
                style = TextStyle(
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color      = LabelRule,
                    fontFamily = FontFamily.Monospace,
                ),
                textAlign = TextAlign.Center,
            )
            // Optional description
            val desc = rule.description
            if (!desc.isNullOrBlank()) {
                Text(
                    text  = desc,
                    style = TextStyle(
                        fontSize = 11.sp,
                        color    = TextDesc,
                    ),
                    textAlign   = TextAlign.Center,
                    maxLines    = 2,
                    overflow    = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ── Logic operator node (AND / OR / NOT) ──────────────────────────────────────

@Composable
private fun LogicOperatorNode(
    label      : String,
    bg         : Color,
    border     : Color,
    labelColor : Color,
    onMeasure  : (Float, Float) -> Unit,
) {
    NodeBox(
        bg          = bg,
        borderColor = border,
        minWidth    = 72.dp,
        onMeasure   = onMeasure,
        shape       = RoundedCornerShape(50),          // pill shape
    ) {
        Box(
            modifier            = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            contentAlignment    = Alignment.Center,
        ) {
            Text(
                text  = label,
                style = TextStyle(
                    fontSize     = 13.sp,
                    fontWeight   = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color        = labelColor,
                ),
            )
        }
    }
}

// ── Condition leaf node ───────────────────────────────────────────────────────

@Composable
private fun ConditionNode(
    expr      : ConditionAst?,
    onMeasure : (Float, Float) -> Unit,
) {
    NodeBox(
        bg          = NodeBgCondition,
        borderColor = BorderCondition,
        minWidth    = 140.dp,
        onMeasure   = onMeasure,
    ) {
        Column(
            modifier            = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (expr != null) {
                // Field name
                Text(
                    text  = expr.field,
                    style = TextStyle(
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = LabelField,
                        fontFamily = FontFamily.Monospace,
                    ),
                )
                // Operator badge
                OperatorBadge(operator = expr.operator, ignoreCase = expr.ignoreCase)
                // Value
                Text(
                    text  = formatLiteral(expr.value),
                    style = TextStyle(
                        fontSize   = 12.sp,
                        color      = LabelValue,
                        fontFamily = FontFamily.Monospace,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    text  = "condition",
                    style = TextStyle(fontSize = 12.sp, color = TextDesc),
                )
            }
        }
    }
}

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

// ── Actions node ──────────────────────────────────────────────────────────────

@Composable
private fun ActionsNode(
    rule      : RuleAst,
    onMeasure : (Float, Float) -> Unit,
) {
    NodeBox(
        bg          = NodeBgActions,
        borderColor = BorderActions,
        minWidth    = 160.dp,
        onMeasure   = onMeasure,
    ) {
        Column(
            modifier            = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Header
            Text(
                text  = "THEN",
                style = TextStyle(
                    fontSize     = 9.sp,
                    fontWeight   = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp,
                    color        = LabelActions.copy(alpha = 0.7f),
                ),
            )
            // Action rows
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
        verticalAlignment   = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Bullet
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
                text  = "(${action.arguments.joinToString(", ") { formatLiteral(it) }})",
                style = TextStyle(
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

// ── Shared node box ───────────────────────────────────────────────────────────

/**
 * A styled rounded box that reports its measured pixel size via [onMeasure].
 * Used as the common outer container for every node type.
 */
@Composable
private fun NodeBox(
    bg          : Color,
    borderColor : Color,
    minWidth    : Dp,
    onMeasure   : (Float, Float) -> Unit,
    shape       : androidx.compose.ui.graphics.Shape = RoundedCornerShape(8.dp),
    content     : @Composable () -> Unit,
) {
    Layout(
        content = {
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = minWidth)
                    .clip(shape)
                    .background(bg)
                    .border(width = 1.dp, color = borderColor, shape = shape),
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        },
        measurePolicy = { measurables, constraints ->
            val placeable = measurables.first().measure(Constraints(minWidth = 0))
            onMeasure(placeable.measuredWidth.toFloat(), placeable.measuredHeight.toFloat())
            layout(placeable.measuredWidth, placeable.measuredHeight) {
                placeable.placeRelative(0, 0)
            }
        },
    )
}

// ── AST helpers ───────────────────────────────────────────────────────────────

/**
 * Walks the rule AST in pre-order to find the expression at a given node index,
 * matching the same traversal order used by [collectAllNodes].
 * Returns null if the node at [targetIndex] is not a [ConditionAst].
 */
private fun findExpression(rule: RuleAst, targetIndex: Int): ExpressionAst? {
    // Node 0 = RULE, node 1 = first child of RULE = condition root,
    // ..., last node = ACTIONS
    // Walk in pre-order, skip index 0 (RULE) and last (ACTIONS).
    var counter = 0

    fun visit(expr: ExpressionAst): ExpressionAst? {
        counter++ // first increment moves past RULE node (index 0)
        if (counter == targetIndex) return expr
        return when (expr) {
            is AndAst -> {
                for (child in expr.children) {
                    val result = visit(child)
                    if (result != null) return result
                }
                null
            }
            is OrAst  -> {
                for (child in expr.children) {
                    val result = visit(child)
                    if (result != null) return result
                }
                null
            }
            is NotAst -> visit(expr.child)
            else      -> null
        }
    }

    return visit(rule.condition)
}

/** Formats a [LiteralAst] for display inside a condition node. */
private fun formatLiteral(lit: LiteralAst): String {
    return when (lit) {
        is StringLiteral  -> "\"${lit.value}\""
        is NumberLiteral  -> lit.value
        is ListLiteral    -> "[${lit.items.joinToString(", ") { formatLiteral(it) }}]"
        is BetweenLiteral -> "${lit.low}..${lit.high}"
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyDiagramPlaceholder() {
    Box(
        modifier         = Modifier.fillMaxSize().background(DiagramBg),
        contentAlignment = Alignment.Center,
    ) {
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
}



