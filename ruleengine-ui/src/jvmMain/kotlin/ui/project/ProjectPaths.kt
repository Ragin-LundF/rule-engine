package ui.project

import java.nio.file.Path

/**
 * On-disk layout of a workbench project.
 *
 * A project is the directory holding its manifest. The manifest file itself may carry any name the
 * user chose in the save dialog, so the name is part of the session rather than a constant here —
 * only the subdirectories are fixed, because nothing ever asks the user about them.
 */
object ProjectPaths {

    /** Pre-filled in the save dialog; the user is free to replace it. */
    const val DEFAULT_MANIFEST_FILE: String = "manifest.yaml"

    const val RULES_DIR: String = "rules"
    const val SCHEMAS_DIR: String = "schemas"

    const val DEFAULT_SCHEMA_FILE: String = "schema.yaml"
    const val DEFAULT_ACTIONS_FILE: String = "actions.yaml"

    fun rulesDir(root: Path): Path = root.resolve(RULES_DIR)

    fun schemasDir(root: Path): Path = root.resolve(SCHEMAS_DIR)

    /**
     * The path of [target] as it should be written into a manifest stored in [root].
     *
     * Relative, using `../` when the target sits outside the project, so that a project and the
     * schemas it shares stay portable as long as they move together. Falls back to an absolute path
     * when the two live on different filesystem roots, where no relative path exists at all —
     * callers surface that as "not portable" rather than silently writing something unusable.
     */
    fun relativize(root: Path, target: Path): String {
        val normalizedRoot = root.toAbsolutePath().normalize()
        val normalizedTarget = target.toAbsolutePath().normalize()

        val relative = runCatching { normalizedRoot.relativize(normalizedTarget).toString() }
            .getOrElse { return normalizedTarget.toString() }

        // Manifests are written with forward slashes on every platform so a project authored on
        // Windows still resolves on macOS and Linux.
        return relative.replace(oldChar = '\\', newChar = '/')
    }

    /** True when [relativePath] points outside the project — what the "(shared)" badge reports. */
    fun isExternal(relativePath: String): Boolean {
        return relativePath.startsWith(prefix = "../") ||
                runCatching { Path.of(relativePath).isAbsolute }.getOrDefault(defaultValue = false)
    }
}
