package ui

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The window-level save chord.
 *
 * Consuming the event matters as much as firing the handler: the DSL and YAML editors install their
 * own key handlers, and an unconsumed `Cmd + S` would reach the focused field and be typed there.
 */
class AppSaveControllerTest {

    private var saves = 0
    private val controller = AppSaveController().apply { onSaveRequested = { saves++ } }

    // The synthesising constructor is the only way to build a `KeyEvent` without an AWT event and a
    // window to have produced it; it is opted into here and nowhere in the app itself.
    @OptIn(InternalComposeUiApi::class)
    @Suppress("LongParameterList")
    private fun keyEvent(
        key: Key = Key.S,
        type: KeyEventType = KeyEventType.KeyDown,
        ctrl: Boolean = false,
        meta: Boolean = false,
        alt: Boolean = false,
        shift: Boolean = false,
    ): KeyEvent = KeyEvent(
        key = key,
        type = type,
        isCtrlPressed = ctrl,
        isMetaPressed = meta,
        isAltPressed = alt,
        isShiftPressed = shift,
    )

    @Test
    fun `cmd S saves and is consumed`() {
        assertTrue(actual = controller.handleKey(event = keyEvent(meta = true)))
        assertEquals(expected = 1, actual = saves)
    }

    /** The Windows and Linux spelling of the same shortcut. */
    @Test
    fun `ctrl S saves and is consumed`() {
        assertTrue(actual = controller.handleKey(event = keyEvent(ctrl = true)))
        assertEquals(expected = 1, actual = saves)
    }

    /** Otherwise one press saves twice, and the second run works against an already-clean project. */
    @Test
    fun `the matching key-up is ignored`() {
        val released = keyEvent(type = KeyEventType.KeyUp, meta = true)

        assertFalse(actual = controller.handleKey(event = released))
        assertEquals(expected = 0, actual = saves)
    }

    @Test
    fun `an unmodified S is left to the editor to type`() {
        assertFalse(actual = controller.handleKey(event = keyEvent()))
        assertEquals(expected = 0, actual = saves)
    }

    @Test
    fun `another key with the modifier held is not the save chord`() {
        assertFalse(actual = controller.handleKey(event = keyEvent(key = Key.C, meta = true)))
        assertEquals(expected = 0, actual = saves)
    }

    /** Left free for Save As, so it must not be swallowed as a plain save. */
    @Test
    fun `cmd shift S is not the save chord`() {
        assertFalse(actual = controller.handleKey(event = keyEvent(meta = true, shift = true)))
        assertEquals(expected = 0, actual = saves)
    }

    @Test
    fun `cmd alt S is not the save chord`() {
        assertFalse(actual = controller.handleKey(event = keyEvent(meta = true, alt = true)))
        assertEquals(expected = 0, actual = saves)
    }

    /**
     * Before the editor is composed there is nothing that can save. Reporting the event unhandled
     * lets it fall through rather than vanishing.
     */
    @Test
    fun `with no handler installed the event is not consumed`() {
        val bare = AppSaveController()

        assertFalse(actual = bare.handleKey(event = keyEvent(meta = true)))
    }
}
