package com.example.search.adapter.out.analytics

import com.example.search.adapter.out.persistence.jpa.SearchClickSpringDataRepository
import com.example.search.application.analytics.port.out.SearchEventAnalyticsRepository
import com.example.search.domain.analytics.LatencyPercentiles
import com.example.search.domain.analytics.QueryStat
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import java.time.Instant
import kotlin.math.ceil

@Component
class SearchEventAnalyticsRepositoryAdapter(
    private val events: SearchEventSpringDataRepository,
    private val clicks: SearchClickSpringDataRepository
) : SearchEventAnalyticsRepository {

    override fun topQueries(from: Instant, to: Instant, limit: Int): List<QueryStat> =
        events.topKeywords(from, to, PageRequest.of(0, limit))
            .map { row -> QueryStat(row[0] as String, (row[1] as Number).toLong()) }

    override fun zeroResultQueries(from: Instant, to: Instant, limit: Int): List<QueryStat> =
        events.zeroResultKeywords(from, to, PageRequest.of(0, limit))
            .map { row -> QueryStat(row[0] as String, (row[1] as Number).toLong()) }

    override fun latencyPercentiles(from: Instant, to: Instant): LatencyPercentiles {
        val sortedAsc = events.latenciesAsc(from, to)
        if (sortedAsc.isEmpty()) {
            return LatencyPercentiles.empty()
        }
        val p50 = percentile(sortedAsc, 0.50)
        val p95 = percentile(sortedAsc, 0.95)
        val p99 = percentile(sortedAsc, 0.99)
        return LatencyPercentiles(p50, p95, p99, sortedAsc.size.toLong())
    }

    override fun countSearchesWithResults(from: Instant, to: Instant): Long =
        events.countSearchesWithResults(from, to)

    override fun countClicks(from: Instant, to: Instant): Long =
        clicks.countByOccurredBetween(from, to)

    /**
     * 정렬된 list 에서 nearest-rank 방식 percentile — Excel/Numpy 의 numpy.percentile(interpolation=
     * 'nearest') 와 동일. 표본이 작을 때 보간 (linear) 보다 직관적.
     */
    private fun percentile(sortedAsc: List<Long>, p: Double): Long {
        val n = sortedAsc.size
        // ceil(p * n) - 1 — 1-based ceil → 0-based index. n>0 보장됨.
        var idx = ceil(p * n).toInt() - 1
        if (idx < 0) idx = 0
        if (idx >= n) idx = n - 1
        return sortedAsc[idx]
    }
}
