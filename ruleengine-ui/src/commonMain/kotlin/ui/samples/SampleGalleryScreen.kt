package ui.samples

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ui.AccentCyan
import ui.AccentCyanSoft
import ui.AccentGreen
import ui.AccentGreenSoft
import ui.AccentOrange
import ui.AccentOrangeSoft
import ui.AccentPurple
import ui.AccentPurpleSoft
import ui.BgElevated
import ui.BgHover
import ui.BorderColor
import ui.PrimaryBlue
import ui.PrimaryGlow
import ui.TextMuted
import ui.TextPrimary
import ui.TextSecondary
import ui.samples.model.SampleCategory
import ui.samples.model.SampleDescriptor

@Composable
fun SampleGalleryScreen(
    onSampleSelected: (SampleDescriptor) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDescriptor by remember { mutableStateOf<SampleDescriptor?>(null) }

    Column(modifier = modifier.fillMaxSize().padding(all = 16.dp)) {
        Text(
            text = "Sample Rule Sets",
            style = MaterialTheme.typography.h6,
            color = TextPrimary,
        )
        Spacer(modifier = Modifier.height(height = 4.dp))
        Text(
            text = "Select a sample to load it into the editor — schema, actions, and rules included.",
            style = MaterialTheme.typography.body2,
            color = TextSecondary,
        )
        Spacer(modifier = Modifier.height(height = 16.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(count = 2),
            contentPadding = PaddingValues(all = 2.dp),
            verticalArrangement = Arrangement.spacedBy(space = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(space = 12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(SampleRegistry.all) { descriptor ->
                SampleCard(
                    descriptor = descriptor,
                    onLoad = { pendingDescriptor = descriptor },
                )
            }
        }
    }

    pendingDescriptor?.let { descriptor ->
        ConfirmLoadSampleDialog(
            descriptor = descriptor,
            onDismiss = { pendingDescriptor = null },
            onConfirm = {
                pendingDescriptor = null
                onSampleSelected(descriptor)
            },
        )
    }
}

/**
 * Asked before loading, because loading a sample replaces everything currently in the editor and
 * there is no undo across all three documents.
 */
@Suppress("FunctionNaming")
@Composable
private fun ConfirmLoadSampleDialog(
    descriptor: SampleDescriptor,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Load \"${descriptor.name}\"?",
                style = MaterialTheme.typography.subtitle1,
            )
        },
        text = {
            Text(
                text = "This will replace your current schema, actions, and rules.",
                style = MaterialTheme.typography.body2,
                color = TextSecondary,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(backgroundColor = PrimaryBlue),
            ) {
                Text(text = "Load Sample", color = TextPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel", color = TextSecondary)
            }
        },
        backgroundColor = BgElevated,
        shape = MaterialTheme.shapes.medium,
    )
}

@Composable
private fun SampleCard(
    descriptor: SampleDescriptor,
    onLoad: () -> Unit,
) {
    val (categoryColor, categoryBg) = descriptor.category.colors()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape = RoundedCornerShape(size = 12.dp))
            .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(size = 12.dp))
            .background(color = BgElevated)
            .padding(all = 16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            CategoryChip(label = descriptor.category.label, color = categoryColor, background = categoryBg)
            Spacer(modifier = Modifier.weight(weight = 1f))
            RuleCountBadge(count = descriptor.ruleCount)
        }

        Spacer(modifier = Modifier.height(height = 10.dp))

        Text(
            text = descriptor.name,
            style = MaterialTheme.typography.subtitle1,
            color = TextPrimary,
        )

        Spacer(modifier = Modifier.height(height = 4.dp))

        Text(
            text = descriptor.description,
            style = MaterialTheme.typography.body2,
            color = TextSecondary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(height = 54.dp),
        )

        Spacer(modifier = Modifier.height(height = 12.dp))

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Button(
                onClick = onLoad,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = PrimaryGlow,
                    contentColor = PrimaryBlue,
                ),
                elevation = ButtonDefaults.elevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(text = "Load Sample", style = MaterialTheme.typography.button, color = PrimaryBlue)
            }
        }
    }
}

@Composable
private fun CategoryChip(label: String, color: Color, background: Color) {
    Box(
        modifier = Modifier
            .clip(shape = RoundedCornerShape(size = 6.dp))
            .background(color = background)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.caption, color = color)
    }
}

@Composable
private fun RuleCountBadge(count: Int) {
    Box(
        modifier = Modifier
            .clip(shape = RoundedCornerShape(size = 6.dp))
            .background(color = BgHover)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = "$count rule${if (count != 1) "s" else ""}",
            style = MaterialTheme.typography.caption,
            color = TextMuted,
        )
    }
}

private fun SampleCategory.colors(): Pair<Color, Color> = when (this) {
    SampleCategory.FINANCE -> PrimaryBlue to PrimaryGlow
    // Shares the security palette: compliance and access control are the same kind of gatekeeping to a
    // reader scanning the gallery, and a sixth accent would only add a colour nothing else uses.
    SampleCategory.COMPLIANCE -> AccentPurple to AccentPurpleSoft
    SampleCategory.LOGGING -> AccentOrange to AccentOrangeSoft
    SampleCategory.ECOMMERCE -> AccentGreen to AccentGreenSoft
    SampleCategory.SECURITY -> AccentPurple to AccentPurpleSoft
    SampleCategory.LOGISTICS -> AccentCyan to AccentCyanSoft
}
