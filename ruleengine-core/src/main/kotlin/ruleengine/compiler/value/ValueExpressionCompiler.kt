package ruleengine.compiler.value

import ruleengine.core.domain.FieldPathResolver
import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.errors.CompilationException
import ruleengine.dsl.ast.ArithmeticValueAst
import ruleengine.dsl.ast.BooleanLiteral
import ruleengine.dsl.ast.ExpressionAst
import ruleengine.dsl.ast.FieldAccessAst
import ruleengine.dsl.ast.FieldSegmentAst
import ruleengine.dsl.ast.FilterSegmentAst
import ruleengine.dsl.ast.FunctionCallValueAst
import ruleengine.dsl.ast.LiteralValueAst
import ruleengine.dsl.ast.NumberLiteral
import ruleengine.dsl.ast.PathSegmentAst
import ruleengine.dsl.ast.SliceSegmentAst
import ruleengine.dsl.ast.StringLiteral
import ruleengine.dsl.ast.ValueExpressionAst
import ruleengine.dsl.ast.VariableRefAst
import ruleengine.evaluator.compiled.AggregateFunctionName
import ruleengine.evaluator.compiled.CollectionFunctionName
import ruleengine.evaluator.compiled.CompiledExpression
import ruleengine.evaluator.compiled.value.ArithmeticCompiledValueExpression
import ruleengine.evaluator.compiled.value.CollectionPredicateCompiledValueExpression
import ruleengine.evaluator.compiled.value.CompiledValueExpression
import ruleengine.evaluator.compiled.value.FieldAccessCompiledValueExpression
import ruleengine.evaluator.compiled.value.FunctionCallCompiledValueExpression
import ruleengine.evaluator.compiled.value.KeyedSumCompiledValueExpression
import ruleengine.evaluator.compiled.value.LiteralCompiledValueExpression
import ruleengine.evaluator.compiled.value.VariableRefCompiledValueExpression
import ruleengine.evaluator.compiled.value.path.CompiledFieldSegment
import ruleengine.evaluator.compiled.value.path.CompiledFilterSegment
import ruleengine.evaluator.compiled.value.path.CompiledPathSegment
import ruleengine.evaluator.compiled.value.path.CompiledSliceSegment
import ruleengine.evaluator.compiled.value.result.BooleanExpressionValue
import ruleengine.evaluator.compiled.value.result.NumberExpressionValue
import ruleengine.evaluator.compiled.value.result.TextExpressionValue
import java.math.BigDecimal

internal object ValueExpressionCompiler {
    fun compile(
        expr: ValueExpressionAst,
        schema: FieldSchema,
        ruleId: String? = null,
        filterCompiler: ((ExpressionAst, FieldSchema) -> CompiledExpression)? = null
    ): CompiledValueExpression {
        return when (expr) {
            is LiteralValueAst -> compileLiteral(literal = expr, ruleId = ruleId)
            is FieldAccessAst -> compileFieldAccess(
                expr = expr,
                schema = schema,
                ruleId = ruleId,
                filterCompiler = filterCompiler
            )

            is ArithmeticValueAst -> compileArithmetic(
                expr = expr,
                schema = schema,
                ruleId = ruleId,
                filterCompiler = filterCompiler
            )

            is FunctionCallValueAst -> compileFunctionCall(
                expr = expr,
                schema = schema,
                ruleId = ruleId,
                filterCompiler = filterCompiler
            )

            is VariableRefAst -> VariableRefCompiledValueExpression(name = expr.name)
        }
    }

    private fun compileLiteral(literal: LiteralValueAst, ruleId: String?): CompiledValueExpression {
        val value = when (val lit = literal.literal) {
            is NumberLiteral -> NumberExpressionValue(value = BigDecimal(lit.value))
            is StringLiteral -> TextExpressionValue(value = lit.value)
            is BooleanLiteral -> BooleanExpressionValue(value = lit.value)
            else -> throw CompilationException(
                ruleId = ruleId,
                details = "Unsupported literal type: ${literal.literal::class.simpleName}"
            )
        }
        return LiteralCompiledValueExpression(value = value)
    }

    private fun compileFieldAccess(
        expr: FieldAccessAst,
        schema: FieldSchema,
        ruleId: String?,
        filterCompiler: ((ExpressionAst, FieldSchema) -> CompiledExpression)?
    ): CompiledValueExpression {
        val compiledSegments = mutableListOf<CompiledPathSegment>()
        // Walked alongside the segments so the path knows two things the compiled form cannot
        // rediscover: which schema a filter's members belong to, and which normalizers the field the
        // path ends at declares. `FieldPathResolver.resolve` cannot supply either — it stops at a
        // collection by design, and a collection is the case that needs both.
        var definition: FieldDefinition? = null
        var fields = schema.fields
        for ((index, segment) in expr.path.withIndex()) {
            when (segment) {
                is FieldSegmentAst -> {
                    val names = if (index == 0) {
                        FieldPathResolver.expandRoot(name = segment.name, schema = schema)
                    } else {
                        listOf(FieldPathResolver.resolveName(identifier = segment.name, fields = fields))
                    }
                    for (name in names) {
                        compiledSegments += CompiledFieldSegment(name = name)
                        definition = fields[FieldId(value = name)]
                        // An undeclared member ends the walk. Continuing against the outer schema would
                        // let a top-level field of the same name lend its normalizers to a member that
                        // declares none.
                        fields = definition?.fields.orEmpty()
                    }
                }

                is FilterSegmentAst -> {
                    val compiler = filterCompiler ?: throw CompilationException(
                        ruleId = ruleId,
                        details = "Filter segments in field paths are not supported in this context"
                    )
                    val scope = elementSchema(fields = fields, schema = schema)
                    compiledSegments += CompiledFilterSegment(expression = compiler(segment.expression, scope))
                }

                is SliceSegmentAst -> {
                    // Validation reports a bad count as a diagnostic; reaching the compiler with one
                    // means the rule was compiled without being validated first.
                    val count = segment.count.toIntOrNull()?.takeIf { value -> value >= 0 }
                        ?: throw CompilationException(
                            ruleId = ruleId,
                            details = "Slice size must be a non-negative whole number, but was '${segment.count}'"
                        )
                    compiledSegments += CompiledSliceSegment(fromEnd = segment.fromEnd, count = count)
                }
            }
        }
        return FieldAccessCompiledValueExpression(
            segments = compiledSegments,
            normalizers = definition?.normalizers ?: emptyList()
        )
    }

    /**
     * The schema a filter's members resolve against: the members of the collection being filtered,
     * laid over the document's own fields.
     *
     * The element wins, so `invoices[customerId == "x"]` reads the invoice's `customerId` even if the
     * document declares one too. The document half is what lets a predicate name a document-level
     * field — `invoices[customerId in priorityCustomerIds]` — and pick up its declared normalizers;
     * `ElementRuleContext` performs the matching fallback at evaluation time.
     */
    private fun elementSchema(fields: Map<FieldId, FieldDefinition>, schema: FieldSchema): FieldSchema {
        if (fields.isEmpty()) {
            return schema
        }
        return FieldSchema(name = schema.name, fields = schema.fields + fields)
    }

    private fun compileFunctionCall(
        expr: FunctionCallValueAst,
        schema: FieldSchema,
        ruleId: String?,
        filterCompiler: ((ExpressionAst, FieldSchema) -> CompiledExpression)?
    ): CompiledValueExpression {
        when (CollectionFunctionName.fromName(name = expr.name)) {
            CollectionFunctionName.EVERY -> return compileCollectionPredicate(
                function = CollectionFunctionName.EVERY,
                expr = expr,
                schema = schema,
                ruleId = ruleId,
                filterCompiler = filterCompiler
            )

            CollectionFunctionName.ANY -> return compileCollectionPredicate(
                function = CollectionFunctionName.ANY,
                expr = expr,
                schema = schema,
                ruleId = ruleId,
                filterCompiler = filterCompiler
            )

            CollectionFunctionName.SUM_BY_KEY -> return compileKeyedSum(
                expr = expr,
                schema = schema,
                ruleId = ruleId,
                filterCompiler = filterCompiler
            )

            null -> Unit
        }
        val functionName = AggregateFunctionName.fromName(name = expr.name) ?: throw CompilationException(
            ruleId = ruleId,
            details = "Unknown function '${expr.name}'"
        )
        if (expr.arguments.size !in functionName.arity) {
            throw CompilationException(
                ruleId = ruleId,
                details = "Function '${expr.name}' requires ${arityText(arity = functionName.arity)}"
            )
        }
        val compiledArgs = expr.arguments.map { argument ->
            compile(expr = argument, schema = schema, ruleId = ruleId, filterCompiler = filterCompiler)
        }
        return FunctionCallCompiledValueExpression(function = functionName, arguments = compiledArgs)
    }

    /**
     * Splits `every(orders[paid == true])` into the collection and the predicate to run over it.
     *
     * The trailing filter *is* the predicate — reusing the filter syntax is what makes `every` work
     * unchanged over a sliced or already-filtered collection, since anything before the last filter
     * simply stays part of the source.
     */
    private fun compileCollectionPredicate(
        function: CollectionFunctionName,
        expr: FunctionCallValueAst,
        schema: FieldSchema,
        ruleId: String?,
        filterCompiler: ((ExpressionAst, FieldSchema) -> CompiledExpression)?
    ): CompiledValueExpression {
        val argument = expr.arguments.singleOrNull() as? FieldAccessAst
        val filter = argument?.path?.lastOrNull() as? FilterSegmentAst
        if (argument == null || filter == null) {
            throw CompilationException(
                ruleId = ruleId,
                details = "${expr.name}() expects a collection with a condition, " +
                        "such as ${expr.name}(orders[total > 0])"
            )
        }
        val compiler = filterCompiler ?: throw CompilationException(
            ruleId = ruleId,
            details = "${expr.name}() is not supported in this context"
        )
        val sourcePath = FieldAccessAst(path = argument.path.dropLast(n = 1))
        val source = compileFieldAccess(
            expr = sourcePath,
            schema = schema,
            ruleId = ruleId,
            filterCompiler = filterCompiler
        ) as FieldAccessCompiledValueExpression
        val scope = elementSchema(fields = memberFields(path = sourcePath.path, schema = schema), schema = schema)
        return CollectionPredicateCompiledValueExpression(
            source = source,
            predicate = compiler(filter.expression, scope),
            requireAll = function == CollectionFunctionName.EVERY
        )
    }

    /**
     * Splits `sumByKey("month", sales.amount, refunds.amount)` into the key and one source per
     * collection.
     *
     * Each source is cut at its last field segment: everything before it selects the collection,
     * and that last segment names the member holding the number. The two have to stay apart because
     * the compiled node reads the key and the value off the same element, which a compiled
     * projection can no longer do.
     */
    private fun compileKeyedSum(
        expr: FunctionCallValueAst,
        schema: FieldSchema,
        ruleId: String?,
        filterCompiler: ((ExpressionAst, FieldSchema) -> CompiledExpression)?
    ): CompiledValueExpression {
        val key = ((expr.arguments.firstOrNull() as? LiteralValueAst)?.literal as? StringLiteral)?.value
            ?: throw CompilationException(
                ruleId = ruleId,
                details = "sumByKey() expects the key member name as its first argument, such as " +
                        "sumByKey(\"month\", sales.amount, refunds.amount)"
            )
        val sources = expr.arguments.drop(n = 1).map { argument ->
            val path = (argument as? FieldAccessAst)?.path
            val valueMember = (path?.lastOrNull() as? FieldSegmentAst)?.name
            if (path == null || valueMember == null || path.size < 2) {
                throw CompilationException(
                    ruleId = ruleId,
                    details = "sumByKey() expects each source to name a collection and a numeric " +
                            "member, such as sales.amount"
                )
            }
            val collection = compileFieldAccess(
                expr = FieldAccessAst(path = path.dropLast(n = 1)),
                schema = schema,
                ruleId = ruleId,
                filterCompiler = filterCompiler
            ) as FieldAccessCompiledValueExpression
            KeyedSumCompiledValueExpression.Source(collection = collection, valueMember = valueMember)
        }
        return KeyedSumCompiledValueExpression(key = key, sources = sources)
    }

    /** The declared members of the field a path ends at, or none once the path leaves the schema. */
    private fun memberFields(
        path: List<PathSegmentAst>,
        schema: FieldSchema
    ): Map<FieldId, FieldDefinition> {
        var fields = schema.fields
        for (segment in path) {
            if (segment !is FieldSegmentAst) {
                continue
            }
            val name = FieldPathResolver.resolveName(identifier = segment.name, fields = fields)
            fields = fields[FieldId(value = name)]?.fields.orEmpty()
        }
        return fields
    }

    private fun arityText(arity: IntRange): String {
        if (arity.first == arity.last) {
            return if (arity.first == 1) "exactly one argument" else "exactly ${arity.first} arguments"
        }
        return "between ${arity.first} and ${arity.last} arguments"
    }

    private fun compileArithmetic(
        expr: ArithmeticValueAst,
        schema: FieldSchema,
        ruleId: String?,
        filterCompiler: ((ExpressionAst, FieldSchema) -> CompiledExpression)?
    ): CompiledValueExpression {
        val left = compile(expr = expr.left, schema = schema, ruleId = ruleId, filterCompiler = filterCompiler)
        val right = compile(expr = expr.right, schema = schema, ruleId = ruleId, filterCompiler = filterCompiler)
        val cost = maxOf(left.cost, right.cost)
        return ArithmeticCompiledValueExpression(
            left = left,
            operator = expr.operator,
            right = right,
            cost = cost
        )
    }

}
