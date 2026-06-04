package ruleengine.compiler.operators

object OperatorUtils {
    @Suppress("CyclomaticComplexMethod")
    fun normalizeOperator(op: String): String {
        return when (op.lowercase()) {
            "==", "equals" -> "equals"
            "=", "eq" -> "equals"
            ">=", "gte" -> "gte"
            ">", "gt" -> "gt"
            "<=", "lte" -> "lte"
            "<", "lt" -> "lt"
            "contains" -> "contains"
            "startswith" -> "startsWith"
            "endswith" -> "endsWith"
            "in" -> "in"
            "containsany" -> "containsAny"
            "containsall" -> "containsAll"
            "regex", "matches", "regexp" -> "regex"
            "between" -> "between"
            else -> op
        }
    }
}
