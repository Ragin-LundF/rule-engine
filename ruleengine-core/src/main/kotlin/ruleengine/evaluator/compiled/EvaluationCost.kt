package ruleengine.evaluator.compiled

/** Roughly what an expression costs to evaluate, used to order cheap tests first. */
enum class EvaluationCost { VERY_CHEAP, CHEAP, MEDIUM, EXPENSIVE }
