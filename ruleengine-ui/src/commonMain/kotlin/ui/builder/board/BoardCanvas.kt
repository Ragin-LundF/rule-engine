package ui.builder.board

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import ruleengine.core.domain.dto.RuleBranch
import ui.AccentCyan
import ui.AccentRed
import ui.BgSurface
import ui.BorderColor
import ui.PrimaryBlue
import ui.TextPrimary
import ui.TextSecondary
import ui.builder.BuilderToRuleDsl
import ui.builder.board.ribbon.EntryRibbon
import ui.builder.board.ribbon.RibbonLegend
import ui.builder.board.ribbon.RibbonModel
import ui.builder.board.ribbon.VariableFlow
import ui.builder.model.BuilderRule
import ui.builder.model.mutable.BuilderEditorState
import ui.components.SectionDivider
import ui.workbench.model.catalog.RuleTreeFile

/**
 * The board: the run along the top, and the selected rule laid out below it.
 *
 * The board and the outline are two renderings of one rule and one selection. Nothing here holds
 * editing state of its own — the ribbon is derived from the loaded rules, the rails and lanes read the
 * same `BuilderEditorState`, and a click writes to the same selection the Inspector reads. That is what
 * makes switching canvas keep your place: there is nothing to carry across, because neither canvas owns
 * anything.
 *
 * The row priorities below are deliberate and were the fix for the prototype collapsing on a short
 * window: the ribbon keeps its content height, the rule area keeps a floor, and the legend yields. A
 * `weight` on all three let the ribbon shrink until its cards clipped.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
fun BoardCanvas(
    state: BuilderEditorState,
    files: List<RuleTreeFile>,
    rules: List<BuilderRule>,
    selectedNodeId: String?,
    selectedStatementId: String?,
    onSelectNode: (String) -> Unit,
    onSelectStatement: (RuleBranch, String) -> Unit,
    onSelectRule: (relativePath: String, ruleId: String) -> Unit,
    onDslChange: (String) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val drag = remember { BoardDragState() }
    val groups = remember(files, rules) { RibbonModel.groups(files = files, rules = rules) }
    val scroll = rememberScrollState()

    // Which variable's flow is lit, and where the scrolling area is. Both are pure view state — nothing
    // about the rule — so they live here rather than in the selection the Inspector reads.
    var highlightedVariable by remember(state) { mutableStateOf<String?>(value = null) }
    var viewportBounds by remember { mutableStateOf<Rect?>(value = null) }

    val flow = highlightedVariable?.let { name ->
        VariableFlow.of(variable = name, groups = groups)
    }

    // A drag has no scroll of its own, so a card dragged to the bottom edge would otherwise have
    // nowhere to go. This nudges the canvas while the pointer sits near an edge. Keyed on the pointer so
    // it restarts as the pointer moves and stops the moment the drag ends.
    AutoScrollWhileDragging(drag = drag, scroll = scroll, viewport = { viewportBounds })

    fun emit() {
        BuilderToRuleDsl.generate(state = state)?.let(onDslChange)
    }

    Column(modifier = modifier.fillMaxSize()) {
        // min-content: the ribbon states its own height and never gives it up.
        EntryRibbon(
            groups = groups,
            selectedRuleId = state.ruleId,
            highlighted = flow,
            onSelectRule = onSelectRule,
            onHighlightVariable = { name -> highlightedVariable = name },
            modifier = Modifier.fillMaxWidth(),
        )
        RibbonLegend(modifier = Modifier.padding(bottom = 8.dp))
        flow?.let { lit -> FlowNote(flow = lit) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(weight = 1f)
                .heightIn(min = MIN_CANVAS_HEIGHT)
                .onGloballyPositioned { coordinates ->
                    viewportBounds = coordinates.boundsInRoot()
                }
                .verticalScroll(state = scroll),
            verticalArrangement = Arrangement.spacedBy(space = 8.dp),
        ) {
            if (state.isLocked) {
                BoardLockedNote(reason = state.lockReason)
                return@Column
            }

            SectionLabel(text = "WHEN", hint = "drag a row onto another to bracket the two")
            BoardWhenRails(
                nodes = state.conditionNodes,
                state = state,
                drag = drag,
                depth = 0,
                selectedNodeId = selectedNodeId,
                onSelectNode = onSelectNode,
                onEdited = ::emit,
                onMessage = onMessage,
            )

            SectionDivider()

            SectionLabel(text = "OUTCOMES", hint = "drag a card between lanes to move it")
            BoardLanes(
                state = state,
                drag = drag,
                selectedStatementId = selectedStatementId,
                onSelectStatement = onSelectStatement,
                onEdited = ::emit,
                onMessage = onMessage,
            )
        }
    }
}

/**
 * Scrolls the canvas while a drag hovers near its top or bottom edge.
 *
 * A drag carries no scroll of its own, so without this a card dragged to the bottom of the viewport has
 * nowhere further to go and a lane below the fold is unreachable.
 *
 * The loop reads [BoardDragState.pointer] fresh each frame rather than depending on it as an effect key:
 * keying on the pointer would restart the effect on every pointer move, which is every frame of a drag.
 * Keying on *whether* a drag is active starts it once and cancels it once, and `withFrameNanos` paces it
 * to the display instead of to a timer.
 */
@Suppress("FunctionNaming")
@Composable
private fun AutoScrollWhileDragging(
    drag: BoardDragState,
    scroll: ScrollState,
    viewport: () -> Rect?,
) {
    val dragging = drag.dragged != null

    LaunchedEffect(dragging) {
        if (!dragging) {
            return@LaunchedEffect
        }
        // One exit condition, checked once: the pointer going null is the drag ending.
        while (isActive && drag.pointer != null) {
            withFrameNanos { }
            val delta = edgeScrollDelta(pointer = drag.pointer, viewport = viewport())
            if (delta != 0f) {
                scroll.scrollBy(value = delta)
            }
        }
    }
}

/**
 * What lighting up a variable actually revealed.
 *
 * The highlight alone shows *which* cards are involved; this line says what the relationship is, and in
 * particular names the readers that no earlier rule publishes to. That case is the board's one genuine
 * warning: the rule parses, validates, runs, and silently never fires, and no single-rule view can see
 * it. Saying it in words is the difference between a pretty highlight and a diagnosis.
 */
@Suppress("FunctionNaming")
@Composable
private fun FlowNote(flow: VariableFlow.Flow) {
    val summary = buildString {
        append("\$${flow.variable}: ")
        append(if (flow.producers.isEmpty()) "set by no rule" else "set by ${flow.producers.size}")
        append(", read by ${flow.readers.size + flow.orphanReaders.size}")
    }

    Column(modifier = Modifier.padding(bottom = 6.dp)) {
        Text(text = summary, style = MaterialTheme.typography.caption, color = AccentCyan)
        if (flow.orphanReaders.isNotEmpty()) {
            Text(
                text = "⚠ rule ${flow.orphanReaders.joinToString(separator = ", ")} " +
                    "read it before anything sets it — those rules can never fire",
                style = MaterialTheme.typography.caption,
                color = AccentRed,
            )
        }
    }
}

/**
 * How far to scroll for a pointer sitting near an edge of [viewport]: negative up, positive down, zero
 * when it is not near one — or when either input is missing, which is the same answer.
 */
private fun edgeScrollDelta(pointer: Offset?, viewport: Rect?): Float {
    if (pointer == null || viewport == null) {
        return 0f
    }
    return when {
        pointer.y < viewport.top + EDGE_BAND -> -SCROLL_STEP
        pointer.y > viewport.bottom - EDGE_BAND -> SCROLL_STEP
        else -> 0f
    }
}

/** A section's name and the gesture it offers, which is the part a board has to say out loud. */
@Suppress("FunctionNaming")
@Composable
private fun SectionLabel(text: String, hint: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
            color = PrimaryBlue,
        )
        Text(text = hint, style = MaterialTheme.typography.caption, color = TextSecondary)
    }
}

/** A rule the Builder will not rewrite. The ribbon still shows it in the run. */
@Suppress("FunctionNaming")
@Composable
private fun BoardLockedNote(reason: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape = RoundedCornerShape(size = 8.dp))
            .background(color = BgSurface)
            .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(size = 8.dp))
            .padding(all = 14.dp),
        verticalArrangement = Arrangement.spacedBy(space = 4.dp),
    ) {
        Text(
            text = "⚠ This rule can only be edited in the code view",
            style = MaterialTheme.typography.subtitle1,
            color = TextPrimary,
        )
        Text(text = reason, style = MaterialTheme.typography.body2, color = TextSecondary)
    }
}

/** The floor the rule area keeps however short the window is — see the note on [BoardCanvas]. */
private val MIN_CANVAS_HEIGHT = 170.dp


/** How close to an edge the pointer must be, in pixels, before the canvas starts scrolling. */
private const val EDGE_BAND: Float = 48f

/** Pixels per frame while auto-scrolling — about 8 lines a second, fast enough to be useful. */
private const val SCROLL_STEP: Float = 12f
