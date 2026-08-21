package ruleengine.builder

import ruleengine.compiler.RuleFileAsts
import ruleengine.core.domain.dto.action.ActionSchema
import ruleengine.core.domain.dto.field.FieldSchema

/**
 * Everything one manifest entry declares, loaded and parsed but neither validated nor compiled.
 *
 * The load phase of [RuleEngineBuilder] stops here for a caller whose job is to *report* on an entry
 * rather than to run it: a validator has to see the diagnostics the builder would have thrown away by
 * failing, and it needs the rules per file, which the builder flattens because execution does not care
 * which file a rule was written in.
 *
 * @param schema the schema the entry's rules are written against — the member schema when the entry
 *   declares a `scope`, since that is what its rules name.
 */
data class EntryInputs(
    val entryId: String,
    val schema: FieldSchema,
    val actions: ActionSchema?,
    val files: List<RuleFileAsts>,
)
