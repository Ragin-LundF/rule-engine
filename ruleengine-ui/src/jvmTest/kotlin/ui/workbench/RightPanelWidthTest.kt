package ui.workbench

import androidx.compose.runtime.mutableStateOf
import ui.workbench.model.RuleWorkbenchState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The right panel's width, and the clamp that keeps a dragged one usable.
 *
 * The clamp is the whole reason [RightPanelController.setWidth] exists rather than the splitter writing
 * the state itself: a drag produces an unbounded stream of deltas, and a width outside the range is a
 * layout the user cannot drag their way out of — past the maximum the centre canvas is the narrow one,
 * and below the minimum the Inspector's own labels wrap until it stops being readable.
 */
class RightPanelWidthTest {

    // The savers are injected — the seam `RightPanelController` provides for exactly this — so the clamp
    // is asserted without writing the developer's, or the CI runner's, real preferences node. Written
    // there, a width from one test becomes the next run's startup layout on that machine, and the suite
    // stops meaning the same thing twice.
    private fun controllerWith(width: Float): Pair<RightPanelController, () -> Float> {
        val state = mutableStateOf(value = width)
        val controller = RightPanelController(
            expanded = mutableStateOf(value = true),
            width = state,
            viewModel = RuleWorkbenchViewModel(initialState = RuleWorkbenchState.Empty),
            saveExpanded = {},
            saveWidth = {},
            saveTab = {},
        )
        return controller to { state.value }
    }

    @Test
    fun `a width inside the range is taken as given`() {
        val (controller, read) = controllerWith(width = RightPanelPersistence.DEFAULT_WIDTH)

        controller.setWidth(value = 420f)

        assertEquals(expected = 420f, actual = read())
        assertEquals(expected = 420f, actual = controller.widthDp)
    }

    @Test
    fun `dragging past the maximum stops at the maximum`() {
        val (controller, read) = controllerWith(width = RightPanelPersistence.DEFAULT_WIDTH)

        controller.setWidth(value = 5_000f)

        assertEquals(expected = RightPanelPersistence.MAX_WIDTH, actual = read())
    }

    @Test
    fun `dragging past the minimum stops at the minimum`() {
        val (controller, read) = controllerWith(width = RightPanelPersistence.DEFAULT_WIDTH)

        controller.setWidth(value = 0f)

        assertEquals(expected = RightPanelPersistence.MIN_WIDTH, actual = read())
    }

    @Test
    fun `a negative width cannot invert the panel`() {
        val (controller, read) = controllerWith(width = RightPanelPersistence.DEFAULT_WIDTH)

        controller.setWidth(value = -800f)

        assertTrue(actual = read() > 0f)
        assertEquals(expected = RightPanelPersistence.MIN_WIDTH, actual = read())
    }

    @Test
    fun `the default sits inside the range it is clamped to`() {
        // A default outside its own bounds would be silently moved on the first launch, which would look
        // like the panel opening at the wrong size for no reason.
        assertTrue(
            actual = RightPanelPersistence.DEFAULT_WIDTH >= RightPanelPersistence.MIN_WIDTH &&
                RightPanelPersistence.DEFAULT_WIDTH <= RightPanelPersistence.MAX_WIDTH,
        )
    }

    @Test
    fun `a stored width from a wider display is clamped when it is read back`() {
        // Clamped on read as well as on write: a width saved on a large monitor would otherwise leave
        // nothing for the centre panel on a laptop, with the handle off the edge and no way to drag back.
        val restored = RightPanelPersistence.loadWidth()

        assertTrue(
            actual = restored in RightPanelPersistence.MIN_WIDTH..RightPanelPersistence.MAX_WIDTH,
            message = "loadWidth returned $restored, outside its own range",
        )
    }
}
