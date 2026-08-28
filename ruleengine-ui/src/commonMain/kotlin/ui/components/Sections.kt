package ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.BorderColor
import ui.TextSecondary

/**
 * A section title label used as a header inside catalog panels.
 */
@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.subtitle1,
        color = TextSecondary,
        modifier = modifier.padding(bottom = 8.dp),
    )
}

/**
 * A rule between two sections, with enough room around it to read as a break.
 *
 * The space is the larger half of what this does; the line only makes the break look deliberate rather
 * than like a gap someone forgot to close. Both are needed: at ordinary item spacing, two sections of
 * boxes read as one continuous list, which is the wrong reading whenever the sections answer different
 * questions — as the Builder's `when` and its outcomes do.
 *
 * [SECTION_GAP] is applied on top of whatever spacing the surrounding column already has, so the break
 * comes out at roughly three times any gap inside either section.
 */
@Composable
fun SectionDivider(modifier: Modifier = Modifier) {
    Divider(
        color = BorderColor,
        thickness = 1.dp,
        modifier = modifier.fillMaxWidth().padding(vertical = SECTION_GAP),
    )
}

/** Breathing room either side of a [SectionDivider]. */
private val SECTION_GAP = 12.dp
