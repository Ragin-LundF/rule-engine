package ruleengine.evaluator.context

import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.domain.dto.field.FieldType
import java.util.Collections
import java.util.IdentityHashMap

/**
 * The input as a schema with `missing: empty` fields describes it: such a field the record does not
 * carry, or carries as `null`, reads as `[]` (collection, string_set) or `{}` (object).
 *
 * Filling the value in where the input is read is what makes every reader agree — aggregates,
 * filters, keyed sums, `every` / `any`, the presence predicates and `extract` all read through here.
 * An object filled in as `{}` still applies its members' own settings, so a member declared
 * `missing: empty` inside an absent object reads as empty too, and a member without it stays missing.
 *
 * The input is never modified: a structure that needs a value filled in is copied. Each root is
 * resolved once per context, and which declarations carry a default somewhere below them is worked
 * out once, when the context is built, rather than again for every element.
 */
internal class MissingAsEmptyRuleContext private constructor(
    private val delegate: RuleContext,
    private val fields: Map<FieldId, FieldDefinition>,
    /** Every declaration that is `missing: empty` itself or has such a member below it, by identity. */
    private val withDefaults: Set<FieldDefinition>,
) : RuleContext {

    private val roots = HashMap<String, Any?>()

    override fun get(field: FieldId): Any? {
        val keys = field.value.split('.')
        var current = root(name = keys.first())
        for (key in keys.drop(n = 1)) {
            current = (current as? Map<*, *>)?.get(key) ?: return null
        }
        return current
    }

    override fun getRaw(fieldPath: List<String>): Any? {
        if (fieldPath.isEmpty()) {
            return delegate.getRaw(fieldPath = fieldPath)
        }
        val name = fieldPath.first()
        val root = root(name = name)
        if (fieldPath.size == 1) {
            return root
        }
        return MapRuleContext(map = mapOf(name to root)).getRaw(fieldPath = fieldPath)
    }

    private fun root(name: String): Any? {
        if (roots.containsKey(key = name)) {
            return roots[name]
        }
        val filled = withEmptyDefaults(
            raw = delegate.getRaw(fieldPath = listOf(name)),
            definition = fields[FieldId(value = name)],
        )
        roots[name] = filled
        return filled
    }

    private fun withEmptyDefaults(raw: Any?, definition: FieldDefinition?): Any? {
        if (definition == null || definition !in withDefaults) {
            return raw
        }
        if (raw == null) {
            return emptyValueOf(definition = definition)
        }
        return when {
            definition.type == FieldType.OBJECT && raw is Map<*, *> -> withMembers(
                element = raw,
                members = definition.fields,
            )

            definition.type == FieldType.COLLECTION && raw is Collection<*> -> raw.map { element ->
                if (element is Map<*, *>) withMembers(element = element, members = definition.fields) else element
            }

            else -> raw
        }
    }

    private fun emptyValueOf(definition: FieldDefinition): Any? {
        if (!definition.missingAsEmpty) {
            return null
        }
        return when (definition.type) {
            FieldType.OBJECT -> withMembers(element = emptyMap<String, Any?>(), members = definition.fields)
            else -> emptyList<Any?>()
        }
    }

    private fun withMembers(element: Map<*, *>, members: Map<FieldId, FieldDefinition>): Map<*, *> {
        var copy: MutableMap<Any?, Any?>? = null
        for ((id, member) in members) {
            val raw = element[id.value]
            val filled = withEmptyDefaults(raw = raw, definition = member)
            if (filled !== raw) {
                copy = copy ?: LinkedHashMap<Any?, Any?>(element)
                copy[id.value] = filled
            }
        }
        return copy ?: element
    }

    companion object {
        /** [context] itself when [schema] declares no `missing: empty` field, so strict schemas pay nothing. */
        fun wrapIfNeeded(context: RuleContext, schema: FieldSchema): RuleContext {
            val withDefaults = Collections.newSetFromMap(IdentityHashMap<FieldDefinition, Boolean>())
            schema.fields.values.forEach { definition -> collectDefaults(definition = definition, into = withDefaults) }
            if (withDefaults.isEmpty()) {
                return context
            }
            return MissingAsEmptyRuleContext(delegate = context, fields = schema.fields, withDefaults = withDefaults)
        }

        /** Adds [definition] to [into] when it, or anything below it, declares `missing: empty`. */
        private fun collectDefaults(definition: FieldDefinition, into: MutableSet<FieldDefinition>): Boolean {
            var below = false
            definition.fields.values.forEach { member ->
                below = collectDefaults(definition = member, into = into) || below
            }
            val declares = definition.missingAsEmpty || below
            if (declares) {
                into += definition
            }
            return declares
        }
    }
}
