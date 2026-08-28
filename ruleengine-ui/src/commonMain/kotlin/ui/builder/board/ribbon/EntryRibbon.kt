package ui.builder.board.ribbon

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ui.AccentCyan
import ui.AccentOrange
import ui.AccentRed
import ui.BgSurface
import ui.BorderColor
import ui.PrimaryBlue
import ui.PrimaryGlow
import ui.TextPrimary
import ui.TextSecondary
import ui.builder.board.ribbon.model.RibbonCard
import ui.builder.board.ribbon.model.RibbonGroup

/**
 * The run, along the top: every rule in the order it is evaluated, grouped by file.
 *
 * This is what no other view shows. The tree lists rules alphabetically inside a file, the Builder shows
 * one rule, and the table shows all of them without order. Evaluation order is what decides whether a
 * `$variable` is set when it is read and what a `stop` cuts off, so it is the thing an author most needs
 * and has least access to.
 *
 * Every card has the same four rows whether or not it has anything to put in them — see [RibbonCard].
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
internal fun EntryRibbon(
    groups: List<RibbonGroup>,
    selectedRuleId: String,
    highlighted: VariableFlow.Flow?,
    onSelectRule: (relativePath: String, ruleId: String) -> Unit,
    onHighlightVariable: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(state = rememberScrollState())
            // The 5dp above and below is the clearance the ribbon row needs so the file labels do not
            // touch the switch above them. It is stated here rather than left to the cards' own padding
            // because it belongs to the row, not to a card.
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 18.dp),
        verticalAlignment = Alignment.Top,
    ) {
        groups.forEach { group ->
            RibbonFileGroup(
                group = group,
                selectedRuleId = selectedRuleId,
                highlighted = highlighted,
                onSelectRule = { ruleId -> onSelectRule(group.relativePath, ruleId) },
                onHighlightVariable = onHighlightVariable,
            )
        }
    }
}

/**
 * One file's cards, under the file's name.
 *
 * **The width is stated, not measured** — `n × card + (n − 1) × arrow`. This is the one piece of
 * layout in the whole feature that is written defensively, and it is written that way because the HTML
 * prototype of this ribbon painted its cards on top of each other in two of three browser engines: the
 * group was a shrinkable flex child, and each engine computed a different intrinsic width for it
 * (Firefox gave a four-card group 639px where it needed 794px). `Row` with `horizontalScroll` and
 * intrinsically-sized children has the same failure mode, and it fails the same silently.
 *
 * The card count is data — it is on [RibbonGroup] — so the width is arithmetic rather than a
 * measurement, and there is nothing for a measuring pass to get wrong.
 */
@Suppress("FunctionNaming")
@Composable
private fun RibbonFileGroup(
    group: RibbonGroup,
    selectedRuleId: String,
    highlighted: VariableFlow.Flow?,
    onSelectRule: (String) -> Unit,
    onHighlightVariable: (String?) -> Unit,
) {
    val n = group.cardCount
    val statedWidth: Dp = if (n == 0) {
        CARD_WIDTH
    } else {
        CARD_WIDTH * n + ARROW_WIDTH * (n - 1)
    }

    Column(
        modifier = Modifier.width(width = statedWidth),
        verticalArrangement = Arrangement.spacedBy(space = 4.dp),
    ) {
        // The file name gets its own row rather than floating above the cards. It used to be positioned
        // over them, which is how it ended up overlapping the controls above the ribbon.
        Text(
            text = group.relativePath,
            style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            group.cards.forEachIndexed { index, card ->
                if (index > 0) {
                    Text(
                        text = "→",
                        style = MaterialTheme.typography.caption,
                        color = TextSecondary,
                        modifier = Modifier.width(width = ARROW_WIDTH),
                    )
                }
                RuleCard(
                    card = card,
                    selected = card.ruleId == selectedRuleId,
                    highlighted = highlighted,
                    onSelect = { onSelectRule(card.ruleId) },
                    onHighlightVariable = onHighlightVariable,
                )
            }
        }
    }
}

/**
 * One rule.
 *
 * Four rows, always: the ordinal and name, what it reads, what it sets, and its flags. A row with a
 * dash says "nothing", which is information; a row that is not drawn says nothing at all and makes the
 * cards different heights.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun RuleCard(
    card: RibbonCard,
    selected: Boolean,
    highlighted: VariableFlow.Flow?,
    onSelect: () -> Unit,
    onHighlightVariable: (String?) -> Unit,
) {
    val lit = highlighted?.touches(ordinal = card.ordinal) == true
    val border = when {
        selected -> PrimaryBlue
        lit -> AccentCyan
        else -> BorderColor
    }

    Column(
        modifier = Modifier
            .width(width = CARD_WIDTH)
            .clip(shape = RoundedCornerShape(size = 6.dp))
            .background(color = if (selected) PrimaryGlow else BgSurface)
            .border(width = 1.dp, color = border, shape = RoundedCornerShape(size = 6.dp))
            .clickable(onClick = onSelect)
            .padding(horizontal = 7.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(space = 3.dp),
    ) {
        CardTitle(card = card)
        ChipRow(
            marker = "↓",
            names = card.reads,
            colour = AccentCyan,
            highlighted = highlighted,
            onHighlightVariable = onHighlightVariable,
        )
        ChipRow(
            marker = "↑",
            names = card.sets,
            colour = AccentOrange,
            highlighted = highlighted,
            onHighlightVariable = onHighlightVariable,
        )
        CardFlags(card = card)
    }
}

/** The ordinal and the rule's name. The name is allowed one line and then ellipsises. */
@Suppress("FunctionNaming")
@Composable
private fun CardTitle(card: RibbonCard) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 5.dp),
    ) {
        Text(
            text = card.ordinal.toString(),
            style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
            color = TextSecondary,
        )
        // weight + a single line is what stops a long rule name pushing the card wider than the width
        // its group stated — the prototype's overlap bug, in the other direction.
        Text(
            text = card.ruleId,
            style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.SemiBold),
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().weight(weight = 1f, fill = false),
        )
    }
}

/**
 * One of the two variable rows.
 *
 * At most [MAX_CHIPS] names, then a `+N`. Uncapped, a rule that sets six variables made its card twice
 * the height of its neighbours, and the ribbon's whole job is comparison across cards.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun ChipRow(
    marker: String,
    names: List<String>,
    colour: Color,
    highlighted: VariableFlow.Flow?,
    onHighlightVariable: (String?) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 3.dp),
    ) {
        Text(
            text = marker,
            style = MaterialTheme.typography.caption,
            color = if (names.isEmpty()) TextSecondary else colour,
        )
        if (names.isEmpty()) {
            Text(text = "—", style = MaterialTheme.typography.caption, color = TextSecondary)
            return@Row
        }
        names.take(n = MAX_CHIPS).forEach { name ->
            VariableChip(
                name = name,
                colour = colour,
                lit = highlighted?.variable == name,
                onHighlightVariable = onHighlightVariable,
            )
        }
        if (names.size > MAX_CHIPS) {
            Text(
                text = "+${names.size - MAX_CHIPS}",
                style = MaterialTheme.typography.caption,
                color = TextSecondary,
            )
        }
    }
}

/** A `$variable`, which lights up its own flow when clicked. */
@Suppress("FunctionNaming")
@Composable
private fun VariableChip(
    name: String,
    colour: Color,
    lit: Boolean,
    onHighlightVariable: (String?) -> Unit,
) {
    Text(
        text = "\$$name",
        style = MaterialTheme.typography.caption,
        color = colour,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(shape = RoundedCornerShape(percent = 50))
            .background(color = colour.copy(alpha = if (lit) 0.32f else 0.14f))
            // Click rather than hover: a hover-driven highlight cannot be reached from the keyboard and
            // cannot be held while the eye travels along the ribbon, which is the whole gesture.
            .clickable(onClick = { onHighlightVariable(if (lit) null else name) })
            .padding(horizontal = 5.dp),
    )
}

/** `⊘` when the rule halts the run, `code only` when the Builder cannot render it. */
@Suppress("FunctionNaming")
@Composable
private fun CardFlags(card: RibbonCard) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 4.dp),
    ) {
        when {
            card.halts -> Text(
                text = "⊘ halts the run",
                style = MaterialTheme.typography.caption,
                color = AccentRed,
            )

            card.locked -> Text(
                text = "code only",
                style = MaterialTheme.typography.caption,
                color = AccentOrange,
            )

            else -> Text(
                text = "—",
                style = MaterialTheme.typography.caption,
                color = TextSecondary,
            )
        }
    }
}

/**
 * The legend, without which the arrows are a guess.
 *
 * The prototype had none, and the first question anyone asked of the ribbon was what the two arrows
 * meant. It costs one line.
 */
@Suppress("FunctionNaming")
@Composable
internal fun RibbonLegend(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(space = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendItem(marker = "↓", text = "reads", colour = AccentCyan)
        LegendItem(marker = "↑", text = "sets", colour = AccentOrange)
        LegendItem(marker = "⊘", text = "halts the run", colour = AccentRed)
        Text(
            text = "left to right is evaluation order",
            style = MaterialTheme.typography.caption,
            color = TextSecondary,
        )
    }
}

@Suppress("FunctionNaming")
@Composable
private fun LegendItem(marker: String, text: String, colour: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(space = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = marker, style = MaterialTheme.typography.caption, color = colour)
        Text(text = text, style = MaterialTheme.typography.caption, color = TextSecondary)
    }
}

/**
 * Card and arrow widths.
 *
 * Fixed, and that is the point: the group's width is computed from them, so they cannot be intrinsic.
 * The card is wide enough for a `$variable` chip pair plus a two-word rule name before ellipsis.
 */
private val CARD_WIDTH: Dp = 152.dp
private val ARROW_WIDTH: Dp = 18.dp

/** Chips per row before the overflow count — see [ChipRow]. */
private const val MAX_CHIPS: Int = 2
