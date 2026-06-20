package ruleengine.dsl.parser

import ruleengine.dsl.ast.ActionAst
import ruleengine.dsl.ast.AndAst
import ruleengine.dsl.ast.ArithmeticOperatorAst
import ruleengine.dsl.ast.ArithmeticValueAst
import ruleengine.dsl.ast.BetweenLiteral
import ruleengine.dsl.ast.ComparisonExpressionAst
import ruleengine.dsl.ast.ComparisonOperatorAst
import ruleengine.dsl.ast.ConditionAst
import ruleengine.dsl.ast.ExpressionAst
import ruleengine.dsl.ast.ExtractionAst
import ruleengine.dsl.ast.ExtractionRefLiteral
import ruleengine.dsl.ast.FieldAccessAst
import ruleengine.dsl.ast.FieldSegmentAst
import ruleengine.dsl.ast.FilterSegmentAst
import ruleengine.dsl.ast.FunctionCallValueAst
import ruleengine.dsl.ast.ListLiteral
import ruleengine.dsl.ast.LiteralAst
import ruleengine.dsl.ast.LiteralValueAst
import ruleengine.dsl.ast.NotAst
import ruleengine.dsl.ast.NumberLiteral
import ruleengine.dsl.ast.OrAst
import ruleengine.dsl.ast.PathSegmentAst
import ruleengine.dsl.ast.RuleAst
import ruleengine.dsl.ast.StringLiteral
import ruleengine.dsl.ast.ValueExpressionAst
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

            if (c.text == "extract") {
                advance()
                actions += parseExtractAction()
            } else {
                val name = c.text
                advance()

                // parse single argument as string or number or list
                val arg = parseLiteral()
                actions += ActionAst(name = name, arguments = listOf(arg))
            }
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

    private fun parsePrimary(): ExpressionAst {
        return if (current().type == TokenType.LPAREN && !isParenthesizedValueExpression()) {
            parseParenthesizedExpression()
        } else {
            parseComparisonOrLegacyCondition()
        }
    }

    /**
     * Peeks ahead to determine whether a `(` starts a parenthesized value expression
     * (i.e. arithmetic like `(amount + fee) * 2 <= 100`) rather than a parenthesized
     * boolean expression. Returns true when the content inside the parens looks like
     * a value expression followed by an arithmetic or comparison operator.
     */
    private fun isParenthesizedValueExpression(): Boolean {
        val savedPos = pos
        return try {
            advance() // consume '('
            parseValueExpression()
            val afterInner = current().type
            pos = savedPos
            afterInner in setOf(
                TokenType.RPAREN,
                TokenType.PLUS, TokenType.MINUS, TokenType.STAR, TokenType.SLASH
            )
        } catch (_: ParseException) {
            pos = savedPos
            false
        }
    }

    /**
     * Decides between a modern symbolic comparison (producing [ComparisonExpressionAst])
     * and a legacy named-operator condition (producing [ConditionAst]).
     *
     * Produces [ComparisonExpressionAst] only when the expression is non-trivial:
     * the left side is a function call or arithmetic, or the right side is a function
     * call or arithmetic. Plain `field op literal` patterns continue to produce
     * [ConditionAst] for backward compatibility until the compiler supports the full
     * value expression model.
     */
    private fun parseComparisonOrLegacyCondition(): ExpressionAst {
        val savedPos = pos
        return try {
            val left = parseValueExpression()
            val op = parseComparisonOperator()
            if (op != null) {
                val right = parseValueExpression()
                if (isModernExpression(left) || isModernExpression(right) || isModernOnlyOperator(op)) {
                    ComparisonExpressionAst(left = left, operator = op, right = right)
                } else {
                    // Both sides are plain field/literal with a legacy-compatible operator
                    // — keep as legacy ConditionAst
                    pos = savedPos
                    parseCondition()
                }
            } else {
                // No symbolic operator found — restore and fall back to legacy
                pos = savedPos
                parseCondition()
            }
        } catch (_: ParseException) {
            pos = savedPos
            parseCondition()
        }
    }

    /**
     * Returns true when a value expression requires the modern evaluation path:
     * function calls, arithmetic, or field paths with filter segments.
     */
    private fun isModernExpression(expr: ValueExpressionAst): Boolean {
        return when (expr) {
            is FunctionCallValueAst -> true
            is ArithmeticValueAst -> true
            is FieldAccessAst -> expr.path.any { it is FilterSegmentAst }
            is LiteralValueAst -> false
        }
    }

    /**
     * Returns true for operators that have no equivalent in the legacy named-operator DSL
     * and must always be routed through the modern [ComparisonExpressionAst] path.
     */
    private fun isModernOnlyOperator(op: ComparisonOperatorAst): Boolean {
        return op == ComparisonOperatorAst.EQ || op == ComparisonOperatorAst.NEQ
    }

    private fun parseComparisonOperator(): ComparisonOperatorAst? {
        return when (current().type) {
            TokenType.EQEQ -> { advance(); ComparisonOperatorAst.EQ }
            TokenType.BANGEQ -> { advance(); ComparisonOperatorAst.NEQ }
            TokenType.GT -> { advance(); ComparisonOperatorAst.GT }
            TokenType.GTE -> { advance(); ComparisonOperatorAst.GTE }
            TokenType.LT -> { advance(); ComparisonOperatorAst.LT }
            TokenType.LTE -> { advance(); ComparisonOperatorAst.LTE }
            else -> null
        }
    }

    private fun parseValueExpression(): ValueExpressionAst {
        return parseAdditiveValue()
    }

    private fun parseAdditiveValue(): ValueExpressionAst {
        var left = parseMultiplicativeValue()
        while (current().type == TokenType.PLUS || current().type == TokenType.MINUS) {
            val op = if (current().type == TokenType.PLUS) ArithmeticOperatorAst.ADD else ArithmeticOperatorAst.SUBTRACT
            advance()
            val right = parseMultiplicativeValue()
            left = ArithmeticValueAst(left = left, operator = op, right = right)
        }
        return left
    }

    private fun parseMultiplicativeValue(): ValueExpressionAst {
        var left = parsePrimaryValue()
        while (current().type == TokenType.STAR || current().type == TokenType.SLASH) {
            val op = if (current().type == TokenType.STAR) {
                ArithmeticOperatorAst.MULTIPLY
            } else {
                ArithmeticOperatorAst.DIVIDE
            }
            advance()
            val right = parsePrimaryValue()
            left = ArithmeticValueAst(left = left, operator = op, right = right)
        }
        return left
    }

    private fun parsePrimaryValue(): ValueExpressionAst {
        val token = current()
        return when (token.type) {
            TokenType.LPAREN -> {
                advance()
                val inner = parseValueExpression()
                expect(type = TokenType.RPAREN)
                inner
            }
            TokenType.NUMBER -> {
                advance()
                LiteralValueAst(literal = NumberLiteral(value = token.text))
            }
            TokenType.STRING -> {
                advance()
                LiteralValueAst(literal = StringLiteral(value = token.text))
            }
            TokenType.IDENT -> parseFunctionCallOrFieldAccess()
            else -> throw ParseException(
                line = token.line,
                column = token.col,
                messageText = "Expected value expression but found ${token.type} (${token.text})"
            )
        }
    }

    private fun parseFunctionCallOrFieldAccess(): ValueExpressionAst {
        val nameTok = expect(type = TokenType.IDENT)
        return if (current().type == TokenType.LPAREN) {
            advance()
            val args = mutableListOf<ValueExpressionAst>()
            while (current().type != TokenType.RPAREN && current().type != TokenType.EOF) {
                args += parseValueExpression()
                if (current().type == TokenType.COMMA) advance()
            }
            expect(type = TokenType.RPAREN)
            FunctionCallValueAst(name = nameTok.text, arguments = args)
        } else {
            parseFieldPath(firstIdentifier = nameTok.text)
        }
    }

    private fun parseFieldPath(firstIdentifier: String): FieldAccessAst {
        val segments = mutableListOf<PathSegmentAst>(FieldSegmentAst(name = firstIdentifier))
        while (current().type == TokenType.LBRACKET || current().type == TokenType.DOT) {
            when (current().type) {
                TokenType.LBRACKET -> {
                    advance()
                    val filterExpr = parseFilterExpression()
                    expect(type = TokenType.RBRACKET)
                    segments += FilterSegmentAst(expression = filterExpr)
                }
                TokenType.DOT -> {
                    advance()
                    val fieldTok = expect(type = TokenType.IDENT)
                    segments += FieldSegmentAst(name = fieldTok.text)
                }
                else -> break
            }
        }
        return FieldAccessAst(path = segments)
    }

    private fun parseFilterExpression(): ExpressionAst {
        return parseExpression()
    }

    private fun parseParenthesizedExpression(): ExpressionAst {
        advance()
        val innerExpression = parseExpression()
        expect(type = TokenType.RPAREN)
        return innerExpression
    }

    private fun parseCondition(): ConditionAst {
        val field = parseConditionField()
        val operator = parseConditionOperator()
        val value = parseConditionValue(operator = operator)
        val ignoreCase = parseIgnoreCaseModifier()

        return ConditionAst(
            field = field,
            operator = operator,
            value = value,
            ignoreCase = ignoreCase
        )
    }

    @Suppress("ThrowsCount")
    private fun parseConditionField(): String {
        val token = current()
        if (token.type != TokenType.IDENT) {
            throw ParseException(
                line = token.line,
                column = token.col,
                messageText = "Expected field identifier in condition"
            )
        }

        val parts = mutableListOf<String>()
        parts.add(token.text)
        advance()

        while (current().type == TokenType.DOT) {
            advance()
            val next = current()
            if (next.type != TokenType.IDENT) {
                throw ParseException(
                    line = next.line,
                    column = next.col,
                    messageText = "Expected identifier after dot"
                )
            }

            parts.add(next.text)
            advance()
        }

        return parts.joinToString(separator = ".")
    }

    private fun parseConditionOperator(): String {
        val opTok = current()
        val isSymbolicOperator = opTok.type in setOf(
            TokenType.EQEQ, TokenType.BANGEQ,
            TokenType.GT, TokenType.GTE,
            TokenType.LT, TokenType.LTE
        )
        if (opTok.type != TokenType.IDENT && !isSymbolicOperator) {
            throw ParseException(
                line = opTok.line,
                column = opTok.col,
                messageText = "Expected operator"
            )
        }

        advance()
        return opTok.text
    }

    private fun parseConditionValue(operator: String): LiteralAst {
        return if (operator.lowercase() == "between") {
            parseBetweenLiteral()
        } else {
            parseLiteral()
        }
    }

    @Suppress("ThrowsCount")
    private fun parseBetweenLiteral(): BetweenLiteral {
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

        return BetweenLiteral(low = lowTok.text, high = highTok.text)
    }

    private fun parseIgnoreCaseModifier(): Boolean {
        if (current().type == TokenType.IDENT && current().text == "ignoreCase") {
            advance()
            return true
        }

        return false
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

    /**
     * Parses a literal or an extraction reference (`$N`).
     * Extraction references may only appear as arguments to an action that
     * also carries an [ExtractionAst].
     */
    private fun parseLiteralOrRef(): LiteralAst {
        val token = current()
        if (token.type == TokenType.IDENT && token.text.startsWith(prefix = "$")) {
            advance()
            val groupIndex = token.text.removePrefix(prefix = "$").toIntOrNull()
                ?: throw ParseException(
                    line = token.line,
                    column = token.col,
                    messageText = "Invalid extraction reference '${token.text}'; expected \$N where N is an integer"
                )
            return ExtractionRefLiteral(groupIndex = groupIndex)
        }
        return parseLiteral()
    }

    /**
     * Parses the body of an `extract` clause:
     * ```
     * extract <sourceField> regex("<pattern>", <groupIndex>) <actionName> <arg>
     * ```
     */
    @Suppress("ThrowsCount", "LongMethod")
    private fun parseExtractAction(): ActionAst {
        val fieldTok = current()
        if (fieldTok.type != TokenType.IDENT) {
            throw ParseException(
                line = fieldTok.line,
                column = fieldTok.col,
                messageText = "Expected source field name after 'extract' but found '${fieldTok.text}'"
            )
        }
        val sourceField = fieldTok.text
        advance()

        val methodTok = current()
        if (methodTok.type != TokenType.IDENT || methodTok.text != "regex") {
            throw ParseException(
                line = methodTok.line,
                column = methodTok.col,
                messageText = "Expected extraction method 'regex' but found '${methodTok.text}'"
            )
        }
        advance()

        expect(type = TokenType.LPAREN)
        val patternTok = expect(type = TokenType.STRING)
        expect(type = TokenType.COMMA)
        val groupIndexTok = current()
        if (groupIndexTok.type != TokenType.NUMBER) {
            throw ParseException(
                line = groupIndexTok.line,
                column = groupIndexTok.col,
                messageText = "Expected integer group index in regex extraction but found '${groupIndexTok.text}'"
            )
        }
        val groupIndex = groupIndexTok.text.toIntOrNull()
            ?: throw ParseException(
                line = groupIndexTok.line,
                column = groupIndexTok.col,
                messageText = "Invalid group index '${groupIndexTok.text}': must be a non-negative integer"
            )
        advance()
        expect(type = TokenType.RPAREN)

        val extraction = ExtractionAst.RegexExtraction(
            sourceField = sourceField,
            pattern = patternTok.text,
            groupIndex = groupIndex
        )

        val actionNameTok = current()
        if (actionNameTok.type != TokenType.IDENT) {
            throw ParseException(
                line = actionNameTok.line,
                column = actionNameTok.col,
                messageText = "Expected action name after extraction definition but found '${actionNameTok.text}'"
            )
        }
        val actionName = actionNameTok.text
        advance()

        val arg = parseLiteralOrRef()

        return ActionAst(
            name = actionName,
            arguments = listOf(arg),
            extraction = extraction
        )
    }
}
