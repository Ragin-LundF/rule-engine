package ui.util

/** Turns free text into a lowercase, hyphen-separated token safe to use in a file or entry name. */
object Slugs {

    private val NON_ALPHANUMERIC = Regex(pattern = "[^a-z0-9]+")

    /**
     * [value] lowercased, with every run of non-alphanumeric characters collapsed to a single `-`
     * and leading/trailing hyphens removed.
     *
     * [fallback] is returned when nothing survives — a name written entirely in a non-Latin script
     * would otherwise produce an empty file name.
     */
    fun slugify(value: String, fallback: String): String {
        val cleaned = value.lowercase()
            .replace(regex = NON_ALPHANUMERIC, replacement = "-")
            .trim('-')

        return cleaned.ifEmpty { fallback }
    }
}
