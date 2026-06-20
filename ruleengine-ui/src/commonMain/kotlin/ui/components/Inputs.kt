package ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import ui.BgInput
import ui.BorderColor
import ui.PrimaryBlue
import ui.TextPrimary
import ui.TextSecondary

/**
 * A section title label used as a header inside catalog panels.
 */
@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.subtitle1,
        color = TextSecondary,
        modifier = modifier.padding(bottom = 8.dp),
    )
}

/**
 * A compact search/filter text field for catalog panels.
 */
@Composable
fun CatalogSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "Search…",
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height = 40.dp)
            .clip(shape = RoundedCornerShape(size = 8.dp))
            .background(color = BgInput)
            .border(
                width = 1.dp,
                color = BorderColor,
                shape = RoundedCornerShape(size = 8.dp),
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.body2.copy(color = TextPrimary),
            cursorBrush = SolidColor(PrimaryBlue),
            decorationBox = { innerTextField ->
                if (value.isBlank()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.body2,
                        color = TextSecondary,
                    )
                }
                innerTextField()
            },
        )
    }
}

/**
 * A modern filled text field for forms and editors.
 */
@Composable
fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    singleLine: Boolean = true,
) {
    Column(modifier = modifier) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.caption,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height = if (singleLine) 40.dp else 80.dp)
                .clip(shape = RoundedCornerShape(size = 8.dp))
                .background(color = BgInput)
                .border(
                    width = 1.dp,
                    color = BorderColor,
                    shape = RoundedCornerShape(size = 8.dp),
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = singleLine,
                textStyle = MaterialTheme.typography.body1.copy(color = TextPrimary),
                cursorBrush = SolidColor(PrimaryBlue),
                decorationBox = { innerTextField ->
                    if (value.isBlank()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.body2,
                            color = TextSecondary,
                        )
                    }
                    innerTextField()
                },
            )
        }
    }
}
