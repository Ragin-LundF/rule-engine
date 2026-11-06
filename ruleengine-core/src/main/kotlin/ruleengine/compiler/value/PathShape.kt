package ruleengine.compiler.value

import ruleengine.core.domain.FieldPathResolver
import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.domain.dto.field.isMultiValued
import ruleengine.dsl.ast.path.FieldSegmentAst
import ruleengine.dsl.ast.path.OrEmptySegmentAst
import ruleengine.dsl.ast.path.PathSegmentAst
import ruleengine.dsl.ast.value.FieldAccessAst
import ruleengine.dsl.ast.value.ValueExpressionAst

/**
 * Whether a path reads one value or many, by its declared shape.
 *
 * The value kind cannot answer this: `orders.total` types as the member's kind, yet projects one value
 * per order. A path is collection-valued once it passes through a `collection`, ends at one or at a
 * `string_set`, or carries any segment that is not a plain name (filter, slice, sort, join, orEmpty).
 * A path that leaves the schema answers neither way, so every check built on this stays permissive.
 */
internal object PathShape {

    /** True when [path] is collection-valued by its declared shape. */
    fun isCollectionValued(path: List<PathSegmentAst>, schema: FieldSchema): Boolean {
        return walk(path = path, schema = schema).multiValued
    }

    /** The declared field a single-valued [path] ends at, or null when it is collection-valued or undeclared. */
    fun singleValuedEnd(path: List<PathSegmentAst>, schema: FieldSchema): FieldDefinition? {
        val shape = walk(path = path, schema = schema)
        return shape.end.takeUnless { shape.multiValued }
    }

    /**
     * A `sumByKey` / `joinByKey` source as `<collection>.<member>`, with a trailing `orEmpty` taken
     * off: `orEmpty(src.member)` reads exactly like `optional src.member`. Null when [argument] is not
     * a path.
     */
    fun keyedSourcePath(argument: ValueExpressionAst): List<PathSegmentAst>? {
        val path = (argument as? FieldAccessAst)?.path ?: return null
        return if (path.lastOrNull() is OrEmptySegmentAst) path.dropLast(n = 1) else path
    }

    /** Whether a keyed source reads a missing collection as empty: `optional` or a wrapping `orEmpty`. */
    fun isOptionalSource(argument: ValueExpressionAst): Boolean {
        val access = argument as? FieldAccessAst ?: return false
        return access.optional || access.path.lastOrNull() is OrEmptySegmentAst
    }

    private data class Shape(val multiValued: Boolean, val end: FieldDefinition?)

    private fun walk(path: List<PathSegmentAst>, schema: FieldSchema): Shape {
        var fields = schema.fields
        var end: FieldDefinition? = null
        for ((index, segment) in path.withIndex()) {
            if (segment !is FieldSegmentAst) {
                return Shape(multiValued = true, end = null)
            }
            val names = if (index == 0) {
                FieldPathResolver.expandRoot(name = segment.name, schema = schema)
            } else {
                listOf(FieldPathResolver.resolveName(identifier = segment.name, fields = fields))
            }
            for (name in names) {
                val definition = fields[FieldId(value = name)] ?: return Shape(multiValued = false, end = null)
                if (definition.type.isMultiValued) {
                    return Shape(multiValued = true, end = null)
                }
                end = definition
                fields = definition.fields
            }
        }
        return Shape(multiValued = false, end = end)
    }
}
