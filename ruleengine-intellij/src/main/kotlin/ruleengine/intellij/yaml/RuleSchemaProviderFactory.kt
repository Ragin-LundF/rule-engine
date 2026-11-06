package ruleengine.intellij.yaml

import com.intellij.openapi.project.Project
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import ruleengine.intellij.yaml.model.RuleYamlKind

class RuleSchemaProviderFactory : JsonSchemaProviderFactory {
    override fun getProviders(project: Project): List<JsonSchemaFileProvider> {
        return RuleYamlKind.entries.map { kind -> RuleSchemaProvider(project = project, kind = kind) }
    }
}
