package ruleengine.dsl.ast.path

import ruleengine.dsl.ast.value.FieldAccessAst

/**
 * One `alias: collection.valueMember` argument of a [JoinSegmentAst].
 *
 * [path] keeps the whole source as written; the compiler cuts it into the collection and the value
 * member, exactly as it does for `sumByKey`.
 */
data class JoinSourceAst(
    val alias: String,
    val path: FieldAccessAst
)
