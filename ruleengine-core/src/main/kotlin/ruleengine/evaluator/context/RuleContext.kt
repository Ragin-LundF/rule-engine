package ruleengine.evaluator.context

import ruleengine.core.domain.dto.field.FieldId

interface RuleContext {
    fun get(field: FieldId): Any?

    fun getRaw(fieldPath: List<String>): Any?

    companion object {
        fun of(vararg entries: Pair<String, Any?>): RuleContext {
            return MapRuleContext(map = entries.associate { it.first to it.second })
        }
    }
}

