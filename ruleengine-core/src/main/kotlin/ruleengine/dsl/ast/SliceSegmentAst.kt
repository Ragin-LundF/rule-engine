package ruleengine.dsl.ast

/**
 * A bounded prefix or suffix of the collection selected so far — what `take(orders, 3)` and
 * `takeLast(orders, 3)` are written as.
 *
 * Spelled in the DSL as a function but modelled as a path segment, because that is what it is: the
 * slice narrows the collection the rest of the path continues from. As a segment it composes with
 * projection and filtering for free — `take(orders, 3).total` and
 * `takeLast(events, 10)[failed == true]` need no grammar of their own — and, `PathSegmentAst` being
 * sealed, every stage that walks a path has to acknowledge it.
 *
 * [count] keeps the text the author wrote, exactly as [NumberLiteral] does. Rejecting a negative or
 * fractional count belongs to validation, which reports it as a diagnostic; parsing it to an `Int`
 * here would force the parser to fail first and with less context.
 */
data class SliceSegmentAst(
    val fromEnd: Boolean,
    val count: String
) : PathSegmentAst
