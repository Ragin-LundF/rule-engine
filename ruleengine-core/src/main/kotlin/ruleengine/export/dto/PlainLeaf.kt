package ruleengine.export.dto

/**
 * One comparison, already written as a sentence — "Declared Value is between 1000 and 25000".
 *
 * The whole sentence is a single string: splitting it into subject, verb and value would let a
 * renderer emphasise the value, but the phrasing varies enough by operator (`between` has two
 * values, `in` has a list, an aggregate comparison has an expression on both sides) that the parts
 * would not line up across them.
 */
data class PlainLeaf(val text: String) : PlainCondition
