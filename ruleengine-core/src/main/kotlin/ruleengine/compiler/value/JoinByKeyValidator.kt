package ruleengine.compiler.value

import ruleengine.core.domain.FieldPathResolver
import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.core.errors.Severity
import ruleengine.core.errors.ValidationDiagnostic
import ruleengine.dsl.ast.path.JoinSegmentAst
import ruleengine.evaluator.compiled.DslFunctions

/**
 * Load-time checks for `joinByKey("key", alias: collection.member, ...)`.
 *
 * The sources are checked exactly as `sumByKey` checks its own — a declared collection, a numeric
 * value member, the key declared with one type everywhere — by the same function. On top of that
 * come the aliases, which name the row's members and therefore must not collide with each other,
 * with the key, or with a schema field a filter on the rows would otherwise lose sight of.
 */
internal object JoinByKeyValidator {

    private val NAME: String = DslFunctions.JOIN_NAMES.single()

    /** A join of one collection is just that collection. */
    private const val MIN_SOURCES = 2

    /**
     * The declared shape of the rows, for the rest of the path to continue from, or null when the
     * join itself is invalid — what follows it would only repeat the same mistake.
     */
    fun validate(
        segment: JoinSegmentAst,
        schema: FieldSchema,
        diagnostics: MutableList<ValidationDiagnostic>
    ): FieldDefinition? {
        val errorsBefore = errorCount(diagnostics = diagnostics)
        if (segment.sources.size < MIN_SOURCES) {
            diagnostics += error(
                message = "$NAME() expects at least two sources, but got ${segment.sources.size}"
            )
        }
        val key = segment.key
        if (key == null) {
            diagnostics += error(
                message = "$NAME() expects the key member name as its first argument, such as " +
                        "$NAME(\"month\", income: income.amount, spending: spending.amount)"
            )
            return null
        }
        validateAliases(segment = segment, key = key, schema = schema, diagnostics = diagnostics)
        var keyType: FieldType? = null
        for (source in segment.sources) {
            keyType = FunctionCallValidator.validateKeyedSumSource(
                functionName = NAME,
                argument = source.path,
                key = key,
                keyType = keyType,
                schema = schema,
                diagnostics = diagnostics
            )
        }
        if (errorCount(diagnostics = diagnostics) > errorsBefore) {
            return null
        }
        return JoinedRowSchema.of(segment = segment, schema = schema)
    }

    private fun validateAliases(
        segment: JoinSegmentAst,
        key: String,
        schema: FieldSchema,
        diagnostics: MutableList<ValidationDiagnostic>
    ) {
        val seen = mutableSetOf<String>()
        for (alias in segment.sources.map { source -> source.alias }) {
            val message = when {
                !seen.add(alias) -> "$NAME() names the alias '$alias' twice; each source needs its own"
                alias == key -> "$NAME() alias '$alias' is the key's name, which every row already holds"
                isSchemaField(name = alias, schema = schema) ->
                    "$NAME() alias '$alias' is also a field of the schema, which a filter on the rows " +
                            "could then no longer read; choose another alias"

                else -> null
            }
            message?.let { text -> diagnostics += error(message = text) }
        }
    }

    private fun isSchemaField(name: String, schema: FieldSchema): Boolean {
        return FieldId(value = name) in schema.fields ||
            FieldPathResolver.expandRoot(name = name, schema = schema) != listOf(name)
    }

    private fun errorCount(diagnostics: List<ValidationDiagnostic>): Int {
        return diagnostics.count { diagnostic -> diagnostic.severity == Severity.ERROR }
    }

    private fun error(message: String): ValidationDiagnostic {
        return ValidationDiagnostic(severity = Severity.ERROR, message = message)
    }
}
