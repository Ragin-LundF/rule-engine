package ruleengine.dsl.ast.path

/**
 * The collection selected so far, read as empty when the record does not carry it — what
 * `orEmpty(fees.rows)` is written as.
 *
 * Spelled as a call but modelled as a segment for the same reason [SliceSegmentAst] is: what follows
 * continues from it, so `orEmpty(fees.rows)[period in periods].amount` needs no grammar of its own.
 * It covers the whole path, so an absent object on the way to the collection reads as empty too.
 */
data object OrEmptySegmentAst : PathSegmentAst
