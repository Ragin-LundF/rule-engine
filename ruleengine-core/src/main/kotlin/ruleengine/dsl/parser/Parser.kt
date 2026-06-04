package ruleengine.dsl.parser

import ruleengine.dsl.ast.ActionAst
import ruleengine.dsl.ast.AndAst
import ruleengine.dsl.ast.BetweenLiteral
import ruleengine.dsl.ast.ConditionAst
import ruleengine.dsl.ast.ExpressionAst
import ruleengine.dsl.ast.ListLiteral
import ruleengine.dsl.ast.LiteralAst
import ruleengine.dsl.ast.NotAst
import ruleengine.dsl.ast.NumberLiteral
import ruleengine.dsl.ast.OrAst
import ruleengine.dsl.ast.RuleAst
import ruleengine.dsl.ast.StringLiteral
import ruleengine.dsl.diagnostics.ParseException
import ruleengine.dsl.lexer.Lexer
import ruleengine.dsl.lexer.Token
import ruleengine.dsl.lexer.TokenType

@Suppress("TooManyFunctions")
class Parser(private val input: String) {
    private val tokens: List<Token> = Lexer(input = input).tokenize()
    private var pos = 0

    private fun current(): Token {
        return tokens.getOrElse(index = pos) { tokens.last() }
    }

    private fun advance() {
        if (pos < tokens.size) {
            pos++
        }
    }

    private fun expect(type: TokenType): Token {
        val t = current()
        if (t.type != type) {
            throw ParseException(
                line = t.line,
                column = t.col,
                messageText = "Expected $type but found ${t.type} (${t.text})"
            )
        }

        advance()
        return t
    }

    fun parseRules(): List<RuleAst> {
        val rules = mutableListOf<RuleAst>()
        while (current().type != TokenType.EOF) {
            rules += parseRule()
        }

        return rules
    }

    @Suppress("ThrowsCount")
    fun parseRule(): RuleAst {
        val first = current()
        if (first.type != TokenType.IDENT || first.text != "rule") {
            throw ParseException(
                line = first.line,
                column = first.col,
                messageText = "Expected 'rule' declaration"
            )
        }

        advance()

        val idTok = expect(type = TokenType.STRING)
        val id = idTok.text

        expect(type = TokenType.LBRACE)

        // optional description ignored for now
        // parse when
        val whenTok = current()
        if (whenTok.type != TokenType.IDENT || whenTok.text != "when") {
            throw ParseException(
                line = whenTok.line,
                column = whenTok.col,
                messageText = "Expected 'when' block"
            )
        }

        advance()
        val condition = parseExpression()

        val thenTok = current()
        if (thenTok.type != TokenType.IDENT || thenTok.text != "then") {
            throw ParseException(
                line = thenTok.line,
                column = thenTok.col,
                messageText = "Expected 'then' block"
            )
        }

        advance()
        val actions = mutableListOf<ActionAst>()
        while (true) {
            val c = current()
            if (c.type == TokenType.RBRACE || c.type == TokenType.EOF) {
                break
            }

            if (c.type != TokenType.IDENT) {
                throw ParseException(
                    line = c.line,
                    column = c.col,
                    messageText = "Expected action identifier but found ${c.text}"
                )
            }

            val name = c.text
            advance()

            // parse single argument as string or number or list
            val arg = parseLiteral()
            actions += ActionAst(name = name, arguments = listOf(arg))
        }

        expect(type = TokenType.RBRACE)
        return RuleAst(id = id, condition = condition, actions = actions)
    }

    private fun parseExpression(): ExpressionAst {
        val expr = parseOr()
        return expr
    }

    private fun parseOr(): ExpressionAst {
        val left = parseAnd()
        val parts = mutableListOf(left)
        while (current().type == TokenType.IDENT && current().text == "or") {
            advance()
            parts += parseAnd()
        }

        return if (parts.size == 1) left else OrAst(children = parts)
    }

    private fun parseAnd(): ExpressionAst {
        val left = parseUnary()
        val parts = mutableListOf(left)
        while (current().type == TokenType.IDENT && current().text == "and") {
            advance()
            parts += parseUnary()
        }

        return if (parts.size == 1) left else AndAst(children = parts)
    }

    private fun parseUnary(): ExpressionAst {
        val token = current()
        if (token.type == TokenType.IDENT && token.text == "not") {
            advance()
            val inner = parseUnary()
            return NotAst(child = inner)
        }

        return parsePrimary()
    }

    @Suppress("ThrowsCount", "LongMethod")
    private fun parsePrimary(): ExpressionAst {
        val c = current()
        if (c.type == TokenType.LPAREN) {
            advance()
            val innerExpression = parseExpression()
            expect(type = TokenType.RPAREN)
            return innerExpression
        }

        // condition: IDENT OP LITERAL [ignoreCase]
        if (c.type != TokenType.IDENT) {
            throw ParseException(
                line = c.line,
                column = c.col,
                messageText = "Expected field identifier in condition"
            )
        }

        val field = c.text
        advance()

        val opTok = current()
        if (opTok.type != TokenType.IDENT) {
            throw ParseException(
                line = opTok.line,
                column = opTok.col,
                messageText = "Expected operator"
            )
        }

        val op = opTok.text
        advance()

        // `between` consumes two number literals rather than one
        val value: LiteralAst
        if (op.lowercase() == "between") {
            val lowTok = current()
            if (lowTok.type != TokenType.NUMBER) {
                throw ParseException(
                    line = lowTok.line,
                    column = lowTok.col,
                    messageText = "Expected lower bound number literal for 'between'"
                )
            }

            advance()
            val highTok = current()
            if (highTok.type != TokenType.NUMBER) {
                throw ParseException(
                    line = highTok.line,
                    column = highTok.col,
                    messageText = "Expected upper bound number literal for 'between'"
                )
            }

            advance()
            value = BetweenLiteral(low = lowTok.text, high = highTok.text)
        } else {
            value = parseLiteral()
        }

        // Optional trailing `ignoreCase` modifier (only meaningful for text operators)
        val ignoreCase: Boolean
        if (current().type == TokenType.IDENT && current().text == "ignoreCase") {
            advance()
            ignoreCase = true
        } else {
            ignoreCase = false
        }

        return ConditionAst(field = field, operator = op, value = value, ignoreCase = ignoreCase)
    }

    private fun parseLiteral(): LiteralAst {
        val token = current()
        val result: LiteralAst = when (token.type) {
            TokenType.STRING -> {
                advance()
                StringLiteral(value = token.text)
            }

            TokenType.NUMBER -> {
                advance()
                NumberLiteral(value = token.text)
            }

            TokenType.LBRACKET -> {
                advance()
                val items = mutableListOf<LiteralAst>()
                while (current().type != TokenType.RBRACKET) {
                    items += parseLiteral()
                    if (current().type == TokenType.COMMA) {
                        advance()
                    }
                }

                expect(type = TokenType.RBRACKET)
                ListLiteral(items = items)
            }

            else -> {
                throw ParseException(
                    line = token.line,
                    column = token.col,
                    messageText = "Expected literal (string/number/list)"
                )
            }
        }

        return result
    }
}

