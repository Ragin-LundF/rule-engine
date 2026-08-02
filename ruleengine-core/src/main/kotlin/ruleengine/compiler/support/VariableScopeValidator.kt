package ruleengine.compiler.support

import ruleengine.core.analysis.VariableUsage
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.errors.Severity
import ruleengine.core.errors.ValidationDiagnostic
import ruleengine.dsl.ast.ActionAst
import ruleengine.dsl.ast.AssignmentKindAst
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
 *
 * An `add` clause is scoped one step wider: it publishes its name **before its own rule's condition
 * is checked**, so the rule that first accumulates into a list may also guard on it. The asymmetry
 * with `set` is deliberate, and rests on accumulators having an identity element. An unset list reads
 * as missing, `missing contains x` is false, and `not` of that is true — so the guard on the very
 * first rule passes, which is the right answer. `set total = $total + amount` has no such element:
 * missing plus a number is missing, and the rule would silently publish nothing. That is why reading
 * a `set` variable before it is assigned stays an error.
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
        val writtenKinds = mutableMapOf<String, AssignmentKindAst>()

        for (rule in asts) {
            declareAccumulators(rule = rule, defined = defined)

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
                writtenKinds = writtenKinds,
                diagnostics = diagnostics
            )
            checkBranch(
                assignments = rule.elseAssignments,
                actions = rule.elseActions,
                rule = rule,
                fieldNames = fieldNames,
                defined = defined,
                assignedBy = assignedBy,
                writtenKinds = writtenKinds,
                diagnostics = diagnostics
            )
        }
    }

    /**
     * Publishes the names this rule's `add` clauses write, before its condition is read.
     *
     * This is the one place the "an earlier rule must assign it" rule is relaxed, and only for
     * accumulators — see the class KDoc for why they can afford it and `set` cannot. Names written by
     * a *later* rule are still not in scope, so a forward reference is still an error.
     */
    private fun declareAccumulators(rule: RuleAst, defined: MutableSet<String>) {
        for (assignment in rule.assignments + rule.elseAssignments) {
            if (assignment.kind == AssignmentKindAst.ADD) {
                defined += assignment.name
            }
        }
    }

    /** The `set` and `add` clauses and actions of one branch, in the order the engine applies them. */
    @Suppress("LongParameterList")
    private fun checkBranch(
        assignments: List<VariableAssignmentAst>,
        actions: List<ActionAst>,
        rule: RuleAst,
        fieldNames: Set<String>,
        defined: MutableSet<String>,
        assignedBy: MutableMap<String, String>,
        writtenKinds: MutableMap<String, AssignmentKindAst>,
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
                writtenKinds = writtenKinds,
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
                        "no earlier rule assigns it with a 'set' or 'add' clause",
                suggestion = Suggestions.suggestClosest(input = name, candidates = defined.toList())
                    ?.let { closest -> "Did you mean '\$$closest'?" },
                line = rule.line,
                column = rule.column,
            )
        }
    }

    @Suppress("LongParameterList")
    private fun checkAssignment(
        assignment: VariableAssignmentAst,
        rule: RuleAst,
        fieldNames: Set<String>,
        assignedBy: MutableMap<String, String>,
        writtenKinds: MutableMap<String, AssignmentKindAst>,
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

        checkKind(assignment = assignment, rule = rule, writtenKinds = writtenKinds, diagnostics = diagnostics)

        // Only for `set`. Several rules accumulating into one list is the point of `add`, not a
        // mistake, and "the last rule that matches wins" would be the wrong thing to say about it.
        if (assignment.kind != AssignmentKindAst.SET) {
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

    /**
     * A name is either a plain value or an accumulator, never both.
     *
     * Checked statically because the runtime cannot tell the two apart: an accumulator is an ordinary
     * list value, so a `set` that produced a list and an `add` that built one are indistinguishable
     * once evaluation starts. Whichever rule matched first would decide what the name means.
     */
    private fun checkKind(
        assignment: VariableAssignmentAst,
        rule: RuleAst,
        writtenKinds: MutableMap<String, AssignmentKindAst>,
        diagnostics: MutableList<ValidationDiagnostic>
    ) {
        val previousKind = writtenKinds.putIfAbsent(assignment.name, assignment.kind) ?: return
        if (previousKind == assignment.kind) {
            return
        }
        diagnostics += ValidationDiagnostic(
            severity = Severity.ERROR,
            message = "Variable '${assignment.name}' is written by both a 'set' and an 'add' clause, " +
                    "the latter in rule '${rule.id}'; a variable is either a plain value or a list, not both",
            suggestion = "Use 'add' everywhere to accumulate a list, or 'set' everywhere to publish " +
                    "a single value",
            line = assignment.line,
            column = assignment.column,
        )
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
