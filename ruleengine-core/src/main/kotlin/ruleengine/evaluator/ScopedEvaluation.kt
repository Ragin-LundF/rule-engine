package ruleengine.evaluator

import ruleengine.core.domain.dto.EvaluationResult
import ruleengine.core.domain.dto.MemberEvaluation
import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.normalizer.NormalizerRegistry
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.RuleContext
import ruleengine.evaluator.context.ScopedRuleContext

/**
 * Runs a rule set once per member of a collection instead of once for the whole document.
 *
 * Lives here rather than inside `LoadedRuleEngine` because the desktop simulator assembles the
 * pipeline itself and would otherwise show unscoped results for the very rules a scope was declared
 * for. Both callers go through [evaluate].
 *
 * Rules are compiled once and reused; only the context is rebuilt per member. The member schema and
 * its scalar paths are derived once by [memberSchema], since neither depends on the member.
 */
object ScopedEvaluation {

    /**
     * The schema a scoped rule set is written against: the collection's declared members laid over
     * the document's own fields.
     *
     * The member wins, matching what [ScopedRuleContext] does at evaluation time, so a rule naming
     * `id` gets the member's `id` even when the document declares one too.
     */
    fun memberSchema(schema: FieldSchema, scope: String): FieldSchema? {
        val collection = schema.fields[FieldId(value = scope)] ?: return null
        return FieldSchema(name = schema.name, fields = schema.fields + collection.fields)
    }

    /**
     * Evaluates [engine] once per member of [scope].
     *
     * The returned result keeps [EvaluationResult.matches] flat, in member order and tagged with the
     * member each came from, so a consumer that knows nothing about scoping still sees every match.
     * Per-member `variables` and `stoppedBy` live in [EvaluationResult.members].
     *
     * A scope naming something that is not a collection, or absent from the input, evaluates no
     * members and returns an empty result rather than failing — the rule set simply had nothing to
     * run against. Whether the scope *can* name a collection is settled at load time.
     */
    fun evaluate(
        engine: RuleEngine,
        document: RuleContext,
        schema: FieldSchema,
        memberSchema: FieldSchema,
        scope: String,
        includeTrace: Boolean = false,
        normalizerRegistry: NormalizerRegistry = NormalizerRegistry.default
    ): EvaluationResult {
        val members = document.getRaw(fieldPath = listOf(scope)) as? Collection<*> ?: emptyList<Any?>()
        val evaluations = members.filterIsInstance<Map<*, *>>().mapIndexed { index, member ->
            val prepared = PreparedRuleContext.prepare(
                ctx = ScopedRuleContext(member = member, document = document),
                schema = memberSchema,
                normalizerRegistry = normalizerRegistry
            )
            val key = memberKey(member = member, schema = schema, scope = scope, index = index)
            MemberEvaluation(
                index = index,
                key = key,
                result = tagged(
                    result = engine.evaluate(prepared = prepared, includeTrace = includeTrace),
                    key = key
                )
            )
        }
        return EvaluationResult(
            matches = evaluations.flatMap { evaluation -> evaluation.result.matches },
            trace = evaluations.mapNotNull { evaluation -> evaluation.result.trace }.ifEmpty { null },
            members = evaluations
        )
    }

    /** Stamps every match with the member it came from, so the flattened list stays readable. */
    private fun tagged(result: EvaluationResult, key: String): EvaluationResult {
        return result.copy(matches = result.matches.map { match -> match.copy(scopeMember = key) })
    }

    /**
     * How a member identifies itself in the result.
     *
     * A declared `id` member is used when the input carries one, because that is what a reader
     * recognises. Otherwise the position is the only honest identity, and it is at least stable.
     */
    private fun memberKey(member: Map<*, *>, schema: FieldSchema, scope: String, index: Int): String {
        val declared = schema.fields[FieldId(value = scope)]?.fields.orEmpty()
        val idMember = idMemberName(members = declared)
        val value = idMember?.let { name -> member[name] }
        return value?.toString() ?: "$scope[$index]"
    }

    private fun idMemberName(members: Map<FieldId, FieldDefinition>): String? {
        return members.keys.map { id -> id.value }.firstOrNull { name -> name == "id" }
    }
}
