package ui.workbench.areas

import ui.components.header.model.BadgeTone
import ui.components.header.model.BindingBadge
import ui.components.header.model.BindingMenuItem
import ui.components.header.model.BindingSpec
import ui.project.ProjectPaths

/** The chip's menu items, as ids: what the area gets back when one is chosen. */
internal const val BINDING_LINK = "link"
internal const val BINDING_UNLINK = "unlink"

/**
 * The binding chip for an area that edits a linked file — the Schema and Actions areas.
 *
 * This is what became of `LinkedFileHeader`, the full-width bar that used to sit above those two
 * editors. The bar said the same things in a whole row of its own, in a shape no other area had; as a
 * chip it says them in the same slot, in the same place, as the Rules area's file and the Manifest's.
 *
 * A shared file gets a badge because replacing it is a decision about other projects too, and a missing
 * one gets a louder badge because the area below it is showing the last thing that loaded.
 */
internal fun linkedFileBinding(linkedPath: String?, isMissing: Boolean): BindingSpec {
    val badge = when {
        isMissing -> BindingBadge(text = "not found", tone = BadgeTone.ERROR)
        linkedPath != null && ProjectPaths.isExternal(relativePath = linkedPath) ->
            BindingBadge(text = "shared", tone = BadgeTone.INFO)

        else -> null
    }

    val items = buildList {
        add(
            element = BindingMenuItem(
                id = BINDING_LINK,
                label = if (linkedPath == null) "Link file…" else "Change…",
                sectionTitle = "Linked file",
            ),
        )
        if (linkedPath != null) {
            add(element = BindingMenuItem(id = BINDING_UNLINK, label = "Unlink"))
        }
    }

    return BindingSpec(
        label = "File",
        value = linkedPath ?: "not linked",
        badge = badge,
        items = items,
    )
}
