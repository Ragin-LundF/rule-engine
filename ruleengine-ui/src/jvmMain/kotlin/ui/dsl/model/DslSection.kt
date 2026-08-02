package ui.dsl.model
/**
 * The structural section of the rule DSL where the cursor is located.
 *
 * [THEN] and [ELSE] are kept apart even though both offer the same completions: a caller that wants
 * to say which branch the cursor is in — a status bar, a diagnostic — cannot recover that once the
 * two are merged, and merging them is a one-line `in setOf(...)` at each use.
 */
enum class DslSection { TOP_LEVEL, RULE_HEADER, WHEN, THEN, ELSE }
