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

    override fun getRaw(fieldPath: List<String>): Any? {
        return resolveRaw(current = map, path = fieldPath, index = 0)
    }

    private fun resolveRaw(current: Any?, path: List<String>, index: Int): Any? {
        if (index >= path.size) {
            return current
        }
        val key = path[index]
        return when (current) {
            is Map<*, *> -> resolveRaw(current = current[key], path = path, index = index + 1)
            is Collection<*> -> {
                val results = current.mapNotNull { element ->
                    val resolved = resolveRaw(current = element, path = path, index = index)
                    resolved
                }
                results.ifEmpty { null }
            }
            is Array<*> -> {
                val results = current.mapNotNull { element ->
                    val resolved = resolveRaw(current = element, path = path, index = index)
                    resolved
                }
                results.ifEmpty { null }
            }
            else -> null
        }
    }
}
