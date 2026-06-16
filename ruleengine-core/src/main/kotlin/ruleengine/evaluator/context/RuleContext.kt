package ruleengine.evaluator.context

import ruleengine.core.domain.FieldId

interface RuleContext {
    fun get(field: FieldId): Any?

    companion object {
        fun of(vararg entries: Pair<String, Any?>): RuleContext {
            return MapRuleContext(entries.associate { it.first to it.second })
        }
    }
}

private class MapRuleContext(private val map: Map<String, Any?>) : RuleContext {
    override fun get(field: FieldId): Any? {
        val keys = field.value.split('.')
        var current: Any? = map
        for (key in keys) {
            if (current is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                current = current[key] as Any?
            } else {
                return null
            }
        }
        return current
    }
}


