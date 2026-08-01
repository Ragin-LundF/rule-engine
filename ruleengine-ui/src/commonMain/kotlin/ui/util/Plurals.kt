package ui.util

/** English pluralisation for the counts shown in labels and chips. */
object Plurals {

    /** `""` for one, `"s"` otherwise — so `"$n rule${suffix(n)}"` reads correctly either way. */
    fun suffix(count: Int): String {
        return if (count == 1) "" else "s"
    }
}
