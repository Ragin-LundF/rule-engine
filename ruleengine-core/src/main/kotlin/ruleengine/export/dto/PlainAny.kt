package ruleengine.export.dto

/** At least one child must hold — the DSL's `or`. */
data class PlainAny(val children: List<PlainCondition>) : PlainCondition
