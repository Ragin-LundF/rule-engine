package ruleengine.compiler.support

import ruleengine.compiler.operators.DateOperator
import ruleengine.compiler.value.ValueExpressionValidator
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.errors.Severity
import ruleengine.core.errors.ValidationDiagnostic
import ruleengine.dsl.ast.condition.ComparisonExpressionAst
import ruleengine.dsl.ast.literal.StringLiteral
import ruleengine.dsl.ast.value.FieldAccessAst
import ruleengine.dsl.ast.value.LiteralValueAst
import ruleengine.dsl.ast.value.ValueExpressionAst

/**
 * `dueDate == "31.01.2024"` reads its literal under the field's declaration, like `dueDate equals …`,
 * so a literal that does not parse is rejected the same way instead of never matching at run time.
 *
 * Only for a rule's own conditions: inside a filter predicate the element is read raw and its literal
 * stays text (see `Compiler.compileFilterExpression`), so the validator never calls this there.
 */
internal object ComparisonDateLiteralValidation {

    fun validate(expr: ComparisonExpressionAst, schema: FieldSchema, diagnostics: MutableList<ValidationDiagnostic>) {
        validateLiteral(literal = expr.right, other = expr.left, schema = schema, diagnostics = diagnostics)
        validateLiteral(literal = expr.left, other = expr.right, schema = schema, diagnostics = diagnostics)
    }

    private fun validateLiteral(
        literal: ValueExpressionAst,
        other: ValueExpressionAst,
        schema: FieldSchema,
        diagnostics: MutableList<ValidationDiagnostic>
    ) {
        val text = ((literal as? LiteralValueAst)?.literal as? StringLiteral)?.value ?: return
        val def = DateOperator.declaredTemporalField(expr = other, schema = schema) ?: return
        if (DateOperator.isValidLiteral(text = text, def = def)) {
            return
        }
        val field = ValueExpressionValidator.pathText(path = (other as FieldAccessAst).path)
        diagnostics += ValidationDiagnostic(
            severity = Severity.ERROR,
            message = "Invalid date '$text' for field '$field'; expected ${DateOperator.expectedFormatText(def = def)}"
        )
    }
}
