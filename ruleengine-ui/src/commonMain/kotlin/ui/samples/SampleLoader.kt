package ui.samples

import io.github.ragin_lundf.ruleengine_ui.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

@OptIn(ExperimentalResourceApi::class)
suspend fun loadSample(descriptor: SampleDescriptor): LoadedSample {
    val schemaYaml = Res.readBytes(descriptor.schemaResPath).decodeToString()
    val actionsYaml = Res.readBytes(descriptor.actionsResPath).decodeToString()
    val ruleTexts = mutableListOf<String>()
    for (path in descriptor.ruleResPaths) {
        ruleTexts.add(Res.readBytes(path).decodeToString())
    }
    val rulesText = ruleTexts.joinToString(separator = "\n\n")
    return LoadedSample(
        descriptor = descriptor,
        schemaYaml = schemaYaml,
        actionsYaml = actionsYaml,
        rulesText = rulesText,
    )
}
