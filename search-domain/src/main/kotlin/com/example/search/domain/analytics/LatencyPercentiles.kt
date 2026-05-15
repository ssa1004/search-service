package com.example.search.domain.analytics

/**
 * 분석 구간의 검색 응답 latency 분포 — ms 단위.
 *
 * [sampleSize] 는 통계 신뢰도 — 1k 미만이면 p99 가 흔들리므로 운영자가 표시 여부를 판단.
 */
@JvmRecord
data class LatencyPercentiles(
    val p50: Long,
    val p95: Long,
    val p99: Long,
    val sampleSize: Long
) {
    init {
        require(p50 >= 0 && p95 >= 0 && p99 >= 0) { "percentile 음수 불가" }
        require(sampleSize >= 0) { "sampleSize 음수 불가" }
    }

    companion object {
        @JvmStatic
        fun empty(): LatencyPercentiles = LatencyPercentiles(0, 0, 0, 0)
    }
}
