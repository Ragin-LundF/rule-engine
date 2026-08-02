package ruleengine.compiler.support

import ruleengine.core.analysis.VariableUsage
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.errors.Severity
import ruleengine.core.errors.ValidationDiagnostic
import ruleengine.dsl.ast.ActionAst
import ruleengine.dsl.ast.RuleAst
import ruleengine.dsl.ast.VariableAssignmentAst

/**
 * Checks that every `$name` read resolves to a variable some earlier rule assigns.
 *
 * The check runs over the whole manifest entry at once, in the order the engine will evaluate the
 * rules (manifest file order, then in-file source order), which is what makes "earlier" meaningful.
 * Within one rule the order is condition → `set` clauses → actions, matching how `RuleEngine`
 * applies assignments before resolving actions.
 *
 * It is deliberately an over-approximation: a variable assigned by a rule that does not match at
 * runtime still counts as defined here, and reads as missing during evaluation. The check exists to
 * catch typos and forward references, not to prove a variable is always populated. A `set` in an
 * `else` block counts the same way — only one branch of a rule ever runs, and which one is a runtime
 * question this check does not ask.
 */
internal object VariableScopeValidator {

    fun validate(
        asts: List<RuleAst>,
        schema: FieldSchema,
        diagnostics: MutableList<ValidationDiagnostic>
    ) {
        val fieldNames = fieldNames(schema = schema)
        val defined = linkedSetOf<String>()
        val assignedBy = mutableMapOf<String, String>()

        for (rule in asts) {
            checkReads(
                names = VariableUsage.readsOfExpression(expr = rule.condition),
                rule = rule,
                defined = defined,
                diagnostics = diagnostics
            )

            // Both branches are walked, in source order. Only one of them runs for a given record,
            // but which one is a runtime question the over-approximation deliberately does not ask.
            checkBranch(
                assignments = rule.assignments,
                actions = rule.actions,
                rule = rule,
                fieldNames = fieldNames,
                defined = defined,
                assignedBy = assignedBy,
                diagnostics = diagnostics
            )
            checkBranch(
                assignments = rule.elseAssignments,
                actions = rule.elseActions,
                rule = rule,
                fieldNames = fieldNames,
                defined = defined,
                assignedBy = assignedBy,
                diagnostics = diagnostics
            )
        }
    }

    /** The `set` clauses and actions of one branch, in the order the engine applies them. */
    @Suppress("LongParameterList")
    private fun checkBranch(
        assignments: List<VariableAssignmentAst>,
        actions: List<ActionAst>,
        rule: RuleAst,
        fieldNames: Set<String>,
        defined: MutableSet<String>,
        assignedBy: MutableMap<String, String>,
        diagnostics: MutableList<ValidationDiagnostic>
    ) {
        for (assignment in assignments) {
            checkReads(
                names = VariableUsage.readsOfValue(expr = assignment.expression),
                rule = rule,
                defined = defined,
                diagnostics = diagnostics
            )
            checkAssignment(
                assignment = assignment,
                rule = rule,
                fieldNames = fieldNames,
                assignedBy = assignedBy,
                diagnostics = diagnostics
            )
            defined += assignment.name
        }

        // Checked after the assignments: an action of the same rule sees what that rule just set.
        checkReads(
            names = VariableUsage.readsOfActions(actions = actions),
            rule = rule,
            defined = defined,
            diagnostics = diagnostics
        )
    }

    private fun checkReads(
        names: Set<String>,
        rule: RuleAst,
        defined: Set<String>,
        diagnostics: MutableList<ValidationDiagnostic>
    ) {
        for (name in names) {
            if (name in defined) {
                continue
            }
            diagnostics += ValidationDiagnostic(
                severity = Severity.ERROR,
                message = "Rule '${rule.id}' reads unknown variable '\$$name'; " +
                        "no earlier rule assigns it with a 'set' clause",
                suggestion = Suggestions.suggestClosest(input = name, candidates = defined.toList())
                    ?.let { closest -> "Did you mean '\$$closest'?" },
                line = rule.line,
                column = rule.column,
            )
        }
    }

    private fun checkAssignment(
        assignment: VariableAssignmentAst,
        rule: RuleAst,
        fieldNames: Set<String>,
        assignedBy: MutableMap<String, String>,
        diagnostics: MutableList<ValidationDiagnostic>
    ) {
        if (assignment.name in fieldNames) {
            diagnostics += ValidationDiagnostic(
                severity = Severity.ERROR,
                message = "Rule '${rule.id}' assigns variable '${assignment.name}', " +
                        "which is also the name of a schema field",
                suggestion = "Rename the variable so a reader cannot confuse '\$${assignment.name}' " +
                        "with the field '${assignment.name}'",
                line = assignment.line,
                column = assignment.column,
            )
            return
        }

        val previous = assignedBy.put(assignment.name, rule.id)
        if (previous != null && previous != rule.id) {
            diagnostics += ValidationDiagnostic(
                severity = Severity.WARNING,
                message = "Variable '${assignment.name}' is assigned by rule '$previous' and " +
                        "rule '${rule.id}'; the last rule that matches wins",
                line = assignment.line,
                column = assignment.column,
            )
        }
    }

    /** Every name a field can be written as, so a variable cannot shadow one. */
    private fun fieldNames(schema: FieldSchema): Set<String> {
        return buildSet {
            schema.fields.forEach { (fieldId, definition) ->
                add(fieldId.value)
                definition.alias?.let { alias -> add(alias) }
            }
        }
    }
}
