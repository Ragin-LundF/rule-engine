package ruleengine.dsl.parser

import ruleengine.compiler.operators.OperatorUtils
import ruleengine.core.domain.OperatorNames
import ruleengine.dsl.ast.AndAst
import ruleengine.dsl.ast.ArithmeticOperatorAst
import ruleengine.dsl.ast.ArithmeticValueAst
import ruleengine.dsl.ast.BooleanLiteral
import ruleengine.dsl.ast.ComparisonExpressionAst
import ruleengine.dsl.ast.ComparisonOperatorAst
import ruleengine.dsl.ast.ConditionAst
import ruleengine.dsl.ast.ExpressionAst
import ruleengine.dsl.ast.FieldAccessAst
import ruleengine.dsl.ast.FieldSegmentAst
import ruleengine.dsl.ast.FilterSegmentAst
import ruleengine.dsl.ast.FunctionCallValueAst
import ruleengine.dsl.ast.LiteralAst
import ruleengine.dsl.ast.LiteralValueAst
import ruleengine.dsl.ast.NotAst
import ruleengine.dsl.ast.NumberLiteral
import ruleengine.dsl.ast.OrAst
import ruleengine.dsl.ast.PathSegmentAst
import ruleengine.dsl.ast.RuleAst
import ruleengine.dsl.ast.SliceSegmentAst
import ruleengine.dsl.ast.StringLiteral
import ruleengine.dsl.ast.ValueExpressionAst
import ruleengine.dsl.ast.VariableRefAst
import ruleengine.dsl.diagnostics.ParseException
import ruleengine.dsl.lexer.Lexer
import ruleengine.dsl.lexer.Token
import ruleengine.dsl.lexer.TokenType
import ruleengine.evaluator.compiled.DslFunctions
import ruleengine.evaluator.compiled.FunctionResultKind

/**
 * The rule DSL parser: rule blocks, the `when` grammar and value expressions.
 *
 * The `then` grammar lives in [ThenBlockParser] and literals in [LiteralParser]; all three read
 * through the same [TokenCursor], so a production can be moved between them without changing how
 * the source is consumed.
 */
class Parser(private val input: String) {
    private val cursor = TokenCursor(tokens = Lexer(input = input).tokenize())
    private val literals = LiteralParser(cursor = cursor)
    private val thenBlockParser = ThenBlockParser(
        cursor = cursor,
        literals = literals,
        parseValueExpression = { parseValueExpression() },
    )

    private var pos: Int
        get() = cursor.pos
        set(value) {
            cursor.pos = value
        }

    private companion object {
        /** The `not_exists` block keyword, matched by text like every other structural word. */
        const val NOT_EXISTS = "not_exists"

        /**
         * Identifiers that must never be read as the start of an implicitly `and`-joined condition:
         * `then` closes the `when` block, and the rest are infix keywords with their own handling.
         *
         * `else` and `not_exists` are listed even though they can only legally follow `then`: without
         * them, a misplaced one inside the condition would be read as a field of that name and reported
         * as an unknown field, instead of as the block ordering mistake it is.
         */
        val INFIX_AND_BLOCK_KEYWORDS = setOf("then", "else", NOT_EXISTS, "and", "or", "ignoreCase")

        /**
         * The slice functions, recognised here rather than through the function registry: they never
         * reach a compiled function call, because the parser turns them into a path segment.
         */
        const val TAKE = "take"
        const val TAKE_LAST = "takeLast"
    }

    private fun current(): Token = cursor.current()

    private fun advance() = cursor.advance()

    private fun expect(type: TokenType): Token = cursor.expect(type = type)

    private fun previousLine(): Int = cursor.previousLine()

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

        val description = parseOptionalDescription()

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
        val thenBlock = thenBlockParser.parse()
        val elseBlock = parseOptionalBranchBlock(keyword = "else")
        val notExistsBlock = parseOptionalBranchBlock(keyword = NOT_EXISTS)
        requireBranchOrder()

        expect(type = TokenType.RBRACE)
        return RuleAst(
            id = id,
            description = description,
            condition = condition,
            actions = thenBlock.actions,
            assignments = thenBlock.assignments,
            elseActions = elseBlock?.actions.orEmpty(),
            elseAssignments = elseBlock?.assignments.orEmpty(),
            stopOnThen = thenBlock.stop,
            stopOnElse = elseBlock?.stop == true,
            notExistsActions = notExistsBlock?.actions.orEmpty(),
            notExistsAssignments = notExistsBlock?.assignments.orEmpty(),
            stopOnNotExists = notExistsBlock?.stop == true,
            line = first.line,
            column = first.col,
        )
    }

    /**
     * Rejects an `else` written after `not_exists`.
     *
     * Both branch blocks are optional and read in a fixed order, so anything left at this point is out
     * of place. Reported as the ordering mistake it is rather than as the "expected }" the brace check
     * would give, because the author wrote a legal keyword in an illegal position.
     */
    private fun requireBranchOrder() {
        val next = current()
        if (next.type != TokenType.IDENT) {
            return
        }
        if (next.text == "else" || next.text == NOT_EXISTS) {
            throw ParseException(
                line = next.line,
                column = next.col,
                messageText = "'${next.text}' is out of place: a rule's blocks are written " +
                        "'then', then 'else', then '$NOT_EXISTS', each at most once"
            )
        }
    }

    /**
     * Consumes the optional block [keyword] opens, if it is the next token.
     *
     * Every branch has the same grammar, so this reuses [ThenBlockParser] rather than restating it, and
     * one function serves `else` and `not_exists` for the same reason. A repeated block is rejected
     * rather than merged: two of the same block on one rule is an authoring mistake, and silently
     * concatenating them would hide it.
     *
     * An empty block is rejected too. It would evaluate as a no-op, indistinguishable from not
     * declaring the block at all, so accepting it would silently keep a half-written rule. A block
     * holding only `stop` is not empty: it means "halt the run when this branch is the one taken".
     */
    @Suppress("ThrowsCount")
    private fun parseOptionalBranchBlock(keyword: String): ThenBlock? {
        val tok = current()
        if (tok.type != TokenType.IDENT || tok.text != keyword) {
            return null
        }

        advance()
        val block = thenBlockParser.parse()
        if (block.actions.isEmpty() && block.assignments.isEmpty() && !block.stop) {
            throw ParseException(
                line = tok.line,
                column = tok.col,
                messageText = "Empty '$keyword' block: declare at least one action, 'set' or 'add' " +
                        "clause, or 'stop', or drop the '$keyword' keyword"
            )
        }

        val next = current()
        if (next.type == TokenType.IDENT && next.text == keyword) {
            throw ParseException(
                line = next.line,
                column = next.col,
                messageText = "Duplicate '$keyword' block"
            )
        }

        return block
    }

    /**
     * Consumes the optional `description "..."` clause that may open a rule block.
     *
     * `description` is not a reserved word — it arrives as a plain [TokenType.IDENT], exactly like
     * `rule`, `when` and `then` — so it is matched by text. A rule body can only begin with either
     * this clause or `when`, which makes the lookahead unambiguous without backtracking.
     *
     * A repeated clause is rejected rather than silently taking the last value: two descriptions on
     * one rule is an authoring mistake, and picking one of them would hide it.
     */
    private fun parseOptionalDescription(): String? {
        val tok = current()
        if (tok.type != TokenType.IDENT || tok.text != "description") {
            return null
        }

        advance()
        val text = expect(type = TokenType.STRING).text

        val next = current()
        if (next.type == TokenType.IDENT && next.text == "description") {
            throw ParseException(
                line = next.line,
                column = next.col,
                messageText = "Duplicate 'description' clause"
            )
        }

        return text
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
        while (true) {
            when {
                current().type == TokenType.IDENT && current().text == "and" -> advance()
                startsImplicitAnd() -> Unit // no keyword to consume
                else -> break
            }
            parts += parseUnary()
        }

        return if (parts.size == 1) left else AndAst(children = parts)
    }

    /**
     * True when the current token begins a new condition on a line after the one just parsed, which
     * the DSL treats as an implicit `and`.
     *
     * There is no newline token — the lexer discards whitespace — so this compares the line recorded
     * on the token against the line of the last consumed token. Only a `(` or a plain identifier can
     * open a condition; `then` must be excluded or the `when` block would never terminate, and the
     * infix keywords are handled by their own branches.
     */
    private fun startsImplicitAnd(): Boolean {
        val token = current()
        if (token.line <= previousLine()) return false
        return when (token.type) {
            TokenType.LPAREN -> true
            TokenType.IDENT -> token.text !in INFIX_AND_BLOCK_KEYWORDS
            else -> false
        }
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
                if (isModernExpression(left) || isModernExpression(right) || requiresModernPath(op, right)) {
                    ComparisonExpressionAst(left = left, operator = op, right = right)
                } else {
                    // Both sides are plain field/literal with a legacy-compatible operator
                    // — keep as legacy ConditionAst
                    pos = savedPos
                    parseCondition()
                }
            } else if (isBooleanCall(expr = left)) {
                // `every(orders[paid == true])` on its own is already a condition. Desugaring it to
                // `== true` keeps it on the ordinary comparison path instead of adding an
                // ExpressionAst member that the validator, the compiler, both renderers and every
                // walker in the UI would each need a new arm for.
                ComparisonExpressionAst(
                    left = left,
                    operator = ComparisonOperatorAst.EQ,
                    right = LiteralValueAst(literal = BooleanLiteral(value = true))
                )
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
     * A call that already answers true or false, so it may stand where a condition is expected.
     *
     * Restricted to functions declared to return a boolean. Accepting any call would turn a
     * misplaced `count(orders)` into `count(orders) == true`, reported as a type mismatch rather
     * than as the missing comparison it is.
     */
    private fun isBooleanCall(expr: ValueExpressionAst): Boolean {
        if (expr !is FunctionCallValueAst) {
            return false
        }
        return DslFunctions.resultKindOf(name = expr.name) == FunctionResultKind.BOOLEAN
    }

    /**
     * Returns true when a value expression requires the modern evaluation path:
     * function calls, arithmetic, or field paths with filter segments.
     */
    private fun isModernExpression(expr: ValueExpressionAst): Boolean {
        return when (expr) {
            is FunctionCallValueAst -> true
            is ArithmeticValueAst -> true
            // A legacy ConditionAst names its left side by a plain field string and cannot hold a
            // variable, so any comparison touching one must take the modern path.
            is VariableRefAst -> true
            // A filter or a slice makes the path multi-valued, which only the value path can read.
            is FieldAccessAst -> expr.path.any { it is FilterSegmentAst || it is SliceSegmentAst }
            is LiteralValueAst -> false
        }
    }

    /**
     * Returns true for operators that have no equivalent in the legacy named-operator DSL
     * and must always be routed through the modern [ComparisonExpressionAst] path.
     *
     * [ComparisonOperatorAst.CONTAINS] is deliberately **not** listed. It does have a legacy
     * equivalent — `purpose contains "rent"` — and that spelling must keep taking the legacy path,
     * which is the only one that enforces the field's declared `operators:` list and normalizes the
     * literal. A `contains` reaches the modern path only when one of its operands is modern on its
     * own account, i.e. a variable, an aggregate, arithmetic or a filtered path.
     */
    private fun requiresModernPath(op: ComparisonOperatorAst, right: ValueExpressionAst): Boolean {
        if (op == ComparisonOperatorAst.EQ || op == ComparisonOperatorAst.NEQ) {
            return true
        }
        // A legacy condition's right-hand side is a literal, so comparing one field against another
        // has no legacy form at all — before this it did not parse, and the error pointed at the
        // second field name as a missing literal.
        if (right is FieldAccessAst) {
            return true
        }
        // `in` splits by what it is tested against. A named source — a string set, a collection
        // projection or a list variable — has no legacy equivalent and must take the modern path.
        // A literal list is the legacy spelling and keeps its path, which is the only one that
        // enforces the field's declared `operators:` list and normalizes each item.
        return op == ComparisonOperatorAst.IN && right is VariableRefAst
    }

    private fun parseComparisonOperator(): ComparisonOperatorAst? {
        val token = current()
        return when (token.type) {
            TokenType.EQEQ -> { advance(); ComparisonOperatorAst.EQ }
            TokenType.BANGEQ -> { advance(); ComparisonOperatorAst.NEQ }
            TokenType.GT -> { advance(); ComparisonOperatorAst.GT }
            TokenType.GTE -> { advance(); ComparisonOperatorAst.GTE }
            TokenType.LT -> { advance(); ComparisonOperatorAst.LT }
            TokenType.LTE -> { advance(); ComparisonOperatorAst.LTE }
            // The one named operator the modern path understands, so a list variable can be tested
            // for membership. Normalised rather than compared literally, so it is recognised in the
            // same spellings the legacy path accepts.
            TokenType.IDENT if OperatorUtils.normalizeOperator(op = token.text) == OperatorNames.CONTAINS -> {
                advance()
                ComparisonOperatorAst.CONTAINS
            }

            // Recognised here, but routed by `isModernOnlyOperator`: only a named membership source
            // belongs on this path, and a literal list stays with the legacy operator.
            TokenType.IDENT if OperatorUtils.normalizeOperator(op = token.text) == OperatorNames.IN -> {
                advance()
                ComparisonOperatorAst.IN
            }

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
            // Must precede the field-access branch: `$` is not a legal identifier start, so a
            // variable read would otherwise be taken for a field path and reported as unknown.
            TokenType.IDENT if token.text.startsWith(prefix = "$") -> {
                advance()
                VariableRefAst(name = LiteralParser.referenceName(token = token))
            }
            // Must precede the field-access branch, or `isActive == true` would read `true` as a
            // field name and report an unknown field.
            TokenType.IDENT if LiteralParser.isBooleanText(text = token.text) -> {
                advance()
                LiteralValueAst(
                    literal = BooleanLiteral(value = token.text.equals(other = "true", ignoreCase = true))
                )
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
        if (current().type != TokenType.LPAREN) {
            return parseFieldPath(firstIdentifier = nameTok.text)
        }
        if (isSliceFunction(name = nameTok.text)) {
            return parseSlice(nameTok = nameTok)
        }
        advance()
        val args = mutableListOf<ValueExpressionAst>()
        while (current().type != TokenType.RPAREN && current().type != TokenType.EOF) {
            args += parseValueExpression()
            if (current().type == TokenType.COMMA) advance()
        }
        expect(type = TokenType.RPAREN)
        return FunctionCallValueAst(name = nameTok.text, arguments = args)
    }

    private fun isSliceFunction(name: String): Boolean {
        return name.equals(other = TAKE, ignoreCase = true) || name.equals(other = TAKE_LAST, ignoreCase = true)
    }

    /**
     * Reads `take(path, n)` / `takeLast(path, n)` and appends the slice to the path it narrows.
     *
     * Written as a call because that reads better than a bracket syntax, but it is not one: the
     * result is the same [FieldAccessAst] the path would have produced, with one more segment. That
     * is what lets `take(orders, 3).total` continue into `.total` — the path loop simply carries on
     * from here — and what keeps every later stage free of a second kind of collection expression.
     */
    private fun parseSlice(nameTok: Token): ValueExpressionAst {
        val start = current()
        advance()
        val target = parseValueExpression()
        if (target !is FieldAccessAst) {
            throw ParseException(
                line = start.line,
                column = start.col,
                messageText = "${nameTok.text}() expects a collection path as its first argument"
            )
        }
        expect(type = TokenType.COMMA)
        val countTok = current()
        if (countTok.type != TokenType.NUMBER) {
            throw ParseException(
                line = countTok.line,
                column = countTok.col,
                messageText = "${nameTok.text}() expects a number of elements, but found '${countTok.text}'"
            )
        }
        advance()
        expect(type = TokenType.RPAREN)
        val segments = target.path.toMutableList()
        segments += SliceSegmentAst(
            fromEnd = nameTok.text.equals(other = TAKE_LAST, ignoreCase = true),
            count = countTok.text
        )
        return parsePathContinuation(segments = segments)
    }

    /**
     * Reads a dotted path with optional `[...]` filter segments.
     *
     * A continuation token must sit on the same line as the token before it. A path is always
     * written on one line, whereas the token that follows a finished path may well be a `[` opening
     * the list argument of the next action — `set total = amount` followed by `tags ["a"]` would
     * otherwise silently read the list as a filter on `amount`.
     */
    private fun parseFieldPath(firstIdentifier: String): FieldAccessAst {
        return parsePathContinuation(segments = mutableListOf(FieldSegmentAst(name = firstIdentifier)))
    }

    private fun parsePathContinuation(segments: MutableList<PathSegmentAst>): FieldAccessAst {
        while (
            (current().type == TokenType.LBRACKET || current().type == TokenType.DOT) &&
            current().line == previousLine()
        ) {
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
        val start = current()
        val field = parseConditionField()
        val operator = parseConditionOperator()
        val value = parseConditionValue(operator = operator)
        val ignoreCase = parseIgnoreCaseModifier()

        return ConditionAst(
            field = field,
            operator = operator,
            value = value,
            ignoreCase = ignoreCase,
            line = start.line,
            column = start.col,
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
        return if (OperatorUtils.normalizeOperator(op = operator) == OperatorNames.BETWEEN) {
            literals.parseBetween()
        } else {
            literals.parse()
        }
    }

    private fun parseIgnoreCaseModifier(): Boolean {
        if (current().type == TokenType.IDENT && current().text == "ignoreCase") {
            advance()
            return true
        }

        return false
    }

}
