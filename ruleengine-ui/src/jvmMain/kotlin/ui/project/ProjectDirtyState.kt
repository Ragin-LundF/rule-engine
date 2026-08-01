package ui.project

/**
 * What each buffer looked like the last time it was read from or written to disk.
 *
 * Dirtiness is a string compare against that baseline rather than a flag set on every keystroke,
 * because a flag cannot tell "edited" from "edited back to what it was", and the baseline is needed
 * anyway to notice that a file changed underneath the editor.
 */
class ProjectDirtyState {

    companion object {
        const val SCHEMA: String = "schema"
        const val ACTIONS: String = "actions"

        /** Rule files are keyed by their manifest-relative path, since a project has many. */
        fun ruleKey(relativePath: String): String = "rule:$relativePath"
    }

    private val baselines: MutableMap<String, String> = mutableMapOf()

    fun markClean(key: String, content: String) {
        baselines[key] = content
    }

    fun isDirty(key: String, content: String): Boolean {
        val baseline = baselines[key] ?: return content.isNotBlank()
        return baseline != content
    }

    /** Null when the buffer was never read from disk — a file this session created, for example. */
    fun baseline(key: String): String? = baselines[key]

    fun forget(key: String) {
        baselines.remove(key)
    }

    fun clear() {
        baselines.clear()
    }
}
