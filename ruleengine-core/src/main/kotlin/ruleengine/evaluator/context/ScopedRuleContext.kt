package ruleengine.evaluator.context

import ruleengine.core.domain.dto.field.FieldId

/**
 * One member of the scoped collection, with the whole document behind it.
 *
 * A scoped rule set is written from the member's point of view — `balance` means *this* account's
 * balance — but a rule may still need something the document carries once for all members, such as a
 * shared threshold or a watch list. Names resolve against the member first and fall through to
 * [document] only when the member does not carry them.
 *
 * The member wins on key presence rather than on value: a member that explicitly holds null means
 * the value is absent for that member, not that the document's value should stand in for it.
 */
class ScopedRuleContext(
    private val member: Map<*, *>,
    private val document: RuleContext
) : RuleContext {
    override fun get(field: FieldId): Any? {
        if (member.containsKey(key = field.value)) {
            return member[field.value]
        }
        return document.get(field = field)
    }

    override fun getRaw(fieldPath: List<String>): Any? {
        if (fieldPath.isEmpty()) {
            return member
        }
        if (!member.containsKey(key = fieldPath.first())) {
            return document.getRaw(fieldPath = fieldPath)
        }
        var current: Any? = member
        for (key in fieldPath) {
            current = when (current) {
                is Map<*, *> -> current[key]
                else -> return null
            }
        }
        return current
    }
}
