package ruleengine.dsl.parser

import ruleengine.dsl.ast.path.JoinSegmentAst
import ruleengine.dsl.ast.path.JoinSourceAst
import ruleengine.dsl.ast.value.FieldAccessAst
import ruleengine.dsl.ast.value.ValueExpressionAst
import ruleengine.dsl.diagnostics.ParseException
import ruleengine.dsl.lexer.Token
import ruleengine.dsl.lexer.TokenType

/**
 * The inside of `joinByKey("key", alias: collection.member, ...)`, up to and including the closing
 * parenthesis.
 *
 * Split out of [Parser] like [ThenBlockParser]: [Parser] owns the path the join starts, and each
 * source is an ordinary value expression, parsed by calling back into [parseValueExpression].
 *
 * Only the shape is checked here. The source count, a missing key and the aliases are left to
 * validation, which reports them with the schema at hand.
 */
internal class JoinByKeyParser(
    private val cursor: TokenCursor,
    private val parseValueExpression: () -> ValueExpressionAst,
) {

    /** Reads from the `(` after [nameTok]. */
    fun parse(nameTok: Token): JoinSegmentAst {
        cursor.advance()
        val key = readKey()
        val sources = mutableListOf<JoinSourceAst>()
        while (cursor.current().type != TokenType.RPAREN && cursor.current().type != TokenType.EOF) {
            sources += readSource(nameTok = nameTok)
            if (cursor.current().type != TokenType.RPAREN) {
                cursor.expect(type = TokenType.COMMA)
            }
        }
        cursor.expect(type = TokenType.RPAREN)
        return JoinSegmentAst(key = key, sources = sources)
    }

    /** The quoted key, when one is written first, consuming the comma that follows it. */
    private fun readKey(): String? {
        val keyTok = cursor.current()
        if (keyTok.type != TokenType.STRING) {
            return null
        }
        cursor.advance()
        if (cursor.current().type != TokenType.RPAREN) {
            cursor.expect(type = TokenType.COMMA)
        }
        return keyTok.text
    }

    /** One `alias: collection.member` source. */
    @Suppress("ThrowsCount")
    private fun readSource(nameTok: Token): JoinSourceAst {
        val aliasTok = cursor.current()
        val hint = "${nameTok.text}() expects each source as alias: path, such as credit: credits.amount"
        if (aliasTok.type != TokenType.IDENT) {
            throw ParseException(
                line = aliasTok.line,
                column = aliasTok.col,
                messageText = "$hint, but found '${aliasTok.text}'"
            )
        }
        cursor.advance()
        if (cursor.current().type != TokenType.COLON) {
            throw ParseException(
                line = aliasTok.line,
                column = aliasTok.col,
                messageText = "$hint; '${aliasTok.text}' is missing its ':'"
            )
        }
        cursor.advance()
        val pathStart = cursor.current()
        val path = parseValueExpression() as? FieldAccessAst ?: throw ParseException(
            line = pathStart.line,
            column = pathStart.col,
            messageText = "$hint; the source of '${aliasTok.text}' must be a collection path"
        )
        return JoinSourceAst(alias = aliasTok.text, path = path)
    }
}
