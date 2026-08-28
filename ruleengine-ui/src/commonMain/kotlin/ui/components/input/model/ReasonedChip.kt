package ui.components.input.model

/**
 * One choice in a [ui.components.input.ReasonedChipRow].
 *
 * [blockedReason] is what makes this different from a row of toggles: a value the engine does not allow
 * here is not simply absent. It may already be in the file — someone wrote it, or the field's type
 * changed under it — and hiding it would mean the editor silently disagreed with the schema on disk.
 */
data class ReasonedChip(
    val value: String,
    val selected: Boolean,
    /** Why this cannot be chosen, or null when it can. */
    val blockedReason: String? = null,
)
