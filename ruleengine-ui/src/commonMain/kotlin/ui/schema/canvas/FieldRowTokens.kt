package ui.schema.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.core.domain.dto.field.isStructure
import ui.AccentCyan
import ui.AccentGreen
import ui.AccentOrange
import ui.AccentPurple
import ui.BgElevated
import ui.PrimaryBlue
import ui.PrimaryBlueLight
import ui.TextMuted
import ui.TextSecondary
import ui.schema.SchemaIssues
import ui.schema.model.EditableField
import ui.schema.yamlValue

/**
 * One schema field, as one line.
 *
 * Everything on the line is what the file will say about that field — the path, the alias a rule may
 * use instead, the declared type, the normalizer **chain in order**, and the operators. Nothing here is
 * an editing control: the row is read, and the Inspector is what writes. That is the same split the
 * Builder's outline canvas uses, and the reason the two areas now look like one app.
 *
 * The colours carry the same meanings they do in a rule: a field path is cyan, an alias purple, a number
 * orange. So `existingLoans.monthlyPayment` reads the same here as it does in the condition that uses it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Suppress("FunctionNaming")
@Composable
internal fun FieldTokens(field: EditableField) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(space = 6.dp),
        verticalArrangement = Arrangement.spacedBy(space = 3.dp),
    ) {
        Token(text = field.path.ifBlank { "…" }, color = AccentCyan)
        if (field.alias.isNotBlank()) {
            Punctuation(text = "as")
            Token(text = field.alias, color = AccentPurple)
        }
        TypeTag(type = field.type)

        if (field.format.isNotBlank()) {
            Crumb(text = "fmt ${field.format}", color = PrimaryBlueLight)
        }
        if (field.normalizers.isNotEmpty()) {
            // Joined with arrows because the order *is* the meaning: the engine applies the chain left
            // to right, and a comma would read as a set.
            Crumb(text = field.normalizers.joinToString(separator = " → "), color = AccentGreen)
        }

        val stray = SchemaIssues.strayOperators(field = field)
        val allowed = field.operators.filterNot { operator -> operator in stray }
        if (allowed.isNotEmpty()) {
            Crumb(text = allowed.joinToString(separator = " · "), color = TextSecondary)
        }
        stray.forEach { operator ->
            Crumb(text = "$operator ⚠", color = AccentOrange, bold = true)
        }
        if (field.type.isStructure) {
            Badge(text = memberLabel(count = field.fields.size))
        }
    }
}

private fun memberLabel(count: Int): String = if (count == 1) "1 member" else "$count members"

/** A name in the file: the path, the alias. Monospace, because that is how the file spells it. */
@Suppress("FunctionNaming")
@Composable
private fun Token(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.body2.copy(fontFamily = FontFamily.Monospace),
        color = color,
        // Wraps as a unit or not at all. The row's `word-break`-equivalent would otherwise split a
        // name across lines, which is how `integer` came out as `inte` / `ger` in the prototype once
        // the Inspector was dragged wide enough to squeeze this column.
        maxLines = 1,
        softWrap = false,
    )
}

/** Structural words that are not names — `as`. Not a target, because there is nothing behind it. */
@Suppress("FunctionNaming")
@Composable
private fun Punctuation(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.body2,
        color = TextMuted,
        maxLines = 1,
        softWrap = false,
    )
}

/**
 * The declared type.
 *
 * Lowercase and quiet, because it is the word the file uses (`type: text`) and because a column of
 * shouting badges outweighs the names beside them — which is what the first draft of the prototype did
 * before it was measured.
 */
@Suppress("FunctionNaming")
@Composable
private fun TypeTag(type: FieldType) {
    val colour = when (type) {
        FieldType.TEXT -> AccentCyan
        FieldType.INTEGER, FieldType.DECIMAL -> AccentOrange
        FieldType.BOOLEAN -> AccentGreen
        FieldType.STRING_SET -> AccentPurple
        FieldType.DATE, FieldType.DATE_TIME -> PrimaryBlueLight
        FieldType.COLLECTION, FieldType.OBJECT -> PrimaryBlue
    }
    Text(
        text = type.yamlValue,
        style = MaterialTheme.typography.caption.copy(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
        ),
        color = colour,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .clip(shape = RoundedCornerShape(size = 4.dp))
            .background(color = BgElevated)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/** What the field carries, kept on the line: read far more often than it is changed, so it is quiet. */
@Suppress("FunctionNaming")
@Composable
private fun Crumb(text: String, color: Color, bold: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption.copy(
            fontFamily = FontFamily.Monospace,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        ),
        color = color,
        maxLines = 1,
        softWrap = false,
    )
}

/** A count the reader must not have to open anything to discover. */
@Suppress("FunctionNaming")
@Composable
private fun Badge(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
        color = PrimaryBlueLight,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .clip(shape = RoundedCornerShape(percent = 50))
            .background(color = PrimaryBlue.copy(alpha = BADGE_ALPHA))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

private const val BADGE_ALPHA: Float = 0.16f
