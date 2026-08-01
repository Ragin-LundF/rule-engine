package ruleengine.export

import ruleengine.export.dto.CatalogOutcome
import ruleengine.export.dto.PlainAll
import ruleengine.export.dto.PlainAny
import ruleengine.export.dto.PlainCondition
import ruleengine.export.dto.PlainLeaf
import ruleengine.export.dto.PlainNot

/**
 * The wording shared by every catalog exporter.
 *
 * Markdown and DOCX render the same document into different markup, so the prose — the lead-in
 * sentence, the bullet nesting, the one-line condition summary, the outcome label — has to be
 * identical between them or the two exports of one rule set describe it differently. Each format
 * owns only its own markup; everything they say in words lives here.
 */
object CatalogText {

    /**
     * The lead-in sentence, which carries the boolean structure the bullets below would otherwise
     * lose. No trailing punctuation: Markdown bolds it and adds a colon, DOCX makes it a styled
     * paragraph without one.
     */
    fun intro(condition: PlainCondition): String {
        return when (condition) {
            is PlainAll -> "Applies when all of the following are true"
            is PlainAny -> "Applies when any of the following is true"
            is PlainNot -> "Applies when the following is not true"
            is PlainLeaf -> "Applies when"
        }
    }

    /** One-line form of a condition, for an index or table where a bullet list will not fit. */
    fun flatten(condition: PlainCondition): String {
        return when (condition) {
            is PlainLeaf -> condition.text
            is PlainNot -> "not (${flatten(condition = condition.child)})"
            is PlainAll -> condition.children.joinToString(separator = " and ") { child ->
                flatten(condition = child)
            }

            is PlainAny -> condition.children.joinToString(separator = " or ") { child ->
                flatten(condition = child)
            }
        }
    }

    /** An outcome as one line: the action, plus its arguments when it has any. */
    fun label(outcome: CatalogOutcome): String {
        if (outcome.arguments.isEmpty()) {
            return outcome.action
        }

        return "${outcome.action} ${outcome.arguments.joinToString(separator = ", ")}"
    }

    /** `1 rule` / `4 rules` — pluralised by appending `s`, which fits every noun the catalog uses. */
    fun count(n: Int, singular: String): String {
        return if (n == 1) "1 $singular" else "$n ${singular}s"
    }

    /**
     * Walks [condition] as a bullet list, handing each bullet's text and indent depth to [emitBullet].
     *
     * [unwrapRoot] drops the outermost group's own bullet, because [intro] has already said "all of
     * the following" — nesting the whole rule one level deeper under a repeat of that sentence wastes
     * a level of indent on every rule in the document.
     */
    fun walk(
        condition: PlainCondition,
        depth: Int,
        unwrapRoot: Boolean = false,
        emitBullet: (text: String, depth: Int) -> Unit,
    ) {
        when (condition) {
            is PlainLeaf -> emitBullet(condition.text, depth)

            is PlainAll -> walkChildren(
                children = condition.children,
                depth = depth,
                unwrapRoot = unwrapRoot,
                header = "All of the following are true:",
                emitBullet = emitBullet,
            )

            is PlainAny -> walkChildren(
                children = condition.children,
                depth = depth,
                unwrapRoot = unwrapRoot,
                header = "Any of the following is true:",
                emitBullet = emitBullet,
            )

            is PlainNot -> {
                if (unwrapRoot) {
                    walk(condition = condition.child, depth = depth, emitBullet = emitBullet)
                } else {
                    emitBullet("The following is not true:", depth)
                    walk(condition = condition.child, depth = depth + 1, emitBullet = emitBullet)
                }
            }
        }
    }

    private fun walkChildren(
        children: List<PlainCondition>,
        depth: Int,
        unwrapRoot: Boolean,
        header: String,
        emitBullet: (text: String, depth: Int) -> Unit,
    ) {
        if (unwrapRoot) {
            children.forEach { child -> walk(condition = child, depth = depth, emitBullet = emitBullet) }
            return
        }

        emitBullet(header, depth)
        children.forEach { child ->
            walk(condition = child, depth = depth + 1, emitBullet = emitBullet)
        }
    }
}
