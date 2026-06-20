package ruleengine.evaluator.context

import ruleengine.core.domain.FieldId

class ElementRuleContext(private val element: Map<*, *>) : RuleContext {
    override fun get(field: FieldId): Any? {
        return element[field.value]
    }

    override fun getRaw(fieldPath: List<String>): Any? {
        if (fieldPath.isEmpty()) {
            return element
        }
        var current: Any? = element
        for (key in fieldPath) {
            current = when (current) {
                is Map<*, *> -> current[key]
                else -> return null
            }
        }
        return current
    }
}
