package com.example.search.adapter.out.elasticsearch

import kotlin.math.min

/**
 * Levenshtein 거리 계산 — fuzzy match 결과의 실제 편집 거리를 RelatedSuggestion 에 채워 정렬에
 * 활용한다.
 *
 * 공통 구현 (Apache Commons Text 와 같음). 외부 의존 없이 짧으니 직접 구현.
 */
object LevenshteinDistance {

    @JvmStatic
    fun compute(a: String?, b: String?): Int {
        val s = a ?: ""
        val t = b ?: ""
        val n = s.length
        val m = t.length
        if (n == 0) return m
        if (m == 0) return n

        var prev = IntArray(m + 1) { it }
        var curr = IntArray(m + 1)

        for (i in 1..n) {
            curr[0] = i
            for (j in 1..m) {
                val cost = if (s[i - 1] == t[j - 1]) 0 else 1
                curr[j] = min(
                    min(curr[j - 1] + 1, prev[j] + 1),
                    prev[j - 1] + cost
                )
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[m]
    }
}
