package com.example.search.application.analytics.service

import com.example.search.application.analytics.port.`in`.QueryAnalyticsUseCase
import com.example.search.application.analytics.port.out.SearchEventAnalyticsRepository
import com.example.search.domain.analytics.ClickThroughRate
import com.example.search.domain.analytics.LatencyPercentiles
import com.example.search.domain.analytics.QueryStat
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class QueryAnalyticsService(
    private val repository: SearchEventAnalyticsRepository
) : QueryAnalyticsUseCase {

    @Transactional(readOnly = true)
    override fun topQueries(from: Instant, to: Instant, limit: Int): List<QueryStat> {
        validateRange(from, to)
        val safeLimit = clampLimit(limit)
        return repository.topQueries(from, to, safeLimit)
    }

    @Transactional(readOnly = true)
    override fun zeroResultQueries(from: Instant, to: Instant, limit: Int): List<QueryStat> {
        validateRange(from, to)
        val safeLimit = clampLimit(limit)
        return repository.zeroResultQueries(from, to, safeLimit)
    }

    @Transactional(readOnly = true)
    override fun queryLatencyPercentiles(from: Instant, to: Instant): LatencyPercentiles {
        validateRange(from, to)
        return repository.latencyPercentiles(from, to)
    }

    @Transactional(readOnly = true)
    override fun clickThroughRate(from: Instant, to: Instant): ClickThroughRate {
        validateRange(from, to)
        val searches = repository.countSearchesWithResults(from, to)
        val clicks = repository.countClicks(from, to)
        return ClickThroughRate.of(searches, clicks)
    }

    private fun validateRange(from: Instant?, to: Instant?) {
        if (from == null || to == null) {
            throw IllegalArgumentException("from / to 모두 필수")
        }
        if (!from.isBefore(to)) {
            throw IllegalArgumentException("from < to 만족 필요: from=$from to=$to")
        }
    }

    private fun clampLimit(limit: Int): Int {
        if (limit <= 0) {
            throw IllegalArgumentException("limit 1 이상 필요: $limit")
        }
        return minOf(limit, MAX_LIMIT)
    }

    companion object {
        /** 한 호출에서 반환할 수 있는 최대 row 수 — 화면 표시 + 응답 크기 보호. */
        private const val MAX_LIMIT: Int = 100
    }
}
