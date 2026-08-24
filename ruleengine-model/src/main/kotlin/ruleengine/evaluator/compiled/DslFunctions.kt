package ruleengine.evaluator.compiled

/**
 * The DSL's function vocabulary, for surfaces that need names rather than behaviour.
 *
 * Two lists, and they are deliberately different. Everything the parser accepts must be highlighted
 * and completed, while the visual editor's aggregate picker offers only reductions over a collection
 * — offering `daysBetween` there would ask the author for a collection it cannot use.
 */
object DslFunctions {

    /**
     * The slice functions. They are absent from every registry because they never become a function
     * call: the parser turns `take(orders, 3)` into a path segment. They still have to be listed
     * here, or the editor would treat the word as an unknown identifier.
     */
    val SLICE_NAMES: List<String> = listOf("take", "takeLast")

    /**
     * The ordering function, absent from every registry for the same reason as [SLICE_NAMES]: the
     * parser turns `sortBy(orders, "total", desc)` into a path segment rather than a call.
     */
    val SORT_NAMES: List<String> = listOf("sortBy")

    /** Every function name the DSL accepts, in declaration order. */
    fun allNames(): List<String> {
        return AggregateFunctionName.dslNames() + CollectionFunctionName.dslNames() + pathFunctionNames()
    }

    /**
     * The names written as a call but parsed as a path segment.
     *
     * A surface that edits function *calls* has to exclude these: there is no
     * `FunctionCallValueAst` for them to edit, and offering one would produce text the parser reads
     * as something else entirely.
     */
    fun pathFunctionNames(): List<String> {
        return SLICE_NAMES + SORT_NAMES
    }

    /** The reductions over a collection, i.e. what an aggregate picker should offer. */
    fun aggregateNames(): List<String> {
        return AggregateFunctionName.entries
            .filter { entry -> entry.isAggregate }
            .map { entry -> entry.dslName }
    }

    /** What a call to [name] evaluates to, or null when no function goes by that name. */
    fun resultKindOf(name: String): FunctionResultKind? {
        AggregateFunctionName.fromName(name = name)?.let { function -> return function.resultKind }
        return CollectionFunctionName.fromName(name = name)?.resultKind
    }
}
