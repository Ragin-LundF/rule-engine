package ui.dsl

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import ruleengine.core.domain.OperatorNames
import ruleengine.core.domain.dto.action.ActionSchema
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.errors.Severity
import ruleengine.core.errors.ValidationDiagnostic
import ruleengine.dsl.lexer.Lexer
import ruleengine.dsl.lexer.Token
import ruleengine.dsl.lexer.TokenType
import ui.AccentOrange
import ui.AccentRed
import ui.ColorAction
import ui.ColorField
import ui.ColorKeyword
import ui.ColorLogic
import ui.ColorNumber
import ui.ColorOp
import ui.ColorString
import ui.PrimaryBlue
import ui.TextMuted
import ui.TextPrimary
import ui.TextSecondary
import ui.builder.OperatorOptions

// Syntax token colours are defined centrally in ui.Theme so the whole workbench
// shares the same palette; they are imported here from the ui package.

private val DSL_STRUCTURE = setOf("rule", "when", "then", "else", "stop", "description", "set", "add", "to")
private val DSL_LOGIC = setOf("and", "or", "not")
private val DSL_BOOL = setOf("true", "false")
private val OP_CHARS = setOf('>', '<', '=', '!')

/** Named operator keywords recognized in condition expressions. */
private val DSL_NAMED_OPS = setOf(
    *OperatorNames.ALL.toTypedArray(),
    "ignoreCase",
)

/** Aggregate function names recognized in value expressions. */
private val DSL_FUNCTIONS = OperatorOptions.AGGREGATE_FUNCTIONS.toSet()

/** The names a token can be recognised as, which depend on the loaded schema rather than the DSL. */
private class KnownNames(val fields: Set<String?>, val actions: Set<String>)

/**
 * Builds an [AnnotatedString] with syntax colouring for the rule DSL.
 * Accepts optional [diagnostics] to render error/warning underlines.
 */
fun annotateRule(
    text: String,
    schema: FieldSchema?,
    actions: ActionSchema?,
    diagnostics: List<ValidationDiagnostic> = emptyList(),
): AnnotatedString {
    if (text.isEmpty()) return AnnotatedString(text)

    val known = KnownNames(
        fields = schema?.fields?.flatMap { (id, field) ->
            listOf(id.value, field.alias).filter { it != null && it.isNotBlank() }
        }?.toSet() ?: emptySet(),
        actions = actions?.actions?.keys?.toSet() ?: emptySet(),
    )
    val commentRanges = commentRangesOf(text = text)

    return buildAnnotatedString {
        append(text)
        styleComments(text = text, commentRanges = commentRanges)
        styleTokens(text = text, commentRanges = commentRanges, known = known)
        underlineDiagnostics(text = text, diagnostics = diagnostics)
    }
}

/**
 * Collects `#` … end-of-line ranges.
 *
 * Used twice: to style the comments, and to stop the token pass recolouring keywords that happen to
 * sit inside one. An unterminated comment runs to the end of the text.
 */
private fun commentRangesOf(text: String): List<IntRange> = buildList {
    var inComment = false
    var start = 0
    text.forEachIndexed { idx, ch ->
        when {
            ch == '#' && !inComment -> {
                inComment = true
                start = idx
            }

            ch == '\n' && inComment -> {
                add(start until idx)
                inComment = false
            }
        }
    }
    if (inComment) add(start until text.length)
}

private fun AnnotatedString.Builder.styleComments(text: String, commentRanges: List<IntRange>) {
    for (r in commentRanges) {
        addStyle(
            SpanStyle(color = TextMuted, fontStyle = FontStyle.Italic),
            r.first,
            (r.last + 1).coerceAtMost(text.length),
        )
    }
}

/**
 * Applies token colours, ignoring anything the lexer chokes on.
 *
 * The whole pass is guarded: half-written text failing to tokenize is the normal state while
 * someone is typing, and colouring is not worth an exception. `tokenize()` returns a materialised
 * list, so a failure means no token was styled — not a half-styled buffer.
 */
private fun AnnotatedString.Builder.styleTokens(
    text: String,
    commentRanges: List<IntRange>,
    known: KnownNames,
) {
    val lineStarts = lineStartOffsetsOf(text = text)
    try {
        Lexer(input = text).tokenize()
            .takeWhile { it.type != TokenType.EOF }
            .forEach { tok ->
                styleToken(
                    tok = tok,
                    text = text,
                    lineStarts = lineStarts,
                    commentRanges = commentRanges,
                    known = known,
                )
            }
    } catch (_: Exception) {
        // partial / invalid text during editing – ignore
    }
}

private fun AnnotatedString.Builder.styleToken(
    tok: Token,
    text: String,
    lineStarts: List<Int>,
    commentRanges: List<IntRange>,
    known: KnownNames,
) {
    val start = absOffset(lineStarts = lineStarts, textLength = text.length, line = tok.line, col = tok.col)
    val end = (start + tok.text.length).coerceAtMost(maximumValue = text.length)
    if (start !in 0..<end) return
    if (commentRanges.any { start in it }) return // inside comment – skip
    val style = tokenStyle(tok = tok, known = known) ?: return
    addStyle(style, start, end)
}

/** The colour for one token, or null for token types that are left as-is. */
private fun tokenStyle(tok: Token, known: KnownNames): SpanStyle? = when (tok.type) {
    TokenType.STRING -> SpanStyle(color = ColorString)
    TokenType.NUMBER -> SpanStyle(color = ColorNumber)
    TokenType.LBRACE, TokenType.RBRACE,
    TokenType.LPAREN, TokenType.RPAREN,
    TokenType.LBRACKET, TokenType.RBRACKET,
    TokenType.COMMA,
    -> SpanStyle(color = TextSecondary)

    TokenType.PLUS, TokenType.MINUS,
    TokenType.STAR, TokenType.SLASH,
    TokenType.EQEQ, TokenType.BANGEQ,
    TokenType.GT, TokenType.GTE,
    TokenType.LT, TokenType.LTE,
    -> SpanStyle(color = ColorOp)

    TokenType.IDENT -> identifierStyle(word = tok.text, known = known)

    else -> null
}

/**
 * The colour for a bare word.
 *
 * Order matters: the DSL's own vocabulary wins over schema names, so a field unluckily called `and`
 * still reads as the logic keyword it will be parsed as.
 */
private fun identifierStyle(word: String, known: KnownNames): SpanStyle = when {
    word in DSL_STRUCTURE -> SpanStyle(color = ColorKeyword, fontWeight = FontWeight.SemiBold)
    word in DSL_LOGIC -> SpanStyle(color = ColorLogic, fontWeight = FontWeight.Medium)
    word in DSL_BOOL -> SpanStyle(color = ColorNumber)
    word.all { it in OP_CHARS } -> SpanStyle(color = ColorOp)
    word in known.fields -> SpanStyle(color = ColorField)
    word in DSL_NAMED_OPS -> SpanStyle(color = ColorOp)
    word in DSL_FUNCTIONS -> SpanStyle(color = ColorOp, fontWeight = FontWeight.Medium)
    word in known.actions -> SpanStyle(color = ColorAction)
    else -> SpanStyle(color = TextPrimary)
}

/** Underlines each positioned diagnostic from its column to the end of its line. */
private fun AnnotatedString.Builder.underlineDiagnostics(text: String, diagnostics: List<ValidationDiagnostic>) {
    val textLines = text.lines()
    diagnostics.forEach { diag ->
        underlineDiagnostic(diag = diag, text = text, textLines = textLines)
    }
}

private fun AnnotatedString.Builder.underlineDiagnostic(
    diag: ValidationDiagnostic,
    text: String,
    textLines: List<String>,
) {
    val line = diag.line ?: return
    val col = (diag.column ?: 1).coerceAtLeast(minimumValue = 1)
    var off = 0
    for (l in 0 until minOf(line - 1, textLines.size)) off += textLines[l].length + 1
    off += (col - 1).coerceAtLeast(minimumValue = 0)
    if (off >= text.length) return
    val lineIdx = (line - 1).coerceIn(0, textLines.lastIndex)
    val endOff = (off + textLines[lineIdx].length - (col - 1)).coerceAtMost(maximumValue = text.length)
    if (off >= endOff) return
    val uc = when (diag.severity) {
        Severity.ERROR -> AccentRed
        Severity.WARNING -> AccentOrange
        else -> PrimaryBlue
    }
    addStyle(
        style = SpanStyle(textDecoration = TextDecoration.Underline, color = uc),
        start = off,
        end = endOff,
    )
}

/** Line start offsets, for fast (line, col) → absolute offset mapping. */
private fun lineStartOffsetsOf(text: String): List<Int> = buildList {
    add(0)
    text.forEachIndexed { idx, ch -> if (ch == '\n') add(idx + 1) }
}

private fun absOffset(lineStarts: List<Int>, textLength: Int, line: Int, col: Int): Int {
    val li = (line - 1).coerceIn(0, lineStarts.lastIndex)
    return (lineStarts[li] + (col - 1).coerceAtLeast(0)).coerceAtMost(textLength)
}
