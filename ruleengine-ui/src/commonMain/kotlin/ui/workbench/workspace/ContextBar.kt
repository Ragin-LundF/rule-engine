package ui.workbench.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ui.AccentGreen
import ui.AccentOrange
import ui.BgHover
import ui.BorderColor
import ui.PrimaryBlue
import ui.PrimaryGlow
import ui.TextPrimary
import ui.TextSecondary
import ui.workbench.model.InspectorItem
import ui.workbench.model.workspace.Crumb

/**
 * The one bar above the workspace: where you are, what you are editing, and whether the file is valid.
 *
 * It replaces three surfaces that each answered part of that — the formula bar (the selected row as
 * text), the diagnostics strip under the window, and the Inspector's own trail. One bar, read left to
 * right, answers "which file, which rule, which condition, which side" and then "what can I do" and
 * "is anything wrong", in that order.
 *
 * The trail wraps rather than scrolls or clips: a narrow window gets a two-line bar, never a bar whose
 * last crumb — the one that says what is being edited — is cut off.
 */
@OptIn(ExperimentalLayoutApi::class)
@Suppress("FunctionNaming", "LongParameterList")
@Composable
fun ContextBar(
    crumbs: List<Crumb>,
    editing: String?,
    checks: Int,
    onCrumb: (InspectorItem) -> Unit,
    onChecks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 12.dp),
    ) {
        FlowRow(
            modifier = Modifier.weight(weight = 1f),
            horizontalArrangement = Arrangement.spacedBy(space = 2.dp),
            verticalArrangement = Arrangement.spacedBy(space = 2.dp),
        ) {
            crumbs.forEachIndexed { index, crumb ->
                if (index > 0) {
                    Text(
                        text = "›",
                        style = MaterialTheme.typography.caption,
                        color = TextSecondary,
                        modifier = Modifier.align(alignment = Alignment.CenterVertically),
                    )
                }
                CrumbButton(crumb = crumb, current = index == crumbs.lastIndex, onCrumb = onCrumb)
            }
        }
        if (editing != null) {
            EditingMode(editing = editing)
        }
        ChecksCounter(checks = checks, onClick = onChecks)
    }
}

/** One part of the trail. The last is where you are, so it is the one that stands out. */
@Suppress("FunctionNaming")
@Composable
private fun CrumbButton(crumb: Crumb, current: Boolean, onCrumb: (InspectorItem) -> Unit) {
    val target = crumb.target
    val base = Modifier.clip(shape = RoundedCornerShape(size = 6.dp))
    val clickable = if (target != null && !current) base.clickable { onCrumb(target) } else base
    Text(
        text = crumb.label,
        style = MaterialTheme.typography.body2.copy(
            fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
        ),
        color = if (current) PrimaryBlue else TextPrimary,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = clickable
            .background(color = if (current) PrimaryGlow else Color.Transparent)
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

/** "Editing left value · Esc up one level" — the mode, and the way out of it. */
@Suppress("FunctionNaming")
@Composable
private fun EditingMode(editing: String) {
    Row(
        modifier = Modifier
            .clip(shape = RoundedCornerShape(percent = 50))
            .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(percent = 50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 6.dp),
    ) {
        Text(
            text = "Editing",
            style = MaterialTheme.typography.caption,
            color = TextSecondary,
            softWrap = false,
        )
        Text(
            text = editing,
            style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
            color = TextPrimary,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "Esc",
            style = MaterialTheme.typography.caption,
            color = TextSecondary,
            softWrap = false,
            modifier = Modifier
                .clip(shape = RoundedCornerShape(size = 4.dp))
                .background(color = BgHover)
                .padding(horizontal = 4.dp),
        )
        Text(
            text = "up one level",
            style = MaterialTheme.typography.caption,
            color = TextSecondary,
            softWrap = false,
        )
    }
}

/**
 * The single checks counter. Clicking it opens the drawer's Checks tab, which is where they are listed.
 *
 * One count in one place: the same problems used to be reported by the diagnostics strip, the status bar
 * and the dock at once, and three counters are three chances to disagree.
 */
@Suppress("FunctionNaming")
@Composable
private fun ChecksCounter(checks: Int, onClick: () -> Unit) {
    val colour = if (checks == 0) AccentGreen else AccentOrange
    Text(
        text = if (checks == 0) "✓ Valid" else "⚠ $checks to fix",
        style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
        color = colour,
        softWrap = false,
        modifier = Modifier
            .clip(shape = RoundedCornerShape(percent = 50))
            .background(color = colour.copy(alpha = 0.12f))
            .border(
                width = 1.dp,
                color = colour.copy(alpha = 0.4f),
                shape = RoundedCornerShape(percent = 50),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
