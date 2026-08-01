package ruleengine.dsl.parser

import ruleengine.dsl.ast.ActionAst
import ruleengine.dsl.ast.ExtractionAst
import ruleengine.dsl.ast.ValueExpressionAst
import ruleengine.dsl.ast.VariableAssignmentAst
import ruleengine.dsl.diagnostics.ParseException
import ruleengine.dsl.lexer.Token
import ruleengine.dsl.lexer.TokenType

/**
 * The body of a `then` block: actions, `extract` clauses and `set` clauses.
 *
 * Split out of [Parser], which owns the `when` side and the value-expression grammar. The right-hand
 * side of a `set` is an ordinary value expression, so it is parsed by calling back into
 * [parseValueExpression] rather than by restating the grammar here.
 */
internal class ThenBlockParser(
    private val cursor: TokenCursor,
    private val literals: LiteralParser,
    private val parseValueExpression: () -> ValueExpressionAst,
) {

    fun parse(): ThenBlock {
        val actions = mutableListOf<ActionAst>()
        val assignments = mutableListOf<VariableAssignmentAst>()
        while (true) {
            val token = cursor.current()
            if (token.type == TokenType.RBRACE || token.type == TokenType.EOF) {
                break
            }

            if (token.type != TokenType.IDENT) {
                throw ParseException(
                    line = token.line,
                    column = token.col,
                    messageText = "Expected action identifier but found ${token.text}"
                )
            }

            when (token.text) {
                "extract" -> {
                    cursor.advance()
                    actions += parseExtractAction()
                }

                "set" -> {
                    cursor.advance()
                    assignments += parseAssignment(setToken = token)
                }

                else -> actions += parseAction(nameToken = token)
            }
        }

        return ThenBlock(actions = actions, assignments = assignments)
    }

    private fun parseAction(nameToken: Token): ActionAst {
        cursor.advance()

        // The argument is optional: an action declared with `argTypes: []` takes none, so a literal
        // is only consumed when one actually follows. Argument count is checked against the action
        // schema by the validator.
        val argument = if (LiteralParser.startsLiteral(token = cursor.current())) literals.parseOrRef() else null
        return ActionAst(name = nameToken.text, arguments = listOfNotNull(argument))
    }

    /**
     * Parses the body of a `set` clause, with the `set` keyword already consumed:
     * ```
     * set <name> = <valueExpression>
     * ```
     *
     * The right-hand side is an ordinary value expression, so aggregates, arithmetic, field paths
     * with filters and reads of earlier variables all work without any dedicated syntax.
     */
    private fun parseAssignment(setToken: Token): VariableAssignmentAst {
        val nameTok = cursor.current()
        if (nameTok.type != TokenType.IDENT || nameTok.text.startsWith(prefix = "$")) {
            throw ParseException(
                line = nameTok.line,
                column = nameTok.col,
                messageText = "Expected variable name after 'set' but found '${nameTok.text}'; " +
                        "write the name without the '\$' prefix"
            )
        }
        cursor.advance()

        val eqTok = cursor.current()
        if (eqTok.type != TokenType.IDENT || eqTok.text != "=") {
            throw ParseException(
                line = eqTok.line,
                column = eqTok.col,
                messageText = "Expected '=' after variable name '${nameTok.text}' but found '${eqTok.text}'"
            )
        }
        cursor.advance()

        return VariableAssignmentAst(
            name = nameTok.text,
            expression = parseValueExpression(),
            line = setToken.line,
            column = setToken.col,
        )
    }

    /**
     * Parses the body of an `extract` clause:
     * ```
     * extract <sourceField> regex("<pattern>", <groupIndex>) <actionName> <arg>
     * ```
     */
    @Suppress("ThrowsCount")
    private fun parseExtractAction(): ActionAst {
        val fieldTok = cursor.current()
        if (fieldTok.type != TokenType.IDENT) {
            throw ParseException(
                line = fieldTok.line,
                column = fieldTok.col,
                messageText = "Expected source field name after 'extract' but found '${fieldTok.text}'"
            )
        }
        val sourceField = fieldTok.text
        cursor.advance()

        val extraction = parseRegexExtraction(sourceField = sourceField)

        val actionNameTok = cursor.current()
        if (actionNameTok.type != TokenType.IDENT) {
            throw ParseException(
                line = actionNameTok.line,
                column = actionNameTok.col,
                messageText = "Expected action name after extraction definition but found '${actionNameTok.text}'"
            )
        }
        cursor.advance()

        return ActionAst(
            name = actionNameTok.text,
            arguments = listOf(literals.parseOrRef()),
            extraction = extraction
        )
    }

    /** The `regex("<pattern>", <groupIndex>)` part of an `extract` clause. */
    @Suppress("ThrowsCount")
    private fun parseRegexExtraction(sourceField: String): ExtractionAst.RegexExtraction {
        val methodTok = cursor.current()
        if (methodTok.type != TokenType.IDENT || methodTok.text != "regex") {
            throw ParseException(
                line = methodTok.line,
                column = methodTok.col,
                messageText = "Expected extraction method 'regex' but found '${methodTok.text}'"
            )
        }
        cursor.advance()

        cursor.expect(type = TokenType.LPAREN)
        val patternTok = cursor.expect(type = TokenType.STRING)
        cursor.expect(type = TokenType.COMMA)
        val groupIndexTok = cursor.current()
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
        cursor.advance()
        cursor.expect(type = TokenType.RPAREN)

        return ExtractionAst.RegexExtraction(
            sourceField = sourceField,
            pattern = patternTok.text,
            groupIndex = groupIndex
        )
    }
}
