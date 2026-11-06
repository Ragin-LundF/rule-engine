package ruleengine.intellij.manifest.model

import com.intellij.openapi.vfs.VirtualFile
import ruleengine.core.domain.dto.action.ActionSchema
import ruleengine.core.domain.dto.field.FieldSchema

/**
 * What a rule file needs from its manifest: the entry that lists it and that entry's schemas.
 *
 * A schema is null when the entry names none or it failed to load; [problems] says which.
 * [schemaFile] and [actionsFile] are kept so navigation can jump into them. [ruleFiles] are the entry's
 * rule files in manifest order, resolved but not read.
 */
data class RuleSources(
    val manifest: VirtualFile,
    val entryId: String,
    val schemaFile: VirtualFile?,
    val actionsFile: VirtualFile?,
    val ruleFiles: List<VirtualFile>,
    val schema: FieldSchema?,
    val actions: ActionSchema?,
    val fieldNames: Set<String>,
    val actionNames: Set<String>,
    val problems: List<String>,
)
