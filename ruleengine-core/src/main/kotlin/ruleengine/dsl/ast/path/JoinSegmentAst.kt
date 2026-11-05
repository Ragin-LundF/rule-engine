package ruleengine.dsl.ast.path

/**
 * Rows built by joining collections on a key — what
 * `joinByKey("period", credit: credits.amount, debit: debits.amount)` is written as.
 *
 * Spelled as a call but modelled as the **first** segment of a path, for the same reason
 * [SliceSegmentAst] is a segment: what follows continues from its rows, so
 * `joinByKey(...)[credit > debit]`, `count(...)`, `take(...)` and `sortBy(...)` need no grammar of
 * their own. Each row holds the key under [key] and one summed value per source under its alias.
 *
 * [key] is null when the author left the key literal out, so validation can name that mistake
 * instead of the parser failing with less context — the same choice [SliceSegmentAst.count] makes.
 */
data class JoinSegmentAst(
    val key: String?,
    val sources: List<JoinSourceAst>
) : PathSegmentAst
