package ruleengine.evaluator.context

import ruleengine.core.domain.FieldId

interface RuleContext {
    fun get(field: FieldId): Any?

    companion object {
        fun of(vararg entries: Pair<String, Any?>): RuleContext = MapRuleContext(entries.associate { it.first to it.second })
    }
}

private class MapRuleContext(private val map: Map<String, Any?>) : RuleContext {
    override fun get(field: FieldId): Any? = map[field.value]
}

