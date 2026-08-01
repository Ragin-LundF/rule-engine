package ui.diagrams

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Small pieces shared by the diagram views. Grouped in one file the way [Nodes] groups the node
 * composables, so the views stay about layout rather than about restating the same border-and-pad
 * recipe.
 */

/** A short label above a section: uppercase, tracked out, muted. */
@Composable
internal fun DiagramEyebrow(text: String, color: Color = TextDesc, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = TextStyle(
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.5.sp,
            color = color,
        ),
    )
}

/** A bordered pill, used for file names, schema references and outcome values. */
@Composable
internal fun DiagramChip(
    text: String,
    textColor: Color = TextDesc,
    borderColor: Color = BorderCondition,
    background: Color = NodeBgCondition,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(shape = RoundedCornerShape(size = 4.dp))
            .background(color = background)
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(size = 4.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            style = TextStyle(fontSize = 10.sp, color = textColor, fontFamily = FontFamily.Monospace),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** One line of rendered DSL, the label used wherever a condition has to fit on a single row. */
@Composable
internal fun DiagramConditionLine(
    text: String,
    color: Color = TextDesc,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        style = TextStyle(fontSize = 11.sp, color = color, fontFamily = FontFamily.Monospace),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** A monospace identifier: a rule id, a field path, an action name. */
@Composable
internal fun DiagramIdentifier(
    text: String,
    color: Color,
    fontSize: Int = 12,
    weight: FontWeight = FontWeight.Medium,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        style = TextStyle(
            fontSize = fontSize.sp,
            fontWeight = weight,
            color = color,
            fontFamily = FontFamily.Monospace,
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** An explanatory aside: the notes that keep a view from implying more than the engine does. */
@Composable
internal fun DiagramNote(text: String, color: Color = TextDesc, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = TextStyle(fontSize = 10.sp, color = color),
    )
}

/** Plural suffix for the counts the views print. */
internal fun plural(count: Int): String {
    if (count == 1) {
        return ""
    }
    return "s"
}

/**
 * The scrolling dark canvas a diagram sits on.
 *
 * Shared so a diagram embedded outside Diagram mode — the schema and action usage panels — gets the
 * same ground and the same scrolling as the one in the editor, instead of inheriting the surrounding
 * form's background and being clipped.
 *
 * Vertical scrolling only, deliberately. A horizontal scroll leaves children with an unbounded width
 * constraint, under which `Modifier.weight` resolves to zero — the field flow's three columns
 * collapsed to one character per line. Views keep themselves inside the viewport instead, by
 * eliding long labels.
 */
@Composable
internal fun DiagramSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(color = DiagramBg)
            .verticalScroll(state = rememberScrollState()),
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(all = 20.dp)) {
            content()
        }
    }
}
