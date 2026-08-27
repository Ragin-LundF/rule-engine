package ruleengine.compiler.support

import ruleengine.compiler.operators.OperatorUtils
import ruleengine.core.domain.OperatorNames
import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldType

/**
 * Which operators a field accepts.
 *
 * A field's declared `operators:` list **narrows** the type's defaults; it never widens them. Before
 * that it replaced them, which let a schema name an operator the compiler has no branch for — a
 * `boolean` declaring `gt`, or a `text` declaring `!=` — and the failure then arrived as a
 * `CompilationException` at load time instead of a diagnostic the author could act on.
 *
 * This is the engine's answer to what is *legal* — the UI keeps its own, deliberately different,
 * lists of what to *offer*. See `ruleengine-core.md` for that divergence.
 */
internal object OperatorSupport {

    internal fun allowedOperatorsFor(def: FieldDefinition): Set<String> {
        if (def.operators.isEmpty()) {
            return supportedOperatorsFor(type = def.type)
        }
        return declaredOperatorsFor(def = def) intersect supportedOperatorsFor(type = def.type)
    }

    /** The field's declared list, reduced to canonical spellings. */
    internal fun declaredOperatorsFor(def: FieldDefinition): Set<String> {
        return def.operators.mapTo(mutableSetOf()) { operator ->
            OperatorUtils.normalizeOperator(op = operator.value)
        }
    }

    /**
     * Declared operators the field's type cannot compile, i.e. what [allowedOperatorsFor] drops.
     *
     * `!=` is excluded deliberately: no field type lists it because the parser routes a symbolic
     * inequality through the expression engine rather than the named-operator path, so declaring it is
     * legitimate even though it never appears in a type's set. See `OperatorUtils.isKnownOperator`.
     */
    internal fun unsupportedOperatorsFor(def: FieldDefinition): Set<String> {
        if (def.operators.isEmpty()) {
            return emptySet()
        }
        return declaredOperatorsFor(def = def) -
            supportedOperatorsFor(type = def.type) -
            OperatorNames.SYMBOL_NOT_EQUALS
    }

    /**
     * Numbers and dates are both ordered, so they accept the same comparisons — plus `in`, which asks
     * about membership of a written-out set rather than about order and applies to any scalar.
     */
    internal val NUMERIC_OPERATORS: Set<String> = setOf(
        OperatorNames.EQUALS,
        OperatorNames.GT,
        OperatorNames.GTE,
        OperatorNames.LT,
        OperatorNames.LTE,
        OperatorNames.BETWEEN,
        OperatorNames.IN,
    )

    /**
     * The `when` names both structure types rather than falling through an `else`, so adding a
     * [FieldType] is a compile error here instead of a field that silently accepts no operator at all.
     */
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
            FieldType.COLLECTION, FieldType.OBJECT -> emptySet()
        }
    }
}
