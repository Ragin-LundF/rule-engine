package ruleengine.intellij.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import ruleengine.core.analysis.VariableUsage
import ruleengine.dsl.ast.rule.AssignmentKindAst
import ruleengine.dsl.parser.Parser
import ruleengine.intellij.lang.RuleFile
import ruleengine.intellij.manifest.RuleSourcesLoader
import ui.autocompletion.buildContextualCompletions
import ui.autocompletion.model.CompletionItem
import ui.autocompletion.model.CompletionKind
import ui.dsl.analyzeDslContext
import javax.swing.Icon

/**
 * Completion for rule files, from the same builders as the UI editor (`ui.autocompletion`).
 *
 * Without a linked manifest it still offers keywords, operators and functions; fields and actions
 * need the schema.
 */
class RuleCompletionContributor : CompletionContributor() {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val file = parameters.originalFile as? RuleFile ?: return
        val text = parameters.editor.document.text
        val offset = parameters.offset
        val prefix = CompletionPrefix.before(text = text, offset = offset)
        val sources = RuleSourcesLoader.sourcesFor(ruleFile = file)
        val variableKinds = variableKindsOf(text = text)
        // Read the context before the whole dotted prefix: the UI's scan would see `customer.` as a
        // finished field and offer operators instead of the rest of the path.
        val context = analyzeDslContext(text = text, cursorPos = offset - prefix.length)
        val items = buildContextualCompletions(
            context = context,
            schema = sources?.schema,
            actionSchema = sources?.actions,
            variableNames = variableKinds.keys.toList(),
            variableKinds = variableKinds,
        )
        result.withPrefixMatcher(prefix)
            .addAllElements(items.map { item -> lookupOf(item = item) })
    }

    /** Variables the open text writes, as the UI editor derives them; empty while the text does not parse. */
    private fun variableKindsOf(text: String): Map<String, AssignmentKindAst> {
        return runCatching {
            Parser(input = text).parseRules()
                .flatMap { rule -> VariableUsage.writeKindsOf(rule = rule).entries }
                .associate { entry -> entry.key to entry.value }
        }.getOrDefault(defaultValue = emptyMap())
    }

    private fun lookupOf(item: CompletionItem): LookupElement {
        val element = LookupElementBuilder.create(item, item.insertText)
            .withLookupString(item.label)
            .withPresentableText(item.label)
            .withTypeText(item.hint)
            .withIcon(iconOf(kind = item.kind))
        // The UI lists by kind first; a higher priority sorts higher, so the first kind gets the highest.
        val priority = (CompletionKind.entries.size - item.kind.ordinal).toDouble()
        return PrioritizedLookupElement.withPriority(element, priority)
    }

    private fun iconOf(kind: CompletionKind): Icon? {
        return when (kind) {
            CompletionKind.FIELD -> AllIcons.Nodes.Field
            CompletionKind.ACTION -> AllIcons.Nodes.Method
            CompletionKind.OPERATOR -> AllIcons.Nodes.Function
            CompletionKind.KEYWORD, CompletionKind.LOGIC, CompletionKind.LITERAL -> null
        }
    }
}
