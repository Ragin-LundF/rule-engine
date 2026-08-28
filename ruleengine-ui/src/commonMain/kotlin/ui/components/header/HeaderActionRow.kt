package ui.components.header

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ui.components.ToolbarButton
import ui.components.header.model.ActionEmphasis
import ui.components.header.model.BarDensity
import ui.components.header.model.HeaderAction

/**
 * The right-hand end of a header: the actions that belong to the area in its current mode.
 *
 * Ranked rather than merely listed. The row this replaces shared one line with the mode tabs, which
 * are fixed-width, so every shortfall came out of the actions and the last button was squeezed until
 * its label wrapped one letter per line. Here the primary verb is never abbreviated, the secondary ones
 * shrink to their icons, and the rare ones were never on the bar to begin with.
 */
@Composable
internal fun HeaderActionRow(
    actions: List<HeaderAction>,
    density: BarDensity,
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // A standard action with no glyph cannot shrink: at anything but full width it moves into the
    // menu instead. The alternative is what the first pass did — hand it a thin arrow glyph nobody can
    // read at 12sp and call that a collapsed button.
    val inline = actions.filter { action -> action.isInline(density = density) }
    val overflow = actions.filterNot { action -> action.isInline(density = density) }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        inline.forEach { action ->
            InlineAction(action = action, density = density, onAction = onAction)
        }
        if (overflow.isNotEmpty()) {
            OverflowMenu(actions = overflow, onAction = onAction)
        }
    }
}

/** Whether this action still earns a place on the bar at [density]. */
private fun HeaderAction.isInline(density: BarDensity): Boolean {
    return when (emphasis) {
        ActionEmphasis.PRIMARY -> true
        ActionEmphasis.OVERFLOW -> false
        ActionEmphasis.STANDARD -> density == BarDensity.FULL || icon != null
    }
}

/**
 * One action on the bar.
 *
 * A collapsed button keeps its label in the semantics tree rather than dropping it: the glyph is what
 * is drawn, but the name is what a screen reader — and a test — has to go on.
 */
@Composable
private fun InlineAction(action: HeaderAction, density: BarDensity, onAction: (String) -> Unit) {
    val collapses = action.emphasis == ActionEmphasis.STANDARD &&
        density != BarDensity.FULL &&
        action.icon != null
    val label = when {
        collapses -> action.icon.orEmpty()
        action.icon != null -> "${action.icon} ${action.label}"
        else -> action.label
    }

    ToolbarButton(
        label = label,
        onClick = { onAction(action.id) },
        modifier = Modifier
            .semantics { contentDescription = action.label }
            // A glyph does not need a button's worth of width around it.
            .then(other = if (collapses) Modifier.width(width = COLLAPSED_ACTION_WIDTH) else Modifier),
        primary = action.emphasis == ActionEmphasis.PRIMARY,
        enabled = action.enabled,
    )
}

/** The `⋯` menu: the actions that are worth offering but not worth a place on the bar. */
@Composable
private fun OverflowMenu(actions: List<HeaderAction>, onAction: (String) -> Unit) {
    var expanded by remember { mutableStateOf(value = false) }

    Box {
        ToolbarButton(
            label = "⋯",
            onClick = { expanded = true },
            modifier = Modifier.semantics { contentDescription = "More actions" },
        )
        HeaderMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            actions.forEach { action ->
                HeaderMenuItem(
                    label = action.label,
                    enabled = action.enabled,
                    onClick = {
                        expanded = false
                        onAction(action.id)
                    },
                )
            }
        }
    }
}

/** Wide enough for a glyph and its padding, and no wider. */
private val COLLAPSED_ACTION_WIDTH: Dp = 46.dp
