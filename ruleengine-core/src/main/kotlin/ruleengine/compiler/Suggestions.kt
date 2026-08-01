package ruleengine.compiler

import ruleengine.compiler.Suggestions.DEFAULT_MAX_DISTANCE


/**
 * "Did you mean …?" for an identifier that did not resolve.
 *
 * Edit distance over the lowercased strings, so a wrong case or a single typo still suggests the
 * field the author meant. Nothing is suggested beyond [DEFAULT_MAX_DISTANCE] edits, where the
 * candidate would be more confusing than no suggestion at all.
 */
internal object Suggestions {

    private const val DEFAULT_MAX_DISTANCE = 3

    internal fun suggestClosest(
        input: String,
        candidates: List<String>,
        maxDistance: Int = DEFAULT_MAX_DISTANCE,
    ): String? {
        var best: String? = null
        var bestDist = Int.MAX_VALUE
        for (c in candidates) {
            val d = levenshtein(a = input.lowercase(), b = c.lowercase())
            if (d < bestDist) {
                bestDist = d
                best = c
            }
        }
        return if (bestDist <= maxDistance) best else null
    }

    internal fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val aLen = a.length
        val bLen = b.length
        val dp = Array(aLen + 1) { IntArray(bLen + 1) }
        for (i in 0..aLen) dp[i][0] = i
        for (j in 0..bLen) dp[0][j] = j
        for (i in 1..aLen) {
            for (j in 1..bLen) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[aLen][bLen]
    }
}
