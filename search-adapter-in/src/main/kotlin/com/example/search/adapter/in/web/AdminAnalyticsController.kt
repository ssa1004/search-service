package com.example.search.adapter.`in`.web

import com.example.search.adapter.`in`.web.dto.AnalyticsDtos
import com.example.search.application.analytics.port.`in`.QueryAnalyticsUseCase
import com.example.search.domain.analytics.QueryStat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/**
 * 운영자 검색 분석 admin endpoint (ADR-0018).
 *
 * 모든 endpoint 는 운영 보안 게이트 (네트워크 분리 또는 인증 미들웨어) 뒤에 둔다. 시간 구간은
 * ISO-8601 instant 로 받음 — 운영 화면이 dashboard / chart 라이브러리와 직접 연동.
 */
@RestController
@RequestMapping("/api/v1/admin/analytics")
class AdminAnalyticsController(
    private val analytics: QueryAnalyticsUseCase
) {

    @GetMapping("/queries/top")
    fun top(
        @RequestParam("from") from: Instant,
        @RequestParam("to") to: Instant,
        @RequestParam(value = "limit", defaultValue = "" + DEFAULT_LIMIT) limit: Int
    ): AnalyticsDtos.TopQueriesResponse {
        val stats = analytics.topQueries(from, to, limit)
        return AnalyticsDtos.TopQueriesResponse(toDtos(stats))
    }

    @GetMapping("/queries/zero-result")
    fun zeroResult(
        @RequestParam("from") from: Instant,
        @RequestParam("to") to: Instant,
        @RequestParam(value = "limit", defaultValue = "" + DEFAULT_LIMIT) limit: Int
    ): AnalyticsDtos.TopQueriesResponse {
        val stats = analytics.zeroResultQueries(from, to, limit)
        return AnalyticsDtos.TopQueriesResponse(toDtos(stats))
    }

    @GetMapping("/latency")
    fun latency(
        @RequestParam("from") from: Instant,
        @RequestParam("to") to: Instant
    ): AnalyticsDtos.LatencyResponse {
        val p = analytics.queryLatencyPercentiles(from, to)
        return AnalyticsDtos.LatencyResponse(p.p50, p.p95, p.p99, p.sampleSize)
    }

    @GetMapping("/ctr")
    fun ctr(
        @RequestParam("from") from: Instant,
        @RequestParam("to") to: Instant
    ): AnalyticsDtos.CtrResponse {
        val ctr = analytics.clickThroughRate(from, to)
        return AnalyticsDtos.CtrResponse(
            ctr.searchesWithResults, ctr.clicks, ctr.rate
        )
    }

    private fun toDtos(stats: List<QueryStat>): List<AnalyticsDtos.QueryStatDto> =
        stats.map { AnalyticsDtos.QueryStatDto(it.keyword, it.count) }

    companion object {
        /** 한 호출 default limit — 운영 화면의 일반적인 top N. */
        private const val DEFAULT_LIMIT: Int = 20
    }
}
