package ruleengine.compiler.operators

object OperatorUtils {

    /**
     * Every spelling of an operator the engine understands, mapped to its canonical name.
     *
     * The `snake_case` entries exist because earlier versions of the visual schema editor wrote them into
     * `operators:` lists. Recognising them keeps those schemas working — without the alias a
     * `starts_with` declaration would restrict the field to a name no condition can ever match.
     */
    private val CANONICAL: Map<String, String> = mapOf(
        "==" to "equals",
        "equals" to "equals",
        "=" to "equals",
        "eq" to "equals",
        ">=" to "gte",
        "gte" to "gte",
        ">" to "gt",
        "gt" to "gt",
        "<=" to "lte",
        "lte" to "lte",
        "<" to "lt",
        "lt" to "lt",
        "contains" to "contains",
        "startswith" to "startsWith",
        "starts_with" to "startsWith",
        "endswith" to "endsWith",
        "ends_with" to "endsWith",
        "in" to "in",
        "containsany" to "containsAny",
        "containsall" to "containsAll",
        "regex" to "regex",
        "matches" to "regex",
        "regexp" to "regex",
        "between" to "between",
    )

    fun normalizeOperator(op: String): String {
        return CANONICAL[op.lowercase()] ?: op
    }

    /**
     * True when [op] names an operator the engine can compile.
     *
     * `!=` is included even though no field type lists it: the parser routes a symbolic inequality through
     * the expression engine, so a schema may legitimately declare it.
     */
    fun isKnownOperator(op: String): Boolean {
        return CANONICAL.containsKey(op.lowercase()) || op == "!="
    }
}
