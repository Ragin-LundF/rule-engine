package ui.components

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ui.TextSecondary

/** A column heading in one of the editor tables. */
@Suppress("FunctionNaming")
@Composable
fun HeaderCell(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.caption,
        fontWeight = FontWeight.SemiBold,
        color = TextSecondary,
        fontSize = 11.sp,
    )
}
