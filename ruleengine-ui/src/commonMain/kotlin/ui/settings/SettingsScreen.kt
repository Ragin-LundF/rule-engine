package ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ui.BgElevated
import ui.BorderColor
import ui.PrimaryBlue
import ui.TextMuted
import ui.TextPrimary
import ui.TextSecondary
import ui.components.SectionTitle
import ui.settings.model.AutoCompleteShortcut

/**
 * Application preferences that are not part of a project.
 *
 * Deliberately narrow: only settings that cannot be inferred and that a user has a reason to change.
 */
@Suppress("FunctionNaming")
@Composable
fun SettingsScreen(
    shortcut: AutoCompleteShortcut,
    onShortcutChange: (AutoCompleteShortcut) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(state = rememberScrollState())
            .padding(all = 16.dp),
        verticalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        SectionTitle(text = "EDITOR")

        Text(
            text = "Autocomplete shortcut",
            style = MaterialTheme.typography.body2,
            color = TextPrimary,
        )
        Text(
            text = "Completions are only offered when you press this. " +
                    "On macOS, Ctrl + Space is the system input-source switcher and Cmd + Space is " +
                    "Spotlight — if the popup flickers and closes, pick one of the Enter options.",
            style = MaterialTheme.typography.caption,
            color = TextSecondary,
        )

        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(space = 4.dp),
        ) {
            AutoCompleteShortcut.entries.forEach { entry ->
                ShortcutOption(
                    shortcut = entry,
                    selected = entry == shortcut,
                    onClick = { onShortcutChange(entry) },
                )
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ShortcutOption(
    shortcut: AutoCompleteShortcut,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape = RoundedCornerShape(size = 8.dp))
            .background(color = if (selected) PrimaryBlue.copy(alpha = 0.12f) else BgElevated)
            .border(
                width = 1.dp,
                color = if (selected) PrimaryBlue.copy(alpha = 0.45f) else BorderColor,
                shape = RoundedCornerShape(size = 8.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(size = 12.dp)
                .clip(shape = CircleShape)
                .background(color = if (selected) PrimaryBlue else BorderColor),
        )
        Text(
            text = shortcut.label,
            style = MaterialTheme.typography.body2.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (selected) PrimaryBlue else TextPrimary,
        )
        if (shortcut.insertsCharacter) {
            Text(
                text = "may be taken by macOS",
                style = MaterialTheme.typography.caption,
                color = TextMuted,
            )
        }
    }
}
