package ruleengine.compiler.support

import ruleengine.compiler.operators.OperatorUtils
import ruleengine.core.domain.OperatorNames
import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldType

/**
 * Which operators a field accepts.
 *
 * A field's declared `operators:` list wins when it has one; otherwise the type's defaults apply.
 * This is the engine's answer to what is *legal* — the UI keeps its own, deliberately different,
 * lists of what to *offer*. See `ruleengine-core.md` for that divergence.
 */
internal object OperatorSupport {

    internal fun allowedOperatorsFor(def: FieldDefinition): Set<String> {
        return if (def.operators.isNotEmpty()) {
            def.operators.mapTo(mutableSetOf()) { operator ->
                OperatorUtils.normalizeOperator(op = operator.value)
            }
        } else {
            supportedOperatorsFor(type = def.type)
        }
    }

    /** Numbers and dates are both ordered, so they accept the same comparisons. */
    internal val NUMERIC_OPERATORS: Set<String> = setOf(
        OperatorNames.EQUALS,
        OperatorNames.GT,
        OperatorNames.GTE,
        OperatorNames.LT,
        OperatorNames.LTE,
        OperatorNames.BETWEEN,
    )

    internal fun supportedOperatorsFor(type: FieldType): Set<String> {
        return when (type) {
            FieldType.TEXT -> setOf(
                OperatorNames.EQUALS,
                OperatorNames.CONTAINS,
                OperatorNames.STARTS_WITH,
                OperatorNames.ENDS_WITH,
                OperatorNames.IN,
                OperatorNames.REGEX,
            )

            FieldType.DECIMAL, FieldType.INTEGER -> NUMERIC_OPERATORS
            FieldType.STRING_SET -> setOf(OperatorNames.CONTAINS_ANY, OperatorNames.CONTAINS_ALL)
            FieldType.BOOLEAN -> setOf(OperatorNames.EQUALS)
            FieldType.DATE, FieldType.DATE_TIME -> NUMERIC_OPERATORS
            // COLLECTION and OBJECT are navigated or aggregated, never compared directly.
            else -> emptySet()
        }
    }
}
