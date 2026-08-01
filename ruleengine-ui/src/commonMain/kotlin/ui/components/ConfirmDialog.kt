package ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.BgSurface
import ui.TextPrimary
import ui.TextSecondary

/**
 * Modal question with up to three answers.
 *
 * Three because the questions this app needs to ask are rarely yes/no: "unsaved changes" is
 * save/discard/cancel, and a shared schema is save-there/copy-locally/cancel. Collapsing those to two
 * buttons would force the user to cancel and guess at a second route.
 *
 * [confirm] is the action that proceeds, [neutral] the safe alternative, and dismissing always means
 * "do nothing" — so an accidental click outside never destroys work.
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "Cancel",
    neutralLabel: String? = null,
    onNeutral: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.subtitle1,
                color = TextPrimary,
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.body2,
                color = TextSecondary,
            )
        },
        buttons = {
            Row(
                modifier = Modifier.padding(all = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
            ) {
                ToolbarButton(label = dismissLabel, onClick = onDismiss)
                if (neutralLabel != null && onNeutral != null) {
                    ToolbarButton(label = neutralLabel, onClick = onNeutral)
                }
                ToolbarButton(label = confirmLabel, onClick = onConfirm, primary = true)
            }
        },
        backgroundColor = BgSurface,
        shape = RoundedCornerShape(size = 10.dp),
    )
}
