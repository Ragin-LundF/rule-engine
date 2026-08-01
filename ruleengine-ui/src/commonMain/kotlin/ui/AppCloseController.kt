package ui

/**
 * Lets the window ask the editor whether it may close.
 *
 * The window owns closing but knows nothing about unsaved work; the editor knows about unsaved work
 * but does not own the window. This carries the request one way and the verdict the other, so
 * closing can raise the same save/discard/cancel question that opening a project does instead of
 * discarding edits silently.
 */
class AppCloseController {

    /** Installed by the editor once it is composed. */
    var onCloseRequested: (() -> Unit)? = null

    fun requestClose() {
        val handler = onCloseRequested
        // Nothing installed yet means nothing has been edited yet — closing straight away is safe.
        if (handler == null) confirmClose() else handler()
    }

    /** Installed by the window; invoked once the editor is satisfied that closing is safe. */
    var onCloseConfirmed: (() -> Unit)? = null

    fun confirmClose() {
        onCloseConfirmed?.invoke()
    }
}
