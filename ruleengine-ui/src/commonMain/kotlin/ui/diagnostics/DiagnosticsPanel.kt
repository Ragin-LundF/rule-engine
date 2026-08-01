package ui.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ruleengine.core.errors.Severity
import ui.AccentGreen
import ui.AccentOrange
import ui.AccentRed
import ui.Bg
import ui.BorderColor
import ui.PrimaryBlue
import ui.TextMuted
import ui.TextPrimary

/**
 * Enhanced diagnostics list with quick-fix buttons.
 *
 * @param diagnostics   Enriched diagnostics produced by [DiagnosticMapper].
 * @param emptyText     Text shown when [diagnostics] is empty.
 * @param onRowClick    Called when the user clicks a row (e.g. to jump to the line).
 * @param onApplyFix    Called when the user clicks a quick-fix button; receives the [QuickFix].
 */
@Suppress("FunctionNaming", "LongMethod")
@Composable
fun DiagnosticsPanel(
    diagnostics: List<UiDiagnosticWithFix>,
    emptyText: String = "No diagnostics — press Validate to check your rule.",
    onRowClick: (UiDiagnosticWithFix) -> Unit = {},
    onApplyFix: (QuickFix) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (diagnostics.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(130.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Bg)
                .border(1.dp, BorderColor, RoundedCornerShape(6.dp))
                .padding(14.dp),
        ) {
            Text(
                text = emptyText,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = if (emptyText.startsWith("No diagnostics")) TextMuted else AccentGreen,
                ),
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Bg)
            .border(1.dp, BorderColor, RoundedCornerShape(6.dp)),
    ) {
        items(diagnostics) { d ->
            val rowBg = when (d.severity) {
                Severity.ERROR -> AccentRed.copy(alpha = 0.07f)
                Severity.WARNING -> AccentOrange.copy(alpha = 0.07f)
                Severity.INFO -> Color.Transparent
            }
            val dotColor = when (d.severity) {
                Severity.ERROR -> AccentRed
                Severity.WARNING -> AccentOrange
                Severity.INFO -> PrimaryBlue
            }
            val lineLabel = d.line?.let { "L$it${d.column?.let { c -> ":$c" } ?: ""}" }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(rowBg)
                    .clickable { onRowClick(d) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(7.dp).background(dotColor, CircleShape))
                    Text(
                        text = d.message,
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimary,
                        ),
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    lineLabel?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.caption,
                            color = TextMuted,
                        )
                    }
                }

                // Hint + quick-fix button row
                val hint = d.hint
                val fix = d.quickFix
                if (hint != null || fix is QuickFix.ReplaceToken) {
                    Row(
                        modifier = Modifier.padding(start = 15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (hint != null) {
                            Text(
                                text = hint,
                                style = MaterialTheme.typography.caption,
                                color = AccentGreen,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        if (fix is QuickFix.ReplaceToken) {
                            TextButton(
                                onClick = { onApplyFix(fix) },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = PrimaryBlue,
                                ),
                                modifier = Modifier.height(24.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 6.dp,
                                    vertical = 0.dp,
                                ),
                            ) {
                                Text(
                                    text = fix.label,
                                    style = MaterialTheme.typography.caption,
                                )
                            }
                        }
                    }
                }
            }
            Divider(color = BorderColor.copy(alpha = 0.4f), thickness = 0.5.dp)
        }
    }
}
