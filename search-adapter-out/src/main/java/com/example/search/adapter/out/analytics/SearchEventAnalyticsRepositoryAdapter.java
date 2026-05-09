package com.example.search.adapter.out.analytics;

import com.example.search.adapter.out.persistence.jpa.SearchClickSpringDataRepository;
import com.example.search.application.analytics.port.out.SearchEventAnalyticsRepository;
import com.example.search.domain.analytics.LatencyPercentiles;
import com.example.search.domain.analytics.QueryStat;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SearchEventAnalyticsRepositoryAdapter implements SearchEventAnalyticsRepository {

    private final SearchEventSpringDataRepository events;
    private final SearchClickSpringDataRepository clicks;

    @Override
    public List<QueryStat> topQueries(Instant from, Instant to, int limit) {
        return events.topKeywords(from, to, PageRequest.of(0, limit)).stream()
                .map(row -> new QueryStat((String) row[0], ((Number) row[1]).longValue()))
                .toList();
    }

    @Override
    public List<QueryStat> zeroResultQueries(Instant from, Instant to, int limit) {
        return events.zeroResultKeywords(from, to, PageRequest.of(0, limit)).stream()
                .map(row -> new QueryStat((String) row[0], ((Number) row[1]).longValue()))
                .toList();
    }

    @Override
    public LatencyPercentiles latencyPercentiles(Instant from, Instant to) {
        List<Long> sortedAsc = events.latenciesAsc(from, to);
        if (sortedAsc.isEmpty()) {
            return LatencyPercentiles.empty();
        }
        long p50 = percentile(sortedAsc, 0.50);
        long p95 = percentile(sortedAsc, 0.95);
        long p99 = percentile(sortedAsc, 0.99);
        return new LatencyPercentiles(p50, p95, p99, sortedAsc.size());
    }

    @Override
    public long countSearchesWithResults(Instant from, Instant to) {
        return events.countSearchesWithResults(from, to);
    }

    @Override
    public long countClicks(Instant from, Instant to) {
        return clicks.countByOccurredBetween(from, to);
    }

    /**
     * 정렬된 list 에서 nearest-rank 방식 percentile — Excel/Numpy 의 numpy.percentile(interpolation=
     * 'nearest') 와 동일. 표본이 작을 때 보간 (linear) 보다 직관적.
     */
    private long percentile(List<Long> sortedAsc, double p) {
        int n = sortedAsc.size();
        // (int) Math.ceil(p * n) - 1 — 1-based ceil → 0-based index. n>0 보장됨.
        int idx = (int) Math.ceil(p * n) - 1;
        if (idx < 0) idx = 0;
        if (idx >= n) idx = n - 1;
        return sortedAsc.get(idx);
    }
}
