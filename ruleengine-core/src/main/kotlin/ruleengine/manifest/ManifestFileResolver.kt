package ruleengine.manifest

/**
 * Turns a path a manifest entry references into the file it names, or into the reason it cannot be
 * used.
 *
 * This is the seam that decides *where* a manifest's files come from. `RuleEngineBuilder` and
 * `RuleCatalogBuilder` ship one implementation per supported location — [FileSystemManifestFileResolver]
 * and `ClasspathManifestFileResolver` — and a caller whose rules live somewhere else entirely (a
 * database, an object store, a config server) can implement it instead of reassembling the load
 * pipeline by hand.
 *
 * An implementation must reject a [relativePath] that leaves the manifest's own location rather than
 * following it, and must report every problem as [ManifestFile.Unavailable] instead of throwing, so
 * the builder can attribute the failure to the manifest entry it belongs to.
 */
fun interface ManifestFileResolver {
    /**
     * @param relativePath the path exactly as the manifest entry spells it
     * @param label what the file is to the entry (`schema`, `actions`, `rules`), used in messages
     */
    fun resolve(relativePath: String, label: String): ManifestFile
}
