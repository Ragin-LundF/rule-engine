package ui.components.header

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ui.components.header.model.BarDensity

/**
 * At or above this, a header also shows its meta line — the quiet count beside the title.
 *
 * Higher than [FULL_DENSITY_MIN_WIDTH] because the count is the only decoration in the bar, and the
 * bar's elastic slot is a file path: below this the room is better spent on the path than on "14 rules
 * · 4 files", which says nothing the panel underneath does not.
 */
val META_MIN_WIDTH: Dp = 1_240.dp

/** At or above this, a header shows every label it has. */
val FULL_DENSITY_MIN_WIDTH: Dp = 900.dp

/** At or above this, only the secondary actions give up their labels. */
val COMPACT_DENSITY_MIN_WIDTH: Dp = 620.dp

/**
 * How much of itself a bar of [width] can show.
 *
 * A function rather than a `when` inside the header so the thresholds are one fact with one test,
 * instead of a pair of numbers repeated in every bar that needs to shrink.
 *
 * The thresholds are arguments because headers are not the same size: the defaults suit a two-tab area
 * with a couple of actions, while the five-tab Rules header needs a good 300 dp more before it can hold
 * every label. A single global pair would either strip the small headers early or overflow the large
 * one — and overflowing is what this whole mechanism exists to prevent.
 */
fun densityFor(
    width: Dp,
    fullWidth: Dp = FULL_DENSITY_MIN_WIDTH,
    compactWidth: Dp = COMPACT_DENSITY_MIN_WIDTH,
): BarDensity {
    return when {
        width >= fullWidth -> BarDensity.FULL
        width >= compactWidth -> BarDensity.COMPACT
        else -> BarDensity.MINIMAL
    }
}
