package ruleengine.evaluator.compiled

import ruleengine.core.domain.FieldId
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.PreparedText

/**
 * Executes a regex extraction against a text field value at evaluation time.
 *
 * @param field      the context field to apply the pattern to
 * @param pattern    the compiled regular expression
 * @param groupIndex 1-based capture-group index (0 = whole match)
 */
class RegexExtractExpression(
    private val field: FieldId,
    private val pattern: Regex,
    private val groupIndex: Int
) {
    /**
     * Applies the regex to the original (un-normalised) text value of [field]
     * and returns the specified capture group, or `null` when the field is
     * absent, the pattern does not match, or the requested group does not exist.
     */
    fun extract(context: PreparedRuleContext): String? {
        val prepared = context.get(field = field) as? PreparedText ?: return null
        return runCatching {
            pattern.find(input = prepared.original)
                ?.groupValues
                ?.getOrNull(index = groupIndex)
        }.getOrNull()
    }
}

