package ruleengine.compiler

import ruleengine.core.analysis.VariableUsage
import ruleengine.core.domain.dto.action.ActionSchema
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.errors.Severity
import ruleengine.core.errors.ValidationDiagnostic
import ruleengine.dsl.ast.AssignmentKindAst
import java.nio.file.Path

/**
 * Validates a whole manifest entry and says which file each diagnostic belongs to.
 *
 * [Validator] answers "is this list of rules valid", which is what the engine needs: it flattens an
 * entry's files before compiling, so a diagnostic's position is enough. A tool that shows the problem to
 * someone needs more — a line number means nothing without the file it is a line of.
 *
 * So this walks the entry **file by file, in manifest order**, and hands each file the variables the
 * files before it publish. Two properties fall out of that, and both are the reason it is not simply a
 * flattened `Validator.validate`:
 *
 * - every diagnostic's `line` and `column` stay relative to its own file, so a caller can point at it;
 * - a `$name` read in the last file resolves against the `set` and `add` clauses of the earlier ones,
 *   exactly as it does at load time.
 *
 * The schema-level checks run once for the entry rather than once per file, and duplicate rule ids are
 * checked both within a file (by [Validator]) and across them (here, where both files can be named).
 */
object EntryValidator {

    /**
     * @param files the entry's rule files, in manifest order — which is load-bearing, because that is
     *   the order the engine evaluates them in and therefore what "an earlier rule" means.
     */
    fun validate(
        files: List<RuleFileAsts>,
        schema: FieldSchema,
        actions: ActionSchema? = null,
    ): ValidationResult {
        val diagnostics = mutableListOf<ValidationDiagnostic>()

        // Once for the entry: a duplicate alias is one problem with the schema, not one problem per
        // rule file that happens to be validated against it.
        Validator.validateSchemas(schema = schema, actions = actions, diagnostics = diagnostics)

        val published = LinkedHashMap<String, AssignmentKindAst>()
        for (file in files) {
            diagnostics += validateFile(
                file = file,
                schema = schema,
                actions = actions,
                published = published,
            )
            for (rule in file.asts) {
                published += VariableUsage.writeKindsOf(rule = rule)
                    .filterKeys { name -> name !in published }
            }
        }

        diagnostics += duplicateIdsAcrossFiles(files = files)

        return ValidationResult(
            isValid = diagnostics.none { diagnostic -> diagnostic.severity == Severity.ERROR },
            diagnostics = diagnostics,
        )
    }

    /** One file's rule-level diagnostics, each stamped with the file that produced it. */
    private fun validateFile(
        file: RuleFileAsts,
        schema: FieldSchema,
        actions: ActionSchema?,
        published: Map<String, AssignmentKindAst>,
    ): List<ValidationDiagnostic> {
        val fileDiagnostics = mutableListOf<ValidationDiagnostic>()
        Validator.validateRuleList(
            asts = file.asts,
            schema = schema,
            actions = actions,
            inheritedVariables = published,
            diagnostics = fileDiagnostics,
        )
        val path = Path.of(file.path)
        return fileDiagnostics.map { diagnostic -> diagnostic.copy(file = path) }
    }

    /**
     * Rule ids repeated in *different* files.
     *
     * The per-file pass cannot see this, and it is the one check a manifest entry needs that a single
     * file never does. Reported against the later file, naming the earlier one — a reader has to know
     * both to decide which id to change.
     */
    private fun duplicateIdsAcrossFiles(files: List<RuleFileAsts>): List<ValidationDiagnostic> {
        val declaredIn = mutableMapOf<String, String>()
        val diagnostics = mutableListOf<ValidationDiagnostic>()
        for (file in files) {
            for (rule in file.asts) {
                val firstFile = declaredIn.putIfAbsent(rule.id, file.path)
                if (firstFile == null || firstFile == file.path) {
                    continue
                }
                diagnostics += ValidationDiagnostic(
                    severity = Severity.ERROR,
                    message = "Duplicate rule id '${rule.id}': also declared in '$firstFile'",
                    suggestion = "Rule ids must be unique across every rule file of an entry",
                    file = Path.of(file.path),
                    line = rule.line,
                    column = rule.column,
                )
            }
        }
        return diagnostics
    }
}
