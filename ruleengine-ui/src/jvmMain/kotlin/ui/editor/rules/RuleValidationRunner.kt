package ui.editor.rules

import ruleengine.compiler.Validator
import ruleengine.core.domain.dto.action.ActionSchema
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.dsl.ast.AssignmentKindAst
import ruleengine.dsl.parser.Parser
import ui.editor.rules.model.RuleValidationOutcome

/**
 * The one place rule text is parsed and validated.
 *
 * Both the debounced background pass and the Validate button used to spell out the same two calls,
 * which is how they came to disagree about everything around them. This returns data only — the
 * status wording and what happens to the diagnostics panel stay with each caller, because those
 * genuinely differ and always did.
 *
 * [ruleText] is one file's worth of rules, so `inheritedVariables` is what keeps the check honest for a
 * manifest entry: the variables the files listed before this one publish. See
 * [inheritedVariablesForOpenBuffer].
 */
internal object RuleValidationRunner {

    fun run(
        ruleText: String,
        schema: FieldSchema,
        actions: ActionSchema?,
        inheritedVariables: Map<String, AssignmentKindAst> = emptyMap(),
    ): RuleValidationOutcome {
        return runCatching {
            val asts = Parser(input = ruleText).parseRules()
            val result = Validator.validate(
                asts = asts,
                schema = schema,
                actions = actions,
                inheritedVariables = inheritedVariables,
            )
            RuleValidationOutcome.Completed(isValid = result.isValid, diagnostics = result.diagnostics)
        }.getOrElse { cause -> RuleValidationOutcome.Threw(cause = cause) }
    }
}
