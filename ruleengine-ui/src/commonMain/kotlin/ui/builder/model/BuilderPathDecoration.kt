package ui.builder.model

/**
 * Something applied to a path segment after the member has been read: a restriction, or a bound on
 * how many elements are kept.
 *
 * Held as an **ordered** list rather than as separate fields, because the order is the meaning:
 * `take(orders, 3)[paid == true]` slices first and selects paid orders among those three, while
 * `take(orders[paid == true], 3)` selects paid orders first and slices those. A flat `filters` list
 * with a slice beside it could not tell the two apart.
 */
sealed interface BuilderPathDecoration {

    /** One `[field op value]` restriction. */
    data class Filter(val filter: BuilderFilter) : BuilderPathDecoration

    /**
     * A bound on how many elements are kept — `take(path, n)` or, with [fromEnd], `takeLast`.
     *
     * [count] stays text so a half-typed number does not have to be rejected mid-keystroke; the
     * engine's validator has the last word on what is a valid size.
     */
    data class Slice(val fromEnd: Boolean, val count: String) : BuilderPathDecoration
}
