package ruleengine.dsl.ast

data class ConditionAst(
    val field: String,
    val operator: String,
    val value: LiteralAst,
    /** When true the compiled expression will compare case-insensitively (text operators only). */
    val ignoreCase: Boolean = false,
    /**
     * Where the condition starts in the source, 1-based, or null when the node was not built by the
     * parser. Diagnostics carry it through so an editor can point at the offending line.
     */
    val line: Int? = null,
    val column: Int? = null,
) : ExpressionAst {

    /**
     * Position is metadata, not identity: the same condition written at a different offset is still
     * the same condition. Equality deliberately ignores [line] and [column] so that comparing two
     * parses — `a and b` against the implicit-and spelling, or a builder round-trip against its
     * source — tests structure rather than indentation.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ConditionAst) return false

        return field == other.field &&
                operator == other.operator &&
                value == other.value &&
                ignoreCase == other.ignoreCase
    }

    override fun hashCode(): Int {
        var result = field.hashCode()
        result = 31 * result + operator.hashCode()
        result = 31 * result + value.hashCode()
        result = 31 * result + ignoreCase.hashCode()
        return result
    }
}
