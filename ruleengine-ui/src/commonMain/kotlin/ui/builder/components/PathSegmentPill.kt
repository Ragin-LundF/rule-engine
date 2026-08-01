package ui.builder.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ui.AccentOrange
import ui.BgElevated
import ui.BgHover
import ui.BorderColor
import ui.PrimaryBlue
import ui.PrimaryBlueLight
import ui.TextPrimary
import ui.TextSecondary

/** Fully rounded, so a segment reads as one indivisible step of the path. */
private val PillShape = RoundedCornerShape(percent = 50)

/**
 * One segment of a field path, drawn as a breadcrumb pill.
 *
 * A path is only ever assembled from schema-declared members, so the name is never typeable: with
 * [options] the pill is a picker, and without them — the segment sits below a member the schema does
 * not declare — it is read-only and marked, which preserves the name the rule text carried.
 *
 * Clicking the pill selects it, which is what opens its `where` drawer in [PathBreadcrumb], and opens
 * the member menu when there is one to open.
 */
// 81 lines against a threshold of 60, and flat: a Row whose five children are each an optional
// Text — the name, the filter count, the unknown marker, the chevron, the remove button. Splitting
// would mean a composable per Text, which is more to read than the tree it replaces. The one part
// with substance, the name menu, is already `OptionMenu`.
@Suppress("LongMethod")
@Composable
fun PathSegmentPill(
    name: String,
    options: List<String>,
    filterCount: Int,
    selected: Boolean,
    onNameSelected: (String) -> Unit,
    onSelected: () -> Unit,
    onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(value = false) }
    val declared = name in options

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(shape = PillShape)
                .background(color = if (selected) BgHover else BgElevated)
                .border(
                    width = 1.dp,
                    color = when {
                        selected -> PrimaryBlue
                        !declared -> AccentOrange.copy(alpha = 0.45f)
                        else -> BorderColor
                    },
                    shape = PillShape,
                )
                .clickable {
                    onSelected()
                    if (options.isNotEmpty()) menuOpen = !menuOpen
                }
                .padding(
                    start = 13.dp,
                    end = if (onRemove == null) 13.dp else 6.dp,
                    top = 6.dp,
                    bottom = 6.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(space = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = name.ifBlank { "…" },
                style = MaterialTheme.typography.body2,
                color = if (declared) TextPrimary else AccentOrange,
            )

            // Keeps a restriction visible while its drawer is closed.
            if (filterCount > 0) {
                Text(
                    text = filterCount.toString(),
                    style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = PrimaryBlueLight,
                    modifier = Modifier
                        .clip(shape = PillShape)
                        .background(color = PrimaryBlue.copy(alpha = 0.16f))
                        .padding(horizontal = 7.dp),
                )
            }

            if (!declared) {
                Text(
                    text = UNKNOWN_MARKER,
                    style = MaterialTheme.typography.caption,
                    color = AccentOrange,
                )
            }

            if (options.isNotEmpty()) {
                Text(
                    text = "▾",
                    style = MaterialTheme.typography.caption,
                    color = if (selected) PrimaryBlue else TextSecondary,
                )
            }

            if (onRemove != null) {
                Text(
                    text = "×",
                    style = MaterialTheme.typography.body2,
                    color = TextSecondary,
                    modifier = Modifier
                        .clip(shape = PillShape)
                        .clickable(onClick = onRemove)
                        .padding(horizontal = 5.dp),
                )
            }
        }

        OptionMenu(
            expanded = menuOpen,
            options = options,
            selected = name,
            onSelected = onNameSelected,
            onDismiss = { menuOpen = false },
        )
    }
}
