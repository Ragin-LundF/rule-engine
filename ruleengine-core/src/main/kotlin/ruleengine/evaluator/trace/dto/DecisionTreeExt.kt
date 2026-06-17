package ruleengine.evaluator.trace.dto

import ruleengine.jackson.JacksonUtil

fun DecisionTree.toJson(): String {
    return JacksonUtil.jsonMapper.writeValueAsString(this)
}
