package com.example.search.domain.analytics

/**
 * keyword 별 집계 한 행 — top queries / zero result query 양쪽이 같은 모양.
 *
 * [count] 는 일반 top 에서는 검색 횟수, zero-result top 에서는 결과 0건이 났던 횟수.
 */
@JvmRecord
data class QueryStat(
    val keyword: String,
    val count: Long
) {
    init {
        require(count >= 0) { "count 음수 불가: $count" }
    }
}
