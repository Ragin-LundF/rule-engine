package ui.dsl

import ruleengine.dsl.lexer.Lexer
import ruleengine.dsl.lexer.Token
import ruleengine.dsl.lexer.TokenType
import ui.dsl.model.DslCursorContext
import ui.dsl.model.DslSection
import ui.util.Words

private val DSL_LOGIC_WORDS = setOf("and", "or", "not")

/**
 * Analyzes the rule DSL text to determine what section the cursor is in and
 * what tokens immediately precede the current word being typed.
 *
 * Tokenizes only the prefix up to [cursorPos] (excluding the current partial word)
 * to avoid confusion from incomplete tokens at the end of the prefix.
 *
 * Takes no schema: the scan classifies a word by its position in the grammar, not by whether the
 * schema knows it, which is what keeps completions working before a schema has loaded.
 */
fun analyzeDslContext(text: String, cursorPos: Int): DslCursorContext {
    return runCatching {
        val wordStart = Words.wordStart(text = text, cursorPos = cursorPos)
        val prefix = text.substring(startIndex = 0, endIndex = wordStart)
        val tokens = Lexer(input = prefix).tokenize()
            .filter { it.type != TokenType.EOF }
        buildContextFromTokens(tokens = tokens)
    }.getOrElse {
        DslCursorContext(section = DslSection.TOP_LEVEL)
    }
}

/** Walks the token list to derive the current DSL editing context. */
private fun buildContextFromTokens(tokens: List<Token>): DslCursorContext {
    val scan = DslScan()
    tokens.forEach(scan::accept)
    return scan.toContext()
}

/**
 * The state a token stream is folded into: where the cursor sits and what precedes it.
 *
 * A class rather than five locals in a loop because the transitions are what this file is about —
 * each token type gets its own named handler, so the rules for one are readable without tracing the
 * others. The schema's field names are deliberately not consulted: any IDENT that is not a logic
 * word is treated as a potential field, which gives correct completions without false negatives on
 * a schema that has not loaded yet.
 */
private class DslScan {
    private var braceDepth = 0
    private var section = DslSection.TOP_LEVEL
    private var precedingField: String? = null
    private var precedingOperator: String? = null
    private var afterAction: String? = null
    private var expectsListName = false

    fun accept(tok: Token) {
        when (tok.type) {
            TokenType.LBRACE -> openBrace()
            TokenType.RBRACE -> closeBrace()
            TokenType.IDENT -> identifier(word = tok.text)
            TokenType.STRING, TokenType.NUMBER -> literal()
            TokenType.RBRACKET -> closeBracket()
            else -> Unit
        }
    }

    fun toContext(): DslCursorContext = DslCursorContext(
        section = section,
        precedingField = precedingField,
        precedingOperator = precedingOperator,
        afterAction = afterAction,
        expectsListName = expectsListName,
    )

    private fun openBrace() {
        braceDepth++
        if (braceDepth == 1) {
            section = DslSection.RULE_HEADER
        }
    }

    /** Leaving the rule body resets everything: the next token starts a new rule. */
    private fun closeBrace() {
        braceDepth--
        if (braceDepth <= 0) {
            braceDepth = 0
            section = DslSection.TOP_LEVEL
            precedingField = null
            precedingOperator = null
            afterAction = null
            expectsListName = false
        }
    }

    private fun identifier(word: String) {
        // `when`/`then`/`else` are section markers only at the rule's own depth — nested inside a
        // bracket expression they are ordinary words.
        if (braceDepth == 1 && word == "when") {
            section = DslSection.WHEN
            precedingField = null
            precedingOperator = null
            return
        }
        if (braceDepth == 1 && word == "then") {
            section = DslSection.THEN
            afterAction = null
            expectsListName = false
            return
        }
        if (braceDepth == 1 && word == "else") {
            section = DslSection.ELSE
            afterAction = null
            expectsListName = false
            return
        }
        when (section) {
            DslSection.WHEN -> whenIdentifier(word = word)
            // Both branches take the same clauses, so the last word read is tracked the same way.
            DslSection.THEN, DslSection.ELSE -> branchIdentifier(word = word)
            else -> Unit
        }
    }

    /**
     * A word in a `then` or `else` block.
     *
     * `set`, `add` and `to` are clause structure, not action names — treating them as actions would
     * offer the argument completions of an action called `add`. After the `to` of an `add` clause the
     * next word is the accumulator's bare name, which is its own kind of completion.
     */
    private fun branchIdentifier(word: String) {
        when (word) {
            "set", "add" -> {
                afterAction = null
                expectsListName = false
            }

            "to" -> {
                afterAction = null
                expectsListName = true
            }

            else -> {
                afterAction = word
                expectsListName = false
            }
        }
    }

    private fun whenIdentifier(word: String) {
        when {
            word in DSL_LOGIC_WORDS -> {
                precedingField = null
                precedingOperator = null
            }

            word == "ignoreCase" -> Unit // modifier — no state change

            // The first word of a comparison is the field, the second its operator. Anything
            // arriving after both starts a new comparison.
            precedingField != null && precedingOperator == null -> precedingOperator = word

            else -> {
                precedingField = word
                precedingOperator = null
            }
        }
    }

    /** A literal completes a comparison, or the argument of an action. */
    private fun literal() {
        when (section) {
            DslSection.WHEN -> if (precedingOperator != null) {
                precedingField = null
                precedingOperator = null
            }

            DslSection.THEN -> {
                afterAction = null
                expectsListName = false
            }

            else -> Unit
        }
    }

    /** The end of a bracket expression leaves no partial comparison behind. */
    private fun closeBracket() {
        when (section) {
            DslSection.WHEN -> {
                precedingField = null
                precedingOperator = null
            }

            DslSection.THEN -> afterAction = null
            else -> Unit
        }
    }
}
