package ruleengine.compiler.operators

import ruleengine.core.domain.OperatorNames

/**
 * Reduces an operator as written in a rule or a schema to its canonical name.
 *
 * The spellings themselves live in [OperatorNames]; this is only the lookup, so that everything
 * downstream can match on one name per operator instead of on every alias of it.
 */
object OperatorUtils {

    fun normalizeOperator(op: String): String {
        return OperatorNames.CANONICAL[op.lowercase()] ?: op
    }

    /**
     * True when [op] names an operator the engine can compile.
     *
     * `!=` is included even though no field type lists it: the parser routes a symbolic inequality through
     * the expression engine, so a schema may legitimately declare it.
     */
    fun isKnownOperator(op: String): Boolean {
        return OperatorNames.CANONICAL.containsKey(op.lowercase()) || op == OperatorNames.SYMBOL_NOT_EQUALS
    }
}
