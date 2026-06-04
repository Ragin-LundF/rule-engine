package ui
import ruleengine.core.domain.FieldSchema
import ruleengine.dsl.lexer.Lexer
import ruleengine.dsl.lexer.Token
import ruleengine.dsl.lexer.TokenType
/** The structural section of the rule DSL where the cursor is located. */
enum class DslSection { TOP_LEVEL, RULE_HEADER, WHEN, THEN }
/**
 * Describes the editing context at the cursor position inside the rule DSL.
 *
 * @property section          The DSL block the cursor is currently in.
 * @property precedingField   The field name immediately before the cursor (if in WHEN and no operator yet).
 * @property precedingOperator The operator after the preceding field (if no value consumed yet).
 * @property afterAction      The action name on the current THEN line (if no argument consumed yet).
 */
data class DslCursorContext(
    val section: DslSection,
    val precedingField: String? = null,
    val precedingOperator: String? = null,
    val afterAction: String? = null,
)
private val DSL_LOGIC_WORDS = setOf("and", "or", "not")
/**
 * Analyzes the rule DSL text to determine what section the cursor is in and
 * what tokens immediately precede the current word being typed.
 *
 * Tokenizes only the prefix up to [cursorPos] (excluding the current partial word)
 * to avoid confusion from incomplete tokens at the end of the prefix.
 */
fun analyzeDslContext(
    text: String,
    cursorPos: Int,
    schema: FieldSchema?,
): DslCursorContext {
    val fieldNames = schema?.fields?.keys?.map { it.value }?.toSet() ?: emptySet()
    return runCatching {
        val wordStart = findWordStart(text = text, cursorPos = cursorPos)
        val prefix = text.substring(startIndex = 0, endIndex = wordStart)
        val tokens = Lexer(input = prefix).tokenize()
            .filter { it.type != TokenType.EOF }
        buildContextFromTokens(tokens = tokens, fieldNames = fieldNames)
    }.getOrElse {
        DslCursorContext(section = DslSection.TOP_LEVEL)
    }
}
/** Returns the character index where the currently typed word begins. */
internal fun findWordStart(text: String, cursorPos: Int): Int {
    val cursor = cursorPos.coerceIn(0, text.length)
    var start = cursor
    while (start > 0) {
        val ch = text[start - 1]
        if (ch.isLetterOrDigit() || ch == '_' || ch == '-') start-- else break
    }
    return start
}
/**
 * Walks the token list to derive the current DSL editing context.
 * [fieldNames] is retained for future schema-driven disambiguation; currently any IDENT
 * not matching a logic word is treated as a potential field, which gives correct
 * completion behavior without false negatives.
 */
@Suppress("CyclomaticComplexMethod", "LongMethod", "UnusedParameter")
private fun buildContextFromTokens(
    tokens: List<Token>,
    @Suppress("UNUSED_PARAMETER") fieldNames: Set<String>,
): DslCursorContext {
    var braceDepth = 0
    var section = DslSection.TOP_LEVEL
    var precedingField: String? = null
    var precedingOperator: String? = null
    var afterAction: String? = null
    for (tok in tokens) {
        when (tok.type) {
            TokenType.LBRACE -> {
                braceDepth++
                if (braceDepth == 1) {
                    section = DslSection.RULE_HEADER
                }
            }
            TokenType.RBRACE -> {
                braceDepth--
                if (braceDepth <= 0) {
                    braceDepth = 0
                    section = DslSection.TOP_LEVEL
                    precedingField = null
                    precedingOperator = null
                    afterAction = null
                }
            }
            TokenType.IDENT -> {
                if (braceDepth == 1 && tok.text == "when") {
                    section = DslSection.WHEN
                    precedingField = null
                    precedingOperator = null
                    continue
                }
                if (braceDepth == 1 && tok.text == "then") {
                    section = DslSection.THEN
                    afterAction = null
                    continue
                }
                when (section) {
                    DslSection.WHEN -> {
                        when {
                            tok.text in DSL_LOGIC_WORDS -> {
                                precedingField = null
                                precedingOperator = null
                            }
                            tok.text == "ignoreCase" -> { /* modifier — no state change */ }
                            precedingField != null && precedingOperator == null -> {
                                precedingOperator = tok.text
                            }
                            precedingField == null -> {
                                precedingField = tok.text
                                precedingOperator = null
                            }
                            else -> {
                                precedingField = tok.text
                                precedingOperator = null
                            }
                        }
                    }
                    DslSection.THEN -> {
                        afterAction = tok.text
                    }
                    else -> {}
                }
            }
            TokenType.STRING, TokenType.NUMBER -> {
                when (section) {
                    DslSection.WHEN -> {
                        if (precedingOperator != null) {
                            precedingField = null
                            precedingOperator = null
                        }
                    }
                    DslSection.THEN -> {
                        afterAction = null
                    }
                    else -> {}
                }
            }
            TokenType.RBRACKET -> {
                when (section) {
                    DslSection.WHEN -> {
                        precedingField = null
                        precedingOperator = null
                    }
                    DslSection.THEN -> {
                        afterAction = null
                    }
                    else -> {}
                }
            }
            else -> {}
        }
    }
    return DslCursorContext(
        section = section,
        precedingField = precedingField,
        precedingOperator = precedingOperator,
        afterAction = afterAction,
    )
}
