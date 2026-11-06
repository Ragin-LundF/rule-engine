package ruleengine.dsl.parser

import ruleengine.dsl.ast.path.OrEmptySegmentAst
import ruleengine.dsl.ast.path.PathSegmentAst
import ruleengine.dsl.ast.value.FieldAccessAst
import ruleengine.dsl.ast.value.ValueExpressionAst
import ruleengine.dsl.diagnostics.ParseException
import ruleengine.dsl.lexer.Token
import ruleengine.dsl.lexer.TokenType
import ruleengine.evaluator.compiled.CollectionFunctionName

/**
 * The arguments of a function call, with the `optional` source marker, and the inside of
 * `orEmpty(path)`.
 *
 * Split out of [Parser] like [JoinByKeyParser], which reads the marker through [atOptionalMarker].
 */
internal class CallArgumentParser(
    private val cursor: TokenCursor,
    private val parseValueExpression: () -> ValueExpressionAst,
) {

    private companion object {
        /** The source marker. Not reserved: a schema field named `optional` stays usable. */
        const val OPTIONAL = "optional"
        val SUM_BY_KEY: String = CollectionFunctionName.SUM_BY_KEY.dslName
    }

    /** Reads from the `(` after [nameTok] up to and including the closing parenthesis. */
    fun parseArguments(nameTok: Token): List<ValueExpressionAst> {
        cursor.advance()
        val args = mutableListOf<ValueExpressionAst>()
        while (cursor.current().type != TokenType.RPAREN && cursor.current().type != TokenType.EOF) {
            args += if (atOptionalMarker()) parseOptionalArgument(functionTok = nameTok) else parseValueExpression()
            if (cursor.current().type == TokenType.COMMA) cursor.advance()
        }
        cursor.expect(type = TokenType.RPAREN)
        return args
    }

    /**
     * Reads `orEmpty(path)` from the `(` after [nameTok] and returns the path with the segment that
     * reads a missing collection as empty, for the caller to continue — `orEmpty(fees.rows)[...].amount`.
     */
    fun parseOrEmpty(nameTok: Token): MutableList<PathSegmentAst> {
        val start = cursor.current()
        cursor.advance()
        val target = parseValueExpression() as? FieldAccessAst ?: throw ParseException(
            line = start.line,
            column = start.col,
            messageText = "${nameTok.text}() expects a collection path, such as ${nameTok.text}(fees.rows)"
        )
        cursor.expect(type = TokenType.RPAREN)
        return (target.path + OrEmptySegmentAst).toMutableList()
    }

    /**
     * True when the cursor stands on the `optional` marker: the word followed by a name on the same
     * line. A field named `optional` is followed by an operator, a dot, a bracket or nothing, so it
     * never reads as the marker.
     */
    fun atOptionalMarker(): Boolean {
        val token = cursor.current()
        val next = cursor.peek()
        return token.type == TokenType.IDENT && token.text.equals(other = OPTIONAL, ignoreCase = true) &&
            next.type == TokenType.IDENT && next.line == token.line
    }

    /**
     * Reads `optional <path>` as an argument of the call [functionTok] names. Only a `sumByKey`
     * source may carry the marker here; anywhere else it is rejected, at load time.
     */
    private fun parseOptionalArgument(functionTok: Token): FieldAccessAst {
        val marker = cursor.current()
        if (!functionTok.text.equals(other = SUM_BY_KEY, ignoreCase = true)) {
            throw ParseException(
                line = marker.line,
                column = marker.col,
                messageText = "'${marker.text}' marks a source of $SUM_BY_KEY or joinByKey only, " +
                    "not an argument of ${functionTok.text}()"
            )
        }
        cursor.advance()
        val path = parseValueExpression() as? FieldAccessAst ?: throw ParseException(
            line = marker.line,
            column = marker.col,
            messageText = "'${marker.text}' must be followed by a collection path, such as " +
                "optional refunds.amount"
        )
        return path.copy(optional = true)
    }
}
