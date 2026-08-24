package ruleengine.dsl.ast

/**
 * The collection selected so far, put in order — what `sortBy(orders, "total", desc)` and
 * `sortBy(tags, asc)` are written as.
 *
 * Spelled in the DSL as a function but modelled as a path segment for the same reason
 * [SliceSegmentAst] is: the ordering rearranges the collection the rest of the path continues from.
 * As a segment it composes with projection, filtering and slicing for free —
 * `take(sortBy(orders, "total", desc), 3).total` needs no grammar of its own — and, `PathSegmentAst`
 * being sealed, every stage that walks a path has to acknowledge it.
 *
 * [member] is the element member to order by, or null when the elements are values that order by
 * themselves (a `string_set`, or a collection of scalars). It is kept as the author wrote it;
 * resolving an alias to its canonical name belongs to the compiler, which is the stage that still
 * knows the collection's declared members.
 */
data class SortSegmentAst(
    val member: String?,
    val descending: Boolean
) : PathSegmentAst
