package ruleengine.dsl.parser

import ruleengine.dsl.lexer.Lexer
import ruleengine.dsl.lexer.Token
import ruleengine.dsl.lexer.TokenType
import ruleengine.dsl.ast.*
import ruleengine.dsl.diagnostics.ParseException

class Parser(private val input: String) {
    private val tokens: List<Token> = Lexer(input).tokenize()
    private var pos = 0

    private fun current(): Token = tokens.getOrElse(pos) { tokens.last() }
    private fun advance() { if (pos < tokens.size) pos++ }
    private fun expect(type: TokenType): Token {
        val t = current()
        if (t.type != type) throw ParseException(line = t.line, column = t.col, messageText = "Expected $type but found ${t.type} (${t.text})")
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

    fun parseRule(): RuleAst {
        val t = current()
        if (t.type != TokenType.IDENT || t.text != "rule") throw ParseException(line = t.line, column = t.col, messageText = "Expected 'rule' declaration")
        advance()

        val idTok = expect(TokenType.STRING)
        val id = idTok.text

        expect(TokenType.LBRACE)

        // optional description ignored for now
        // parse when
        val whenTok = current()
        if (whenTok.type != TokenType.IDENT || whenTok.text != "when") throw ParseException(line = whenTok.line, column = whenTok.col, messageText = "Expected 'when' block")
        advance()
        val condition = parseExpression()

        val thenTok = current()
        if (thenTok.type != TokenType.IDENT || thenTok.text != "then") throw ParseException(line = thenTok.line, column = thenTok.col, messageText = "Expected 'then' block")
        advance()
        val actions = mutableListOf<ActionAst>()
        while (true) {
            val c = current()
            if (c.type == TokenType.RBRACE || c.type == TokenType.EOF) { break }
            if (c.type != TokenType.IDENT) throw ParseException(line = c.line, column = c.col, messageText = "Expected action identifier but found ${c.text}")
            val name = c.text; advance()
            // parse single argument as string or number or list
            val arg = parseLiteral()
            actions += ActionAst(name = name, arguments = listOf(arg))
        }

        expect(TokenType.RBRACE)
        return RuleAst(id = id, condition = condition, actions = actions)
    }

    private fun parseExpression(): ExpressionAst {
        return parseOr()
    }

    private fun parseOr(): ExpressionAst {
        var left = parseAnd()
        val parts = mutableListOf<ExpressionAst>(left)
        while (current().type == TokenType.IDENT && current().text == "or") {
            advance()
            parts += parseAnd()
        }
        return if (parts.size == 1) left else OrAst(parts)
    }

    private fun parseAnd(): ExpressionAst {
        var left = parseUnary()
        val parts = mutableListOf<ExpressionAst>(left)
        while (current().type == TokenType.IDENT && current().text == "and") {
            advance()
            parts += parseUnary()
        }
        return if (parts.size == 1) left else AndAst(parts)
    }

    private fun parseUnary(): ExpressionAst {
        val c = current()
        if (c.type == TokenType.IDENT && c.text == "not") {
            advance()
            return NotAst(parseUnary())
        }
        return parsePrimary()
    }

    private fun parsePrimary(): ExpressionAst {
        val c = current()
        if (c.type == TokenType.LPAREN) {
            advance()
            val e = parseExpression()
            expect(TokenType.RPAREN)
            return e
        }
        // condition: IDENT OP LITERAL
        if (c.type != TokenType.IDENT) throw ParseException(line = c.line, column = c.col, messageText = "Expected field identifier in condition")
        val field = c.text; advance()
        val opTok = current()
        if (opTok.type != TokenType.IDENT) throw ParseException(line = opTok.line, column = opTok.col, messageText = "Expected operator")
        val op = opTok.text; advance()
        val value = parseLiteral()
        return ConditionAst(field = field, operator = op, value = value)
    }

    private fun parseLiteral(): LiteralAst {
        val c = current()
        return when (c.type) {
            TokenType.STRING -> { advance(); StringLiteral(c.text) }
            TokenType.NUMBER -> { advance(); NumberLiteral(c.text) }
            TokenType.LBRACKET -> {
                advance()
                val items = mutableListOf<LiteralAst>()
                while (current().type != TokenType.RBRACKET) {
                    items += parseLiteral()
                    if (current().type == TokenType.COMMA) advance()
                }
                expect(TokenType.RBRACKET)
                ListLiteral(items)
            }
            else -> throw ParseException(line = c.line, column = c.col, messageText = "Expected literal (string/number/list)")
        }
    }
}


