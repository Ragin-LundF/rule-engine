package ui.components.header

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ui.BgSurface
import ui.BorderColor
import ui.TextMuted
import ui.TextPrimary
import ui.components.header.model.BarDensity
import ui.components.header.model.BindingSpec
import ui.components.header.model.HeaderAction

/** Every area header is this tall, whichever of its slots are filled. */
val AREA_HEADER_HEIGHT: Dp = 52.dp

/**
 * The one header above every editor area.
 *
 * Four slots, one order, in all four areas: **what this is** (title and how much of it), **what it is
 * bound to** (the file), **how it is being shown** (the mode tabs), and **what can be done to it** (the
 * actions, right-aligned). The panel body underneath is the only thing that differs between areas.
 *
 * It replaces four different headers: the Rules area's title-plus-toggle with a second row of buttons
 * underneath, the Schema and Actions areas' full-width linked-file bar above a tab strip, and the
 * Manifest area's bare tab strip. Three of those did not offer the actions at all, and each named the
 * same two modes differently.
 *
 * [tabs] is a slot taking the measured [BarDensity] rather than a list of modes: every area switches
 * over its own enum, and passing the density is what lets the strip drop to icons in step with the rest
 * of the bar instead of guessing at a width of its own. **The area owns the policy**, because the
 * strips are not the same size: a two-tab area can hold its labels down to [BarDensity.MINIMAL], while
 * the five-tab Rules strip has to drop to icons already at [BarDensity.COMPACT]. One global rule would
 * either strip a small header early or overflow a large one. [fullWidth] and [compactWidth] are the
 * other half of that: they say how much room *this* header needs before it can show everything.
 *
 * The height is fixed so that switching areas does not move the canvas underneath — the slots an area
 * fills are its own business, and a header that changes height with them makes every area switch a
 * small jump.
 */
@Composable
fun AreaHeader(
    title: String,
    tabs: @Composable (BarDensity) -> Unit,
    modifier: Modifier = Modifier,
    meta: String? = null,
    binding: BindingSpec? = null,
    onBindingItem: (String) -> Unit = {},
    subTabs: (@Composable (BarDensity) -> Unit)? = null,
    actions: List<HeaderAction> = emptyList(),
    onAction: (String) -> Unit = {},
    fullWidth: Dp = FULL_DENSITY_MIN_WIDTH,
    compactWidth: Dp = COMPACT_DENSITY_MIN_WIDTH,
) {
    Column(modifier = modifier.fillMaxWidth().background(color = BgSurface)) {
        BoxWithConstraints {
            val panelWidth = maxWidth
            val density = densityFor(width = panelWidth, fullWidth = fullWidth, compactWidth = compactWidth)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height = AREA_HEADER_HEIGHT)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(space = 10.dp),
            ) {
                HeaderIdentity(
                    title = title,
                    // Dropped before anything else gives way — see META_MIN_WIDTH.
                    meta = meta.takeIf { panelWidth >= META_MIN_WIDTH },
                    binding = binding,
                    onBindingItem = onBindingItem,
                    density = density,
                    tabs = tabs,
                    subTabs = subTabs,
                    modifier = Modifier.weight(weight = 1f),
                )
                HeaderActionRow(actions = actions, density = density, onAction = onAction)
            }
        }
        Divider(color = BorderColor, thickness = 1.dp)
    }
}

/**
 * The left-hand cluster: what this area is, what it is bound to, and how it is being shown.
 *
 * Weighted, so that when the bar runs short it is the file path that truncates and not the tabs that
 * fall off the edge.
 */
@Composable
private fun HeaderIdentity(
    title: String,
    meta: String?,
    binding: BindingSpec?,
    onBindingItem: (String) -> Unit,
    density: BarDensity,
    tabs: @Composable (BarDensity) -> Unit,
    subTabs: (@Composable (BarDensity) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.SemiBold),
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (meta != null) {
            Text(
                text = meta,
                style = MaterialTheme.typography.caption,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        binding?.let { spec ->
            // The elastic slot: it has a ceiling per density and truncates inside it, so when the
            // header runs short this is what gives. A truncated path still reads as a path, whereas
            // half a tab strip reads as a bug.
            BindingChip(spec = spec, onItem = onBindingItem, density = density)
        }
        VerticalRule()
        tabs(density)
        subTabs?.invoke(density)
    }
}

/** The hairline that separates what the area is from how it is being shown. */
@Composable
private fun VerticalRule() {
    Box(
        modifier = Modifier
            .width(width = 1.dp)
            .height(height = 22.dp)
            .background(color = BorderColor),
    )
}
