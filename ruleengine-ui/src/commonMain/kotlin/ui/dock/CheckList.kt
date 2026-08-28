package ui.dock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ui.AccentGreen
import ui.AccentOrange
import ui.AccentRed
import ui.BgHover
import ui.PrimaryBlue
import ui.TextMuted
import ui.TextSecondary
import ui.dock.model.DockBadge
import ui.dock.model.DockBadgeKind
import ui.schema.IssueLevel
import ui.schema.SchemaIssue

/**
 * The dock's Checks tab: every verdict, and the row each one is about.
 *
 * **A check names its subject rather than describing it.** Clicking a row selects the declaration the
 * check is about, which is the difference between a panel that reports problems and one that takes you
 * to them — and it is only possible because every issue carries the path or name it belongs to rather
 * than being a sentence.
 *
 * Errors first, then warnings, then notes. Not because notes do not matter, but because a list sorted by
 * where the declaration happens to sit in the file buries the one thing that stops it loading.
 */
@Suppress("FunctionNaming")
@Composable
internal fun CheckList(
    issues: List<SchemaIssue>,
    modifier: Modifier = Modifier,
    /** Selects what the check is about. Null leaves the rows unclickable. */
    onSelect: ((String) -> Unit)? = null,
    /** Shown when there is nothing to report, in the area's own words. */
    allClearText: String = "Nothing to report.",
    /** An extra line above the list — the dock uses it to say the preview is a stale one. */
    notice: String? = null,
) {
    val ordered = issues.sortedBy { issue -> issue.level.ordinal }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(state = rememberScrollState()),
    ) {
        notice?.let { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.caption,
                color = AccentOrange,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        if (ordered.isEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LevelTag(level = null)
                Text(text = allClearText, style = MaterialTheme.typography.caption, color = TextSecondary)
            }
            return@Column
        }
        ordered.forEach { issue ->
            CheckRow(
                issue = issue,
                onSelect = onSelect?.takeIf { issue.path.isNotBlank() }?.let { select ->
                    { select(issue.path) }
                },
            )
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun CheckRow(issue: SchemaIssue, onSelect: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape = RoundedCornerShape(size = 4.dp))
            .background(color = Color.Transparent)
            .then(other = if (onSelect == null) Modifier else Modifier.clickable(onClick = onSelect))
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        LevelTag(level = issue.level)
        Column(modifier = Modifier.weight(weight = 1f)) {
            Text(
                text = issue.message,
                style = MaterialTheme.typography.caption,
                color = TextSecondary,
            )
            if (issue.path.isNotBlank()) {
                Text(
                    text = issue.path,
                    style = MaterialTheme.typography.caption.copy(fontFamily = FontFamily.Monospace),
                    color = TextMuted,
                )
            }
        }
        if (onSelect != null) {
            Text(
                text = "go to row",
                style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
                color = PrimaryBlue,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .clip(shape = RoundedCornerShape(size = 4.dp))
                    .background(color = BgHover)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

/** Null means "nothing wrong", which needs a tag of its own or the all-clear row reads as an error. */
@Suppress("FunctionNaming")
@Composable
private fun LevelTag(level: IssueLevel?) {
    val (text, colour) = when (level) {
        IssueLevel.ERROR -> "error" to AccentRed
        IssueLevel.WARNING -> "warning" to AccentOrange
        IssueLevel.NOTE -> "note" to PrimaryBlue
        null -> "ok" to AccentGreen
    }
    Text(
        text = text,
        style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
        color = colour,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .width(width = LEVEL_WIDTH)
            .clip(shape = RoundedCornerShape(percent = 50))
            .background(color = colour.copy(alpha = TAG_ALPHA))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/**
 * The badge for a tab holding [issues].
 *
 * The count on the tab rather than inside the body, for the reason the panel this replaced already
 * recorded: a problem count nobody can see until they open a panel arrives after the mistake is saved.
 * Errors outrank warnings outrank notes, so a single number never hides the worse of two kinds.
 */
internal fun checksBadge(issues: List<SchemaIssue>): DockBadge {
    val errors = issues.count { issue -> issue.level == IssueLevel.ERROR }
    val warnings = issues.count { issue -> issue.level == IssueLevel.WARNING }
    return when {
        errors > 0 -> DockBadge(text = errors.toString(), kind = DockBadgeKind.ERROR)
        warnings > 0 -> DockBadge(text = warnings.toString(), kind = DockBadgeKind.WARNING)
        issues.isNotEmpty() -> DockBadge(text = issues.size.toString(), kind = DockBadgeKind.INFO)
        else -> DockBadge(text = "ok", kind = DockBadgeKind.OK)
    }
}

private val LEVEL_WIDTH = 52.dp
private const val TAG_ALPHA: Float = 0.16f
