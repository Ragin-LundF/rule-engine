package ruleengine.export.dto

/** Every child must hold — the DSL's `and`. */
data class PlainAll(val children: List<PlainCondition>) : PlainCondition
