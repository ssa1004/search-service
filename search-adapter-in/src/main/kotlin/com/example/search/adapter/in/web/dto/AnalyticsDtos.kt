package com.example.search.adapter.`in`.web.dto

/**
 * 분석 admin REST DTO 모음. 도메인 record 와 분리해 응답 schema 안정성 확보.
 */
object AnalyticsDtos {

    @JvmRecord
    data class QueryStatDto(val keyword: String, val count: Long)

    @JvmRecord
    data class TopQueriesResponse(val queries: List<QueryStatDto>)

    @JvmRecord
    data class LatencyResponse(val p50: Long, val p95: Long, val p99: Long, val sampleSize: Long)

    @JvmRecord
    data class CtrResponse(val searchesWithResults: Long, val clicks: Long, val rate: Double)
}
