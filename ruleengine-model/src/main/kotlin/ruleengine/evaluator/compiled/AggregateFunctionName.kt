package ruleengine.evaluator.compiled

enum class AggregateFunctionName {
    COUNT,
    SUM,
    SUBTRACT,
    AVG,
    MEDIAN,
    MAX,
    MIN;

    companion object {

        /**
         * The function [name] denotes, case-insensitively, or null when it names none.
         *
         * The DSL accepts any casing — `count`, `COUNT` and `Count` are the same function — so every
         * stage that reads a function name off the AST resolves it through here rather than matching
         * on its own lowercased copy of the entry list.
         */
        fun fromName(name: String): AggregateFunctionName? {
            return entries.firstOrNull { entry -> entry.name.equals(other = name, ignoreCase = true) }
        }

        /** Every function name in the spelling the DSL and diagnostics use. */
        fun lowercaseNames(): List<String> {
            return entries.map { entry -> entry.name.lowercase() }
        }
    }
}
