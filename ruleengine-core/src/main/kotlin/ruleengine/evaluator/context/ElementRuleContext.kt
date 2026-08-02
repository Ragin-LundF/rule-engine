package ruleengine.evaluator.context

import ruleengine.core.domain.dto.field.FieldId

/**
 * One element of a collection, as seen by a filter predicate.
 *
 * A name the element does not carry is looked up in [fallback], the context the collection itself
 * was read from. Without that, `invoices[customerId in priorityCustomerIds]` could not work at all:
 * `customerId` belongs to the invoice, `priorityCustomerIds` to the document, and both have to
 * resolve from inside one predicate.
 *
 * The element always wins, decided by whether it *declares* the key rather than by whether the value
 * is null — an element that holds an explicit null means the value is absent for that element, not
 * that the document's value should be read instead.
 */
class ElementRuleContext(
    private val element: Map<*, *>,
    private val fallback: RuleContext? = null
) : RuleContext {
    override fun get(field: FieldId): Any? {
        if (element.containsKey(key = field.value)) {
            return element[field.value]
        }
        return fallback?.get(field = field)
    }

    override fun getRaw(fieldPath: List<String>): Any? {
        if (fieldPath.isEmpty()) {
            return element
        }
        if (!element.containsKey(key = fieldPath.first())) {
            return fallback?.getRaw(fieldPath = fieldPath)
        }
        var current: Any? = element
        for (key in fieldPath) {
            current = when (current) {
                is Map<*, *> -> current[key]
                else -> return null
            }
        }
        return current
    }
}
