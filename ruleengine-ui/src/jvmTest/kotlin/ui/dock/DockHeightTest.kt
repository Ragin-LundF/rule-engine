package ui.dock

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import ui.dock.model.DockSurface
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The dock's clamp, and the thing the right panel's equivalent cannot do: respect a limit the window
 * imposes without writing that limit down.
 *
 * Hermetic, unlike `RightPanelWidthTest` — the recording lambdas mean nothing here touches the
 * developer's real preferences node.
 */
class DockHeightTest {

    private class Recorder {
        val heights = mutableListOf<Float>()
        val expanded = mutableListOf<Pair<DockSurface, Boolean>>()
    }

    private fun controllerWith(height: Float): Triple<DockController, () -> Float, Recorder> {
        val state = mutableStateOf(value = height)
        val recorder = Recorder()
        val controller = DockController(
            height = state,
            expanded = mutableStateMapOf(),
            tab = mutableStateMapOf(),
            saveHeight = { value -> recorder.heights.add(element = value) },
            saveExpanded = { surface, value -> recorder.expanded.add(element = surface to value) },
        )
        return Triple(controller, { state.value }, recorder)
    }

    @Test
    fun `a height inside the range is taken as given`() {
        val (controller, read, _) = controllerWith(height = DockPersistence.DEFAULT_HEIGHT)

        controller.setHeight(value = 300f)

        assertEquals(expected = 300f, actual = read())
        assertEquals(expected = 300f, actual = controller.heightDp)
    }

    @Test
    fun `dragging past the maximum stops at the maximum`() {
        val (controller, read, _) = controllerWith(height = DockPersistence.DEFAULT_HEIGHT)

        controller.setHeight(value = 5_000f)

        assertEquals(expected = DockPersistence.MAX_HEIGHT, actual = read())
    }

    @Test
    fun `dragging past the minimum stops at the minimum`() {
        val (controller, read, _) = controllerWith(height = DockPersistence.DEFAULT_HEIGHT)

        controller.setHeight(value = 0f)

        assertEquals(expected = DockPersistence.MIN_HEIGHT, actual = read())
    }

    @Test
    fun `a negative height cannot invert the dock`() {
        val (controller, read, _) = controllerWith(height = DockPersistence.DEFAULT_HEIGHT)

        controller.setHeight(value = -800f)

        assertTrue(actual = read() > 0f)
        assertEquals(expected = DockPersistence.MIN_HEIGHT, actual = read())
    }

    @Test
    fun `the default sits inside the range it is clamped to`() {
        assertTrue(
            actual = DockPersistence.DEFAULT_HEIGHT in DockPersistence.MIN_HEIGHT..DockPersistence.MAX_HEIGHT,
        )
    }

    // ── the ceiling, which is what the right panel has no equivalent of ───────

    @Test
    fun `a window ceiling below the maximum bounds the drag`() {
        val (controller, read, _) = controllerWith(height = 200f)

        controller.setHeight(value = 5_000f, ceiling = 260f)

        assertEquals(expected = 260f, actual = read())
    }

    /** A ceiling under the minimum would invert the range; the minimum wins instead. */
    @Test
    fun `a ceiling below the minimum yields the minimum rather than something smaller`() {
        val (controller, read, _) = controllerWith(height = 200f)

        controller.setHeight(value = 400f, ceiling = 10f)

        assertEquals(expected = DockPersistence.MIN_HEIGHT, actual = read())
    }

    /**
     * One writer: whatever the ceiling did to the value, the state and the saved value agree. A
     * divergence here would be a stored height that no window ever showed.
     */
    @Test
    fun `the saved height never diverges from the state`() {
        val (controller, read, recorder) = controllerWith(height = 200f)

        controller.setHeight(value = 5_000f, ceiling = 300f)
        controller.setHeight(value = 0f)
        controller.setHeight(value = 250f)

        assertEquals(expected = listOf(300f, DockPersistence.MIN_HEIGHT, 250f), actual = recorder.heights)
        assertEquals(expected = read(), actual = recorder.heights.last())
    }

    @Test
    fun `reset returns the default`() {
        val (controller, read, _) = controllerWith(height = DockPersistence.MAX_HEIGHT)

        controller.resetHeight()

        assertEquals(expected = DockPersistence.DEFAULT_HEIGHT, actual = read())
    }

    // ── open state, per surface ───────────────────────────────────────────────

    /** The decision the plan settled: the Builder's dock is the only one open on a first launch. */
    @Test
    fun `only the rules surface is open by default`() {
        val (controller, _, _) = controllerWith(height = DockPersistence.DEFAULT_HEIGHT)

        assertTrue(actual = controller.isExpanded(surface = DockSurface.RULES))
        assertFalse(actual = controller.isExpanded(surface = DockSurface.SCHEMA))
        assertFalse(actual = controller.isExpanded(surface = DockSurface.ACTIONS))
        assertFalse(actual = controller.isExpanded(surface = DockSurface.MANIFEST))
        assertEquals(
            expected = listOf(DockSurface.RULES),
            actual = DockSurface.entries.filter { surface -> surface.openByDefault },
        )
    }

    @Test
    fun `toggling one surface records it and leaves the others alone`() {
        val (controller, _, recorder) = controllerWith(height = DockPersistence.DEFAULT_HEIGHT)

        controller.toggleExpanded(surface = DockSurface.SCHEMA)

        assertTrue(actual = controller.isExpanded(surface = DockSurface.SCHEMA))
        assertTrue(actual = controller.isExpanded(surface = DockSurface.RULES))
        assertFalse(actual = controller.isExpanded(surface = DockSurface.ACTIONS))
        assertEquals(expected = listOf(DockSurface.SCHEMA to true), actual = recorder.expanded)
    }

    @Test
    fun `the selected tab defaults to the file tab and is remembered per surface`() {
        val (controller, _, _) = controllerWith(height = DockPersistence.DEFAULT_HEIGHT)

        assertEquals(expected = DockSurface.FILE_TAB_ID, actual = controller.selectedTab(surface = DockSurface.RULES))

        controller.selectTab(surface = DockSurface.RULES, tabId = "checks")

        assertEquals(expected = "checks", actual = controller.selectedTab(surface = DockSurface.RULES))
        assertEquals(
            expected = DockSurface.FILE_TAB_ID,
            actual = controller.selectedTab(surface = DockSurface.SCHEMA),
        )
    }
}
