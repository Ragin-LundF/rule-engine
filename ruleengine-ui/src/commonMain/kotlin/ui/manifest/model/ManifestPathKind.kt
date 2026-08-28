package ui.manifest.model

/**
 * Which of an entry's three kinds of path is being chosen.
 *
 * One seam rather than three lambdas: all three answer the same question — "which file, relative to the
 * manifest" — and differ only in the dialog's title and filter, which is a platform detail the Inspector
 * has no business knowing.
 */
enum class ManifestPathKind {
    SCHEMA,
    ACTIONS,
    RULE,
}
