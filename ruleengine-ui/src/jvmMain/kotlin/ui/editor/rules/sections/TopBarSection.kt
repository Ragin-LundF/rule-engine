package ui.editor.rules.sections

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.ragin_lundf.ruleengine_ui.generated.resources.Res
import io.github.ragin_lundf.ruleengine_ui.generated.resources.app
import org.jetbrains.compose.resources.painterResource
import ui.AccentOrange
import ui.BgSurface
import ui.PrimaryBlue
import ui.TextPrimary
import ui.components.StatusBadge
import ui.components.ToolbarButton
import ui.components.header.BindingChip
import ui.components.header.densityFor
import ui.components.header.model.BarDensity
import ui.components.header.model.BindingMenuItem
import ui.components.header.model.BindingSpec
import ui.project.ProjectWorkspace
import ui.project.manifest.ManifestEntrySelection
import ui.theme.ThemeController
import ui.theme.ThemePersistence

/**
 * Top bar: app brand, then the project actions, then the two shared-file exports.
 *
 * The six independent load/save buttons that used to live here have collapsed into one project
 * group. Loading a schema is no longer a toolbar action at all — it belongs next to the schema it
 * replaces, in the Schema area, where the user can see what is linked.
 *
 * @param inspectorOpen      Whether the right panel is open *on the Inspector tab*, which is what the
 *                           button reports as its pressed state — open on Simulate is not open here.
 * @param onToggleInspector  Opens the Inspector, or closes the panel when it is already showing it.
 * @param entrySelection     What the entry picker shows, derived by the caller from the session *and*
 *                           the parsed manifest; null hides the picker because there is no manifest at
 *                           all. Passed in rather than read off the session here — reading only the
 *                           session is what made the picker name the previous project after a sample
 *                           was loaded.
 */
@Composable
fun TopBarSection(
    workspace: ProjectWorkspace,
    onManageEntries: () -> Unit,
    inspectorOpen: Boolean,
    onToggleInspector: () -> Unit,
    entrySelection: ManifestEntrySelection?,
) {
    val isDirty = workspace.isDirty

    BoxWithConstraints(modifier = Modifier.fillMaxWidth().background(color = BgSurface)) {
        // Measured on the window, unlike an area header, which is measured on its panel: this bar has
        // the whole width, and it gives up its identity first and its labels last.
        val density = densityFor(
            width = maxWidth,
            fullWidth = TOP_BAR_FULL_WIDTH,
            compactWidth = TOP_BAR_COMPACT_WIDTH,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space = 10.dp),
        ) {
            AppLogo()
            if (density == BarDensity.FULL) {
                Text(
                    text = "Rule Engine",
                    style = MaterialTheme.typography.h6.copy(fontWeight = FontWeight.SemiBold),
                    color = TextPrimary,
                )
            }
            if (density != BarDensity.MINIMAL) {
                StatusBadge(label = "WORKBENCH", color = PrimaryBlue)
            }

            entrySelection?.let { selection ->
                ManifestEntryPicker(
                    selection = selection,
                    density = density,
                    onSelect = { entryId -> workspace.selectEntry(entryId = entryId) },
                    onAdd = {
                        // The new entry needs naming, and the Manifest area is where its card lives.
                        if (workspace.addEntry(entryId = workspace.suggestEntryId())) onManageEntries()
                    },
                )
            }

            // What the `•` on the old Save button had to carry on its own. A badge says it once, where
            // the eye already is, and leaves the button to say what it does.
            if (isDirty) {
                StatusBadge(label = "UNSAVED", color = AccentOrange)
            }

            Spacer(modifier = Modifier.weight(weight = 1f))

            ProjectMenuButton(workspace = workspace)
            SaveSplitButton(workspace = workspace, isDirty = isDirty)

            Spacer(modifier = Modifier.width(width = 6.dp))

            // The only entry point to the Inspector that does not require finding the collapsed strip on
            // the right edge first. `primary` rather than a separate marker: the pressed look is the
            // state, so the button cannot disagree with the panel.
            ToolbarButton(
                label = "Inspector",
                onClick = onToggleInspector,
                primary = inspectorOpen,
            )

            // The one place a picture beats a word: everyone reads sun and moon, and the button shows
            // the theme it switches *to*. The sun is drawn from the emoji font, so it is a colour glyph
            // where the moon is monochrome — deliberate here, and the reason this is the only
            // pictogram left on the bar.
            ToolbarButton(
                label = if (ThemeController.isDark) "☀" else "☾",
                onClick = {
                    ThemeController.isDark = !ThemeController.isDark
                    ThemePersistence.saveIsDark(ThemeController.isDark)
                },
            )
        }
    }
}

/** Below this the wordmark goes; the logo stays, because it is the mark people aim at. */
private val TOP_BAR_FULL_WIDTH: Dp = 1_300.dp

/** Below this the badge goes and the entry chip drops its key. The right-hand controls are words, and
 *  words do not shrink: what is left is short enough to fit. */
private val TOP_BAR_COMPACT_WIDTH: Dp = 1_100.dp

/**
 * Which manifest entry the workbench is editing.
 *
 * In the top bar rather than in the Manifest area because the choice governs every other area: the
 * schema, the actions and the rule files on screen all belong to the entry named here.
 *
 * Read-only when the manifest has no project behind it — a loaded sample. It still names the entry,
 * because that is the question the top bar is here to answer, but it drops the `▾` and does not open:
 * both operations behind the menu need a session, so a menu would offer two controls that do nothing.
 */
@Composable
private fun ManifestEntryPicker(
    selection: ManifestEntrySelection,
    density: BarDensity,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
) {
    val activeEntryId = selection.activeEntryId

    // No items when the manifest has no project behind it — a loaded sample. The chip still names the
    // entry, because that is the question it is here to answer, but it does not open: both operations
    // behind the menu need a session, so a menu would offer two controls that do nothing.
    val items = if (!selection.editable) {
        emptyList()
    } else {
        selection.entryIds.mapIndexed { index, entryId ->
            BindingMenuItem(
                id = entryId,
                label = entryId,
                selected = entryId == activeEntryId,
                sectionTitle = if (index == 0) "Manifest entry" else null,
            )
        } + BindingMenuItem(id = NEW_ENTRY, label = "New entry…", separatorBefore = true)
    }

    BindingChip(
        spec = BindingSpec(label = "Entry", value = activeEntryId, items = items),
        density = density,
        onItem = { id -> if (id == NEW_ENTRY) onAdd() else onSelect(id) },
    )
}

/** The picker's id for "make a new one"; anything else is an entry id. */
private const val NEW_ENTRY = "*new*"

@Composable
private fun AppLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(Res.drawable.app),
        contentDescription = "Rule Engine Workbench",
        modifier = modifier
            .clip(shape = CircleShape)
            .background(color = PrimaryBlue.copy(alpha = 0.12f))
            .padding(all = 4.dp)
            .size(size = 30.dp),
    )
}
