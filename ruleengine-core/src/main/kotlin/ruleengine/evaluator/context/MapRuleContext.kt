package ruleengine.evaluator.context

import ruleengine.core.domain.FieldId

class MapRuleContext(private val map: Map<String, Any?>) : RuleContext {
    override fun get(field: FieldId): Any? {
        val keys = field.value.split('.')
        var current: Any? = map
        for (key in keys) {
            if (current is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                current = current[key]
            } else {
                return null
            }
        }
        return current
    }
}
