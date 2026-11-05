package ruleengine.dsl.ast.rule

import ruleengine.dsl.ast.literal.ExtractionRefLiteral
import ruleengine.dsl.ast.literal.LiteralAst

data class ActionAst(
    val name: String,
    val arguments: List<LiteralAst>,
    /**
     * Optional extraction that is applied to a context field before the action
     * arguments are resolved.  Arguments that are [ExtractionRefLiteral] will be
     * replaced with the extracted string value at evaluation time.
     */
    val extraction: ExtractionAst? = null
)
