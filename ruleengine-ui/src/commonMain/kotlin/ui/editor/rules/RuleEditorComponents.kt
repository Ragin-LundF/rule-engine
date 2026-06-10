package ui.editor.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ui.AccentRed
import ui.Bg
import ui.BgElevated
import ui.BgSurface
import ui.BorderColor
import ui.PrimaryBlue
import ui.TextMuted
import ui.TextPrimary
import ui.TextSecondary

enum class StatusKind { IDLE, SUCCESS, ERROR }

val FIELD_SCHEMA_EXAMPLE: String = """
schema: my-schema

fields:
  fieldName:
    type: text
    normalizers:
      - trim
      - lowercase
    operators:
      - equals
      - contains
      - startsWith
  amount:
    type: integer
    operators:
      - equals
      - greaterThan
      - lessThan
""".trimIndent()

val ACTION_SCHEMA_EXAMPLE: String = """
actions:
  label:
    argTypes: [string]
  category:
    argTypes: [string]
  flag:
    argTypes: [string]
  score:
    argTypes: [integer]
""".trimIndent()

val MANIFEST_EXAMPLE: String = """
name: my-project

entries:
  - id: sample
    schema: schema.yaml
    actions: actions.yaml
    rules:
      - rules/rule.rule
""".trimIndent()

fun Modifier.drawBottomLine(w: Dp, color: Color): Modifier = this.drawWithContent {
    drawContent()
    drawLine(
        color = color,
        start = Offset(x = 0f, y = size.height),
        end = Offset(x = size.width, y = size.height),
        strokeWidth = w.toPx()
    )
}

fun Modifier.drawTopLine(w: Dp, color: Color): Modifier = this.drawWithContent {
    drawContent()
    drawLine(
        color = color,
        start = Offset(x = 0f, y = 0f),
        end = Offset(x = size.width, y = 0f),
        strokeWidth = w.toPx()
    )
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.subtitle1,
        color = TextSecondary,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
fun PanelDivider() {
    Divider(color = BorderColor, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
fun AppButton(
    label: String,
    primary: Boolean = false,
    danger: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bg = when {
        primary -> PrimaryBlue
        danger -> AccentRed.copy(alpha = 0.12f)
        else -> Color.Transparent
    }
    val border = when {
        primary -> PrimaryBlue
        danger -> AccentRed
        else -> BorderColor
    }
    val text = when {
        primary -> Bg
        danger -> AccentRed
        else -> TextSecondary
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(5.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = MaterialTheme.typography.button, color = text)
    }
}

@Composable
fun Chip(
    label: String,
    bg: Color = BgElevated,
    textColor: Color = TextSecondary,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.caption, color = textColor)
    }
}

@Composable
fun PlainCodeEditor(
    text: String,
    onTextChange: (String) -> Unit,
    placeholder: String = "",
    modifier: Modifier = Modifier,
    textStyle: TextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        color = TextPrimary,
        lineHeight = 19.sp,
    ),
) {
    val scrollState = rememberScrollState()
    val lineCount = remember(text) { text.lines().size.coerceAtLeast(1) }
    val lineNumberWidthDp = 40.dp
    val editorPaddingDp = 10.dp

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Bg)
            .border(1.dp, BorderColor, RoundedCornerShape(6.dp)),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(lineNumberWidthDp)
                    .background(BgSurface),
            ) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier
                        .padding(top = editorPaddingDp, end = 6.dp, start = 4.dp)
                        .verticalScroll(scrollState),
                    horizontalAlignment = Alignment.End,
                ) {
                    repeat(lineCount) { index ->
                        Text(
                            text = (index + 1).toString(),
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 19.sp,
                                color = TextMuted,
                            ),
                        )
                    }
                }
            }
            Box(modifier = Modifier.width(1.dp).background(BorderColor))
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState),
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(editorPaddingDp),
                    textStyle = textStyle,
                    cursorBrush = SolidColor(PrimaryBlue),
                )
            }
        }

        if (text.isEmpty() && placeholder.isNotEmpty()) {
            Text(
                text = placeholder,
                modifier = Modifier.padding(
                    start = lineNumberWidthDp + 1.dp + editorPaddingDp,
                    top = editorPaddingDp,
                ),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TextMuted,
                    lineHeight = 19.sp,
                ),
            )
        }
    }
}


