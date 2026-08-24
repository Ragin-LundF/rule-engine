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

    /**
     * An ordering over the elements — `sortBy(path, asc|desc)`, or `sortBy(path, "member", asc|desc)`
     * when the elements are structures.
     *
     * Ordered beside the others for the same reason a slice is: `take(sortBy(orders, "total", desc),
     * 3)` is the three largest orders, while `sortBy(take(orders, 3), "total", desc)` is the first
     * three orders put in order. Only the position in [BuilderPathStep.decorations] tells them apart.
     *
     * [member] stays text so a half-typed name does not have to be rejected mid-keystroke; the
     * engine's validator has the last word on which members can be ordered by.
     */
    data class Sort(val member: String?, val descending: Boolean) : BuilderPathDecoration
}
