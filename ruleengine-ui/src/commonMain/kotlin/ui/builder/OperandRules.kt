package ui.builder

import ui.builder.model.BuilderOperand
import ui.builder.model.BuilderPathStep
import ui.builder.model.BuilderTerm
import ui.builder.model.catalog.CatalogFieldInfo
import ui.builder.model.catalog.fieldAtPath
import ui.builder.model.catalog.fieldsAtPath
import ui.builder.model.catalog.scalarPaths
import ui.builder.model.names


/**
 * The rules that decide which operand kinds a comparison row may offer, and which comparison
 * operators go with them.
 *
 * Aggregates and arithmetic are numeric-only in the engine, so they are offered only when the other
 * side of the comparison can be numeric. A side whose type cannot be determined — an undeclared path,
 * or an empty literal — counts as "possibly numeric": the engine's validator has the last word, and
 * hiding the option would block legitimate rules over undeclared collections.
 */
object OperandRules {

    /** The operand kinds a chip can switch between. */
    enum class OperandKind(val badge: String, val label: String) {
        FIELD(badge = "◇", label = "Field"),
        VALUE(badge = "#", label = "Value"),
        AGGREGATE(badge = "Σ", label = "Aggregate"),
        CALCULATION(badge = "±", label = "Calculation"),
    }

    fun kindOf(operand: BuilderOperand): OperandKind = when (operand) {
        is BuilderOperand.FieldRef -> OperandKind.FIELD
        is BuilderOperand.Literal -> OperandKind.VALUE
        is BuilderOperand.Aggregate -> OperandKind.AGGREGATE
        is BuilderOperand.Calc -> OperandKind.CALCULATION
    }

    /**
     * Kinds selectable for one side, given the operand on the [other] side.
     * Computed kinds appear only when a numeric comparison is possible.
     */
    fun availableKinds(other: BuilderOperand, fields: List<CatalogFieldInfo>): List<OperandKind> {
        val base = listOf(OperandKind.FIELD, OperandKind.VALUE)
        return if (canBeNumeric(operand = other, fields = fields)) {
            base + OperandKind.AGGREGATE + OperandKind.CALCULATION
        } else {
            base
        }
    }

    /** True unless the operand is definitely non-numeric. */
    fun canBeNumeric(operand: BuilderOperand, fields: List<CatalogFieldInfo>): Boolean = when (operand) {
        is BuilderOperand.Aggregate, is BuilderOperand.Calc -> true
        is BuilderOperand.Literal ->
            operand.numeric || operand.text.isBlank() || operand.text.trim().toDoubleOrNull() != null
        is BuilderOperand.FieldRef -> {
            val leaf = fields.fieldAtPath(segments = operand.path.names)
            // An undeclared path cannot be ruled out, so it stays eligible.
            leaf == null || OperatorOptions.isNumericType(fieldType = leaf.type) ||
                OperatorOptions.isStructureType(fieldType = leaf.type)
        }
    }

    /** True when the operand is certainly numeric, which drives the operator list. */
    private fun isDefinitelyNumeric(operand: BuilderOperand, fields: List<CatalogFieldInfo>): Boolean =
        when (operand) {
            is BuilderOperand.Aggregate, is BuilderOperand.Calc -> true
            is BuilderOperand.Literal -> operand.numeric || operand.text.trim().toDoubleOrNull() != null
            is BuilderOperand.FieldRef -> fields.fieldAtPath(segments = operand.path.names)
                // An untyped variable counts as numeric so the row still offers ordering
                // comparisons; the engine places no type restriction on one either.
                ?.let {
                    OperatorOptions.isNumericType(fieldType = it.type) ||
                        OperatorOptions.isVariableType(fieldType = it.type)
                } ?: false
        }

    /**
     * Comparison operators for a row. Ordering operators are offered as soon as one side is numeric;
     * a text-only comparison is restricted to equality, matching the engine's validator.
     */
    fun operatorsFor(
        left: BuilderOperand,
        right: BuilderOperand,
        fields: List<CatalogFieldInfo>,
    ): List<String> {
        // A list is only ever tested for membership, so it replaces the symbolic set rather than
        // extending it — every ordering or equality against a whole list evaluates to false.
        if (isList(operand = left, fields = fields) || isList(operand = right, fields = fields)) {
            return OperatorOptions.LIST_VARIABLE
        }
        val numeric = isDefinitelyNumeric(operand = left, fields = fields) ||
            isDefinitelyNumeric(operand = right, fields = fields)
        return OperatorOptions.comparisonOperators(numeric = numeric)
    }

    /** True when the operand resolves to a variable an `add` clause builds. */
    private fun isList(operand: BuilderOperand, fields: List<CatalogFieldInfo>): Boolean {
        val leaf = (operand as? BuilderOperand.FieldRef)
            ?.let { ref -> fields.fieldAtPath(segments = ref.path.names) }
            ?: return false
        return OperatorOptions.isListVariableType(fieldType = leaf.type)
    }

    /**
     * True when `ignoreCase` is meaningful for this row, i.e. both sides are textual.
     *
     * A list membership test is not: `ComparisonCompiledExpression` compares elements by value, and
     * `ignoreCase` after a variable operand does not parse.
     */
    fun supportsIgnoreCase(
        left: BuilderOperand,
        right: BuilderOperand,
        fields: List<CatalogFieldInfo>,
    ): Boolean = !isList(operand = left, fields = fields) &&
        !isList(operand = right, fields = fields) &&
        !isDefinitelyNumeric(operand = left, fields = fields) &&
        !isDefinitelyNumeric(operand = right, fields = fields)

    /**
     * A sensible starting operand when the user switches a side to [kind].
     *
     * Aggregates default to `count` over the first declared collection, because that is the one
     * aggregate that is valid for any element type.
     */
    fun defaultOperand(
        kind: OperandKind,
        fields: List<CatalogFieldInfo>,
        previous: BuilderOperand,
    ): BuilderOperand = when (kind) {
        OperandKind.FIELD -> BuilderOperand.FieldRef(
            path = listOf(BuilderPathStep(name = fields.firstOrNull()?.id ?: ""))
        )

        OperandKind.VALUE -> BuilderOperand.Literal(text = "", numeric = false)

        OperandKind.AGGREGATE -> BuilderOperand.Aggregate(
            function = "count",
            path = listOf(
                BuilderPathStep(
                    name = fields.firstOrNull { OperatorOptions.isStructureType(fieldType = it.type) }?.id
                        ?: fields.firstOrNull()?.id
                        ?: ""
                )
            ),
        )

        OperandKind.CALCULATION -> BuilderOperand.Calc(
            terms = listOf(
                BuilderTerm(operator = "", operand = previous.takeUnless { it is BuilderOperand.Calc }
                    ?: BuilderOperand.Literal(text = "0", numeric = true)),
                BuilderTerm(operator = "*", operand = BuilderOperand.Literal(text = "1", numeric = true)),
            ),
        )
    }

    /**
     * Fields offered for the segment at [depth] of a path, i.e. the members of the structure the
     * preceding segments point at. The first segment lists the schema's top-level fields.
     */
    fun segmentOptions(
        fields: List<CatalogFieldInfo>,
        path: List<BuilderPathStep>,
        depth: Int,
    ): List<CatalogFieldInfo> =
        if (depth == 0) fields else fields.fieldsAtPath(segments = path.take(n = depth).names)

    /** True when a further segment can be appended, i.e. the current leaf is a declared structure. */
    fun canAppendSegment(fields: List<CatalogFieldInfo>, path: List<BuilderPathStep>): Boolean {
        val leaf = fields.fieldAtPath(segments = path.names) ?: return false
        return OperatorOptions.isStructureType(fieldType = leaf.type) && leaf.nestedFields.isNotEmpty()
    }

    /**
     * Members available to a filter on the segment at [depth].
     *
     * A member reachable through a nested object is offered by its dotted path, because the engine
     * resolves the filter against the element and walks that path — `parcels[origin.hub == "HAM"]`.
     * Collection members are left out: projecting one yields many values, which a single
     * `field op value` row has nothing to compare against.
     */
    fun filterFieldOptions(
        fields: List<CatalogFieldInfo>,
        path: List<BuilderPathStep>,
        depth: Int,
    ): List<CatalogFieldInfo> = fields.fieldAtPath(segments = path.take(n = depth + 1).names)
        ?.nestedFields?.scalarPaths()
        ?: emptyList()
}
