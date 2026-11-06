package ruleengine.intellij.yaml

import com.intellij.json.JsonFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.SchemaType
import ruleengine.intellij.manifest.ManifestFiles
import ruleengine.intellij.yaml.model.RuleYamlKind

/** Attaches the JSON Schema of one [RuleYamlKind] to every YAML file of that kind. */
class RuleSchemaProvider(private val project: Project, private val kind: RuleYamlKind) : JsonSchemaFileProvider {
    private val schemaFile by lazy {
        val name = "${kind.name.lowercase()}.schema.json"
        LightVirtualFile(name, JsonFileType.INSTANCE, RuleJsonSchemas.schemaFor(kind = kind))
    }

    override fun isAvailable(file: VirtualFile): Boolean {
        return ManifestFiles.isYaml(file = file) &&
            RuleYamlKinds.kindOf(text = ManifestFiles.textOf(project = project, file = file)) == kind
    }

    override fun getName(): String {
        return kind.displayName
    }

    override fun getSchemaFile(): VirtualFile {
        return schemaFile
    }

    override fun getSchemaType(): SchemaType {
        return SchemaType.embeddedSchema
    }
}
