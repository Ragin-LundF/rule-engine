package ruleengine.dsl.parser

import ruleengine.dsl.ast.BetweenLiteral
import ruleengine.dsl.ast.BooleanLiteral
import ruleengine.dsl.ast.ExtractionAst
import ruleengine.dsl.ast.ExtractionRefLiteral
import ruleengine.dsl.ast.ListLiteral
import ruleengine.dsl.ast.LiteralAst
import ruleengine.dsl.ast.NumberLiteral
import ruleengine.dsl.ast.StringLiteral
import ruleengine.dsl.ast.VariableRefLiteral
import ruleengine.dsl.diagnostics.ParseException
import ruleengine.dsl.lexer.Token
import ruleengine.dsl.lexer.TokenType

/**
 * Literals in every position that takes one: condition values, `between` bounds and action arguments.
 *
 * Split out of [Parser] because both the `when` and the `then` side need it, and neither owns it.
 */
internal class LiteralParser(private val cursor: TokenCursor) {

    fun parse(): LiteralAst {
        val token = cursor.current()
        return when (token.type) {
            TokenType.STRING -> {
                cursor.advance()
                StringLiteral(value = token.text)
            }

            TokenType.NUMBER -> {
                cursor.advance()
                NumberLiteral(value = token.text)
            }

            TokenType.LBRACKET -> parseList()

            TokenType.IDENT if isBooleanText(text = token.text) -> {
                cursor.advance()
                BooleanLiteral(value = token.text.equals(other = "true", ignoreCase = true))
            }

            else -> throw ParseException(
                line = token.line,
                column = token.col,
                messageText = "Expected literal (string/number/list/true/false)"
            )
        }
    }

    private fun parseList(): ListLiteral {
        cursor.advance()
        val items = mutableListOf<LiteralAst>()
        while (cursor.current().type != TokenType.RBRACKET) {
            items += parse()
            if (cursor.current().type == TokenType.COMMA) {
                cursor.advance()
            }
        }

        cursor.expect(type = TokenType.RBRACKET)
        return ListLiteral(items = items)
    }

    /**
     * Parses a literal, an extraction reference (`$N`) or a variable reference (`$name`).
     *
     * The two `$` forms are told apart by their spelling: an all-digit name is a capture group, and
     * extraction references may only appear as arguments to an action that also carries an
     * [ExtractionAst].
     */
    fun parseOrRef(): LiteralAst {
        val token = cursor.current()
        if (token.type == TokenType.IDENT && token.text.startsWith(prefix = "$")) {
            cursor.advance()
            val name = referenceName(token = token)
            val groupIndex = name.toIntOrNull()
            return if (groupIndex != null) {
                ExtractionRefLiteral(groupIndex = groupIndex)
            } else {
                VariableRefLiteral(name = name)
            }
        }
        return parse()
    }

    /**
     * Parses the two bounds of `between`.
     *
     * Bounds are numbers for numeric fields and quoted ISO dates for date fields; [BetweenLiteral]
     * carries both as text and the field's compiler decides how to read them.
     */
    fun parseBetween(): BetweenLiteral = BetweenLiteral(
        low = parseBoundToken(label = "lower"),
        high = parseBoundToken(label = "upper"),
    )

    private fun parseBoundToken(label: String): String {
        val token = cursor.current()
        if (token.type != TokenType.NUMBER && token.type != TokenType.STRING) {
            throw ParseException(
                line = token.line,
                column = token.col,
                messageText = "Expected $label bound (number or quoted date) for 'between'"
            )
        }
        cursor.advance()
        return token.text
    }

    companion object {
        /**
         * True when [token] can begin an action argument: a string, a number, a list, or a `$`
         * reference such as `$1` or `$total`. Anything else means the action has no argument.
         */
        fun startsLiteral(token: Token): Boolean = when (token.type) {
            TokenType.STRING, TokenType.NUMBER, TokenType.LBRACKET -> true
            TokenType.IDENT -> token.text.startsWith(prefix = "$")
            else -> false
        }

        /** True for the two identifiers the parser treats as boolean literals in value position. */
        fun isBooleanText(text: String): Boolean =
            text.equals(other = "true", ignoreCase = true) || text.equals(other = "false", ignoreCase = true)

        /** The name part of a `$`-prefixed token, rejecting a bare `$`. */
        fun referenceName(token: Token): String {
            val name = token.text.removePrefix(prefix = "$")
            if (name.isEmpty()) {
                throw ParseException(
                    line = token.line,
                    column = token.col,
                    messageText = "Expected a name after '\$', " +
                            "e.g. \$orderTotal for a variable or \$1 for a capture group"
                )
            }
            return name
        }
    }
}
