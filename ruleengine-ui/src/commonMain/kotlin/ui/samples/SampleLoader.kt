package ui.samples

import io.github.ragin_lundf.ruleengine_ui.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi
import ui.samples.model.LoadedSample
import ui.samples.model.SampleDescriptor

@OptIn(ExperimentalResourceApi::class)
suspend fun loadSample(descriptor: SampleDescriptor): LoadedSample {
    val manifestYaml = Res.readBytes(descriptor.manifestResPath).decodeToString()
    val schemaYaml = Res.readBytes(descriptor.schemaResPath).decodeToString()
    val actionsYaml = Res.readBytes(descriptor.actionsResPath).decodeToString()

    val ruleFiles = descriptor.ruleResPaths.map { resPath ->
        manifestRelativePath(resPath = resPath) to Res.readBytes(resPath).decodeToString()
    }

    return LoadedSample(
        descriptor = descriptor,
        manifestYaml = manifestYaml,
        schemaYaml = schemaYaml,
        actionsYaml = actionsYaml,
        rulesText = ruleFiles.joinToString(separator = "\n\n") { (_, content) -> content },
        ruleFiles = ruleFiles,
    )
}

/**
 * The path a manifest would use for this rule file — `rules/x.rule` out of
 * `files/samples/<id>/rules/x.rule`.
 *
 * The manifest run diagram labels its file bands with the manifest's own relative paths, so a sample has
 * to hand over the same spelling a project does rather than the resource path it was read from.
 */
private fun manifestRelativePath(resPath: String): String {
    return resPath.substringAfter(delimiter = "/rules/", missingDelimiterValue = "")
        .let { name -> if (name.isEmpty()) resPath.substringAfterLast(delimiter = '/') else "rules/$name" }
}
