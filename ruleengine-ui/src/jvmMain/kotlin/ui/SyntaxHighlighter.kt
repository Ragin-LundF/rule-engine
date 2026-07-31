package ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import ruleengine.core.domain.dto.ActionSchema
import ruleengine.core.domain.dto.FieldSchema
import ruleengine.core.errors.Severity
import ruleengine.core.errors.ValidationDiagnostic
import ruleengine.dsl.lexer.Lexer
import ruleengine.dsl.lexer.TokenType
import ui.builder.OperatorOptions

// Syntax token colours are defined centrally in ui.Theme so the whole workbench
// shares the same palette; they are imported here from the ui package.

private val DSL_STRUCTURE = setOf("rule", "when", "then", "description")
private val DSL_LOGIC = setOf("and", "or", "not")
private val DSL_BOOL = setOf("true", "false")
private val OP_CHARS = setOf('>', '<', '=', '!')

/** Named operator keywords recognized in condition expressions. */
private val DSL_NAMED_OPS = setOf(
    "equals", "contains", "startsWith", "endsWith", "in", "regex",
    "gt", "gte", "lt", "lte", "between",
    "containsAny", "containsAll",
    "ignoreCase",
)

/** Aggregate function names recognized in value expressions. */
private val DSL_FUNCTIONS = OperatorOptions.AGGREGATE_FUNCTIONS.toSet()

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

    val fieldNames = schema?.fields?.flatMap { (id, field) ->
        listOf(id.value, field.alias).filter { it != null && it.isNotBlank() }
    }?.toSet() ?: emptySet()
    val actionNames = actions?.actions?.keys?.toSet() ?: emptySet()

    // Pre-compute line start offsets for fast (line, col) → absolute offset mapping.
    val lineStartOffsets = buildList<Int> {
        add(0)
        text.forEachIndexed { idx, ch -> if (ch == '\n') add(idx + 1) }
    }

    fun absOffset(line: Int, col: Int): Int {
        val li = (line - 1).coerceIn(0, lineStartOffsets.lastIndex)
        return (lineStartOffsets[li] + (col - 1).coerceAtLeast(0)).coerceAtMost(text.length)
    }

    // Collect comment ranges (# … end-of-line) – used for styling and skip-check.
    val commentRanges = buildList<IntRange> {
        var inComment = false;
        var start = 0
        text.forEachIndexed { idx, ch ->
            when {
                ch == '#' && !inComment -> {
                    inComment = true; start = idx
                }

                ch == '\n' && inComment -> {
                    add(start until idx); inComment = false
                }
            }
        }
        if (inComment) add(start until text.length)
    }

    return buildAnnotatedString {
        append(text)

        // ── Comments (italic, muted) ───────────────────────────────────────────
        for (r in commentRanges) {
            addStyle(
                SpanStyle(color = TextMuted, fontStyle = FontStyle.Italic),
                r.first, (r.last + 1).coerceAtMost(text.length)
            )
        }

        // ── Token-based colours (Lexer now natively skips # comments) ─────────
        try {
            for (tok in Lexer(input = text).tokenize()) {
                if (tok.type == TokenType.EOF) break
                val start = absOffset(tok.line, tok.col)
                val end = (start + tok.text.length).coerceAtMost(maximumValue = text.length)
                if (start !in 0..<end) continue
                if (commentRanges.any { start in it }) continue // inside comment – skip

                val style: SpanStyle = when (tok.type) {
                    TokenType.STRING -> SpanStyle(color = ColorString)
                    TokenType.NUMBER -> SpanStyle(color = ColorNumber)
                    TokenType.LBRACE, TokenType.RBRACE,
                    TokenType.LPAREN, TokenType.RPAREN,
                    TokenType.LBRACKET, TokenType.RBRACKET,
                    TokenType.COMMA -> SpanStyle(color = TextSecondary)

                    TokenType.PLUS, TokenType.MINUS,
                    TokenType.STAR, TokenType.SLASH,
                    TokenType.EQEQ, TokenType.BANGEQ,
                    TokenType.GT, TokenType.GTE,
                    TokenType.LT, TokenType.LTE -> SpanStyle(color = ColorOp)

                    TokenType.IDENT -> when {
                        tok.text in DSL_STRUCTURE -> SpanStyle(color = ColorKeyword, fontWeight = FontWeight.SemiBold)
                        tok.text in DSL_LOGIC -> SpanStyle(color = ColorLogic, fontWeight = FontWeight.Medium)
                        tok.text in DSL_BOOL -> SpanStyle(color = ColorNumber)
                        tok.text.all { it in OP_CHARS } -> SpanStyle(color = ColorOp)
                        tok.text in fieldNames -> SpanStyle(color = ColorField)
                        tok.text in DSL_NAMED_OPS -> SpanStyle(color = ColorOp)
                        tok.text in DSL_FUNCTIONS -> SpanStyle(color = ColorOp, fontWeight = FontWeight.Medium)
                        tok.text in actionNames -> SpanStyle(color = ColorAction)
                        else -> SpanStyle(color = TextPrimary)
                    }

                    else -> continue
                }
                addStyle(style, start, end)
            }
        } catch (_: Exception) { /* partial / invalid text during editing – ignore */
        }

        // ── Diagnostic underlines ──────────────────────────────────────────────
        val textLines = text.lines()
        for (diag in diagnostics) {
            val line = diag.line ?: continue
            val col = (diag.column ?: 1).coerceAtLeast(minimumValue = 1)
            var off = 0
            for (l in 0 until minOf(line - 1, textLines.size)) off += textLines[l].length + 1
            off += (col - 1).coerceAtLeast(minimumValue = 0)
            if (off >= text.length) continue
            val lineIdx = (line - 1).coerceIn(0, textLines.lastIndex)
            val endOff = (off + textLines[lineIdx].length - (col - 1)).coerceAtMost(maximumValue = text.length)
            if (off >= endOff) continue
            val uc = when (diag.severity) {
                Severity.ERROR -> AccentRed
                Severity.WARNING -> AccentOrange
                else -> PrimaryBlue
            }
            addStyle(
                style = SpanStyle(textDecoration = TextDecoration.Underline, color = uc),
                start = off,
                end = endOff
            )
        }
    }
}

