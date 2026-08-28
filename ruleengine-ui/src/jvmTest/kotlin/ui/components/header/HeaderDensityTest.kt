package ui.components.header

import androidx.compose.ui.unit.dp
import ui.components.header.model.BarDensity
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The two widths at which a header gives something up.
 *
 * Asserted on the boundaries rather than in the middle of each band: an off-by-one here is a bar that
 * drops its labels a pixel early or a window that overflows a pixel late, and neither is visible in a
 * screenshot taken at a round number.
 */
class HeaderDensityTest {

    @Test
    fun `a wide panel shows everything`() {
        assertEquals(expected = BarDensity.FULL, actual = densityFor(width = 1_440.dp))
        assertEquals(expected = BarDensity.FULL, actual = densityFor(width = FULL_DENSITY_MIN_WIDTH))
    }

    @Test
    fun `just under the full width the secondary actions give up their labels`() {
        assertEquals(
            expected = BarDensity.COMPACT,
            actual = densityFor(width = FULL_DENSITY_MIN_WIDTH - 1.dp),
        )
        assertEquals(expected = BarDensity.COMPACT, actual = densityFor(width = COMPACT_DENSITY_MIN_WIDTH))
    }

    @Test
    fun `a header can say it needs more room than the default`() {
        // The five-tab Rules header does. Judged by the defaults it would call 1100 dp roomy and then
        // overflow; judged by its own it drops to icons and fits.
        assertEquals(
            expected = BarDensity.COMPACT,
            actual = densityFor(width = 1_100.dp, fullWidth = 1_280.dp, compactWidth = 1_000.dp),
        )
        assertEquals(expected = BarDensity.FULL, actual = densityFor(width = 1_100.dp))
    }

    @Test
    fun `below the compact width the tabs give up theirs too`() {
        assertEquals(
            expected = BarDensity.MINIMAL,
            actual = densityFor(width = COMPACT_DENSITY_MIN_WIDTH - 1.dp),
        )
        assertEquals(expected = BarDensity.MINIMAL, actual = densityFor(width = 0.dp))
    }
}
