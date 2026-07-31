package ruleengine.builder

import ruleengine.core.domain.dto.ActionSchema
import ruleengine.core.domain.dto.EvaluationResult
import ruleengine.core.domain.dto.FieldSchema
import ruleengine.core.errors.ValidationDiagnostic
import ruleengine.core.normalizer.NormalizerRegistry
import ruleengine.evaluator.RuleEngine
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.RuleContext

/**
 * A fully prepared rule engine for a single manifest entry, together with everything needed to
 * evaluate input against it.
 *
 * Immutable and thread-safe; keep one instance per manifest entry for the lifetime of the
 * application and reuse it for every evaluation.
 *
 * @property entryId id of the manifest entry this engine was built from
 * @property engine the compiled engine
 * @property schema field schema the rules were compiled against (needed for normalisation)
 * @property actions action schema, or `null` when the manifest entry declares none
 * @property warnings non-fatal validation diagnostics; errors would have failed the build
 */
data class LoadedRuleEngine(
    val entryId: String,
    val engine: RuleEngine,
    val schema: FieldSchema,
    val actions: ActionSchema? = null,
    val warnings: List<ValidationDiagnostic> = emptyList(),
) {
    /**
     * Normalises [input] against [schema] and evaluates it.
     *
     * Convenience wrapper around [RuleContext.of], [PreparedRuleContext.prepare] and
     * [RuleEngine.evaluate]. Use [engine] directly when a [PreparedRuleContext] is reused across
     * several evaluations.
     */
    fun evaluate(
        input: Map<String, Any?>,
        includeTrace: Boolean = false,
        normalizerRegistry: NormalizerRegistry = NormalizerRegistry.default,
    ): EvaluationResult {
        val ruleContext = RuleContext.of(entries = input.entries.map { it.key to it.value }.toTypedArray())
        val prepared = PreparedRuleContext.prepare(
            ctx = ruleContext,
            schema = schema,
            normalizerRegistry = normalizerRegistry,
        )

        return engine.evaluate(prepared = prepared, includeTrace = includeTrace)
    }
}
