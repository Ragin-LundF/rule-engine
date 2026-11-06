package ruleengine.intellij.manifest.model

import com.intellij.openapi.vfs.VirtualFile

/** One line of the manifest tool window. [isRule] marks the lines that link a rule when opened. */
data class ManifestNode(
    val label: String,
    val file: VirtualFile? = null,
    val isRule: Boolean = false,
) {
    override fun toString(): String {
        return label
    }
}
