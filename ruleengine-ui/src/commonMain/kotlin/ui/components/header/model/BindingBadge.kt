package ui.components.header.model

/** A short marker on a binding chip — "shared", "not found" — with the tone that colours it. */
data class BindingBadge(
    val text: String,
    val tone: BadgeTone = BadgeTone.INFO,
)
