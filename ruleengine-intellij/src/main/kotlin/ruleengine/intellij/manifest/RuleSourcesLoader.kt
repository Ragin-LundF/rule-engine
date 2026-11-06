package ruleengine.intellij.manifest

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import ruleengine.core.domain.FieldPathResolver
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.intellij.manifest.model.RuleSources
import ruleengine.schema.ActionSchemaLoader
import ruleengine.schema.FieldSchemaLoader

/**
 * Loads the manifest entry and schemas of one rule file.
 *
 * Reads only the linked manifest and the two schema files its entry names. The result is cached on
 * the rule's PSI and rebuilt after any PSI change (which includes edits to the schema YAML) or after
 * a link changes.
 */
object RuleSourcesLoader {
    fun sourcesFor(ruleFile: PsiFile): RuleSources? {
        val project = ruleFile.project
        return CachedValuesManager.getCachedValue(ruleFile) {
            CachedValueProvider.Result.create(
                ruleFile.originalFile.virtualFile?.let { rule -> load(project = project, rule = rule) },
                PsiModificationTracker.getInstance(project),
                ManifestLinks.getInstance(project).tracker,
            )
        }
    }

    private fun load(project: Project, rule: VirtualFile): RuleSources? {
        val manifestFile = ManifestLinks.getInstance(project).manifestFor(rule = rule) ?: return null
        val manifest = ManifestFiles.parse(project = project, file = manifestFile) ?: return null
        val entry = ManifestFiles.entryListing(manifestFile = manifestFile, manifest = manifest, rule = rule)
            ?: return null
        val problems = mutableListOf<String>()
        val schemaFile = entry.schema?.let { path ->
            resolveOrReport(manifestFile = manifestFile, path = path, problems = problems)
        }
        val actionsFile = entry.actions?.let { path ->
            resolveOrReport(manifestFile = manifestFile, path = path, problems = problems)
        }
        val schema = schemaFile?.let { file ->
            loadOrReport(file = file, problems = problems) {
                val content = ManifestFiles.textOf(project = project, file = file)
                FieldSchemaLoader.loadFromString(content = content, nameHint = file.name)
            }
        }
        val actions = actionsFile?.let { file ->
            loadOrReport(file = file, problems = problems) {
                ActionSchemaLoader.loadFromString(content = ManifestFiles.textOf(project = project, file = file))
            }
        }
        return RuleSources(
            manifest = manifestFile,
            entryId = entry.id,
            schemaFile = schemaFile,
            actionsFile = actionsFile,
            ruleFiles = entry.rules.mapNotNull { path ->
                ManifestFiles.resolve(manifestFile = manifestFile, relativePath = path)
            },
            schema = schema,
            actions = actions,
            fieldNames = schema?.let { loaded -> fieldNamesOf(schema = loaded) }.orEmpty(),
            actionNames = actions?.actions?.keys.orEmpty(),
            problems = problems,
        )
    }

    private fun resolveOrReport(manifestFile: VirtualFile, path: String, problems: MutableList<String>): VirtualFile? {
        val file = ManifestFiles.resolve(manifestFile = manifestFile, relativePath = path)
        if (file == null) {
            problems += "File '$path' named in ${manifestFile.name} does not exist"
        }
        return file
    }

    private fun <T> loadOrReport(file: VirtualFile, problems: MutableList<String>, load: () -> T): T? {
        return runCatching(load).onFailure { error ->
            problems += "${file.name} could not be loaded: ${error.message}"
        }.getOrNull()
    }

    /** Every name a rule may use for a field: ids, aliases and nested scalar paths, as the UI editor colours them. */
    private fun fieldNamesOf(schema: FieldSchema): Set<String> {
        return buildSet {
            schema.fields.forEach { (id, field) ->
                add(id.value)
                field.alias?.takeIf { alias -> alias.isNotBlank() }?.let(::add)
            }
            addAll(FieldPathResolver.scalarPaths(schema = schema).keys.map { id -> id.value })
            addAll(schema.aliasTargets.map { target -> target.alias })
        }
    }
}
