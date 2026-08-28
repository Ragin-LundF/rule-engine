package ui.builder.model.catalog

/**
 * The catalog a path resolves against: the declared fields, plus the **bare-alias index**.
 *
 * The index is the half a nested [CatalogFieldInfo] tree cannot express. An alias declared on a field
 * at any depth is a legal identifier on its own — `TRANSACTION_HISTORY_DAYS` stands for
 * `reports.income.daysOfReport` — and the engine resolves it through
 * `ruleengine.core.domain.dto.field.FieldSchema.aliasPaths`. Walking [CatalogFieldInfo.nestedFields]
 * one segment at a time can only ever match an alias *at its own level*, so without this index the
 * Builder rejects paths the engine accepts, and the condition dropdown ends up offering an alias its
 * own path walk then reports as undeclared.
 *
 * The index is **not** derived here. `ui.workbench.builderCatalogFieldsFrom` takes it from the engine's
 * own `aliasPaths`, already filtered by the two rules that decide whether a bare alias is usable at
 * all — see there. That keeps one source of truth for alias semantics; this type only carries it to
 * `commonMain`.
 *
 * Implements `List<CatalogFieldInfo>` by delegation so the many surfaces that merely *read* the
 * catalog — operator pickers, `scalarPaths()`, dropdown option builders — keep taking a plain list.
 * Only the path-resolving surface names this type.
 *
 * One caveat follows from being a `data class` that is also a `List`: equality is asymmetric.
 * `catalog == list` is false — the generated `equals` compares the index too — while `list == catalog`
 * is true, because `AbstractList` compares elements. Compare catalogs to catalogs. It matters where one
 * is a `remember` key, and there both sides are catalogs, which is the conservative direction: a
 * changed index invalidates.
 */
data class BuilderCatalog(
    val fields: List<CatalogFieldInfo>,
    /**
     * Bare alias → the canonical segments it stands for.
     *
     * Mirrors what `ruleengine.core.domain.FieldPathResolver.expandRoot` returns for an alias root.
     * Empty for an element catalog: an alias declared inside a `collection` can never be used bare,
     * so a filter's operands have no bare alias to resolve.
     */
    val aliasPaths: Map<String, List<String>> = emptyMap(),
) : List<CatalogFieldInfo> by fields {

    /**
     * The same catalog with rule output variables appended.
     *
     * Exists so the variables can be added without dropping the index, which plain list concatenation
     * would do — `catalog + variables` is a `List`, not a [BuilderCatalog].
     */
    fun withVariables(variables: List<CatalogFieldInfo>): BuilderCatalog =
        if (variables.isEmpty()) this else copy(fields = fields + variables)

    companion object {
        val Empty: BuilderCatalog = BuilderCatalog(fields = emptyList())

        /** A catalog with no alias index — an element scope, or a test fixture. */
        fun of(fields: List<CatalogFieldInfo>): BuilderCatalog = BuilderCatalog(fields = fields)
    }
}
