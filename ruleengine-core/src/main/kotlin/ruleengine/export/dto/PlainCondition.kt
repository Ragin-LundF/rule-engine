package ruleengine.export.dto

/**
 * A rule's condition restated for a reader who has never seen the DSL.
 *
 * A tree rather than a finished string, because the boolean structure is the part a business reader
 * most needs to see and every output format shows it differently — Markdown as a nested bullet list,
 * Word as an indented numbered list. Flattening it to text here would force each renderer to parse
 * the sentence back apart.
 */
sealed interface PlainCondition
