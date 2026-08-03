package ruleengine.core.domain.dto.field

data class FieldSchema(
    val name: String,
    val fields: Map<FieldId, FieldDefinition>
) {
    /** Every aliased field at any depth, in declaration order. */
    val aliasTargets: List<AliasTarget> by lazy {
        collectAliasTargets(prefix = null, collectionPath = null, fields = fields)
    }

    /**
     * Bare alias → its target. The first declaration wins, so the index stays deterministic when two
     * fields share an alias; `Validator` reports that collision separately.
     */
    val aliasPaths: Map<String, AliasTarget> by lazy {
        buildMap { for (target in aliasTargets) putIfAbsent(target.alias, target) }
    }
}

private fun collectAliasTargets(
    prefix: String?,
    collectionPath: String?,
    fields: Map<FieldId, FieldDefinition>,
): List<AliasTarget> = buildList {
    for ((fieldId, definition) in fields) {
        val path = if (prefix == null) fieldId.value else "$prefix.${fieldId.value}"
        definition.alias?.takeIf { it.isNotBlank() }?.let { alias ->
            add(
                AliasTarget(
                    alias = alias,
                    path = FieldId(value = path),
                    definition = definition,
                    collectionPath = collectionPath,
                )
            )
        }
        if (definition.fields.isNotEmpty()) {
            addAll(
                collectAliasTargets(
                    prefix = path,
                    // The nearest collection between the root and a member is the one a plain condition
                    // would be stopped by, so an outer collection keeps precedence over an inner one.
                    collectionPath = collectionPath ?: path.takeIf { definition.type == FieldType.COLLECTION },
                    fields = definition.fields,
                )
            )
        }
    }
}
