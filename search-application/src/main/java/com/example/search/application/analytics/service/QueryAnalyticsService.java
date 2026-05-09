package com.example.search.application.analytics.service;

import com.example.search.application.analytics.port.in.QueryAnalyticsUseCase;
import com.example.search.application.analytics.port.out.SearchEventAnalyticsRepository;
import com.example.search.domain.analytics.ClickThroughRate;
import com.example.search.domain.analytics.LatencyPercentiles;
import com.example.search.domain.analytics.QueryStat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class QueryAnalyticsService implements QueryAnalyticsUseCase {

    /** 한 호출에서 반환할 수 있는 최대 row 수 — 화면 표시 + 응답 크기 보호. */
    private static final int MAX_LIMIT = 100;

    private final SearchEventAnalyticsRepository repository;

    @Override
    @Transactional(readOnly = true)
    public List<QueryStat> topQueries(Instant from, Instant to, int limit) {
        validateRange(from, to);
        int safeLimit = clampLimit(limit);
        return repository.topQueries(from, to, safeLimit);
    }

    @Override
    @Transactional(readOnly = true)
    public List<QueryStat> zeroResultQueries(Instant from, Instant to, int limit) {
        validateRange(from, to);
        int safeLimit = clampLimit(limit);
        return repository.zeroResultQueries(from, to, safeLimit);
    }

    @Override
    @Transactional(readOnly = true)
    public LatencyPercentiles queryLatencyPercentiles(Instant from, Instant to) {
        validateRange(from, to);
        return repository.latencyPercentiles(from, to);
    }

    @Override
    @Transactional(readOnly = true)
    public ClickThroughRate clickThroughRate(Instant from, Instant to) {
        validateRange(from, to);
        long searches = repository.countSearchesWithResults(from, to);
        long clicks = repository.countClicks(from, to);
        return ClickThroughRate.of(searches, clicks);
    }

    private void validateRange(Instant from, Instant to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("from / to 모두 필수");
        }
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("from < to 만족 필요: from=" + from + " to=" + to);
        }
    }

    private int clampLimit(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit 1 이상 필요: " + limit);
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
