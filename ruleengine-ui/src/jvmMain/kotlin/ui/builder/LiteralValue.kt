package ui.builder
/** Internal holder for a decomposed literal value. */
internal data class LiteralValue(
    val value: String = "",
    val valueTo: String = "",
    val listItems: List<String> = emptyList(),
)
