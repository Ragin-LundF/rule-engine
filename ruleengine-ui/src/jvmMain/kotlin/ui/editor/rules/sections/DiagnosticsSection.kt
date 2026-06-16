package ui.editor.rules.sections

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
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
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
import ui.editor.rules.Chip
import ui.editor.rules.RuleEditorState

/** Diagnostics section: displays validation errors and warnings below the editor panels. */
@Suppress("FunctionNaming", "LongMethod", "CyclomaticComplexMethod")
@Composable
fun DiagnosticsSection(state: RuleEditorState) {
    val diagnosticsList by state.diagnosticsList
    val diagnosticsText by state.diagnosticsText
    var ruleValue by state.ruleValue

    // Spacer that was previously between the right-panel editor and diagnostics
    Spacer(Modifier.height(10.dp))

    Column(modifier = Modifier.padding(horizontal = 12.dp)) {

        // ── Diagnostics ───────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Diagnostics", style = MaterialTheme.typography.h6, color = TextPrimary)
            Spacer(Modifier.weight(1f))
            if (diagnosticsList.isNotEmpty()) {
                val errors = diagnosticsList.count { it.severity == Severity.ERROR }
                val warnings = diagnosticsList.count { it.severity == Severity.WARNING }
                if (errors > 0) {
                    Chip(
                        label = "$errors error${if (errors > 1) "s" else ""}",
                        bg = AccentRed.copy(alpha = 0.15f),
                        textColor = AccentRed
                    )
                }
                if (warnings > 0) {
                    Chip(
                        label = "$warnings warning${if (warnings > 1) "s" else ""}",
                        bg = AccentOrange.copy(alpha = 0.15f),
                        textColor = AccentOrange
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))

        if (diagnosticsList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height = 130.dp)
                    .clip(shape = RoundedCornerShape(size = 6.dp))
                    .background(color = Bg)
                    .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(size = 6.dp))
                    .padding(all = 14.dp),
            ) {
                Text(
                    text = diagnosticsText.ifBlank {
                        "No diagnostics — press Validate to check your rule."
                    },
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                        color = if (diagnosticsText.isBlank()) TextMuted else AccentGreen
                    ),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height = 160.dp)
                    .clip(shape = RoundedCornerShape(size = 6.dp))
                    .background(color = Bg)
                    .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(size = 6.dp)),
            ) {
                items(diagnosticsList) { d ->
                    val rowBg = when (d.severity) {
                        Severity.ERROR -> AccentRed.copy(alpha = 0.07f)
                        Severity.WARNING -> AccentOrange.copy(alpha = 0.07f)
                        else -> Color.Transparent
                    }
                    val dotColor = when (d.severity) {
                        Severity.ERROR -> AccentRed
                        Severity.WARNING -> AccentOrange
                        else -> PrimaryBlue
                    }
                    val lineLabel = d.line?.let { "L${it}${d.column?.let { c -> ":$c" } ?: ""}" }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(color = rowBg)
                            .clickable {
                                runCatching {
                                    val line = d.line ?: -1
                                    val col = d.column ?: -1
                                    if (line > 0) {
                                        val lines = ruleValue.text.lines()
                                        var offset = 0
                                        for (i in 0 until minOf(
                                            a = line - 1,
                                            b = lines.size - 1
                                        )) {
                                            offset += lines[i].length + 1
                                        }
                                        if (col > 0) offset += (col - 1)
                                        ruleValue = TextFieldValue(
                                            ruleValue.text,
                                            selection = TextRange(
                                                index = offset.coerceIn(0, ruleValue.text.length)
                                            )
                                        )
                                    }
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.size(7.dp).background(dotColor, CircleShape))
                        Text(
                            text = d.message,
                            style = TextStyle(
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextPrimary
                            ),
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        lineLabel?.let { Chip(label = it) }
                        d.suggestion?.let {
                            Text(
                                "→ $it", style = MaterialTheme.typography.caption, color = AccentGreen,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Divider(color = BorderColor.copy(alpha = 0.4f), thickness = 0.5.dp)
                }
            }
        }
    }
}


