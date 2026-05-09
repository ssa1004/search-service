package com.example.search.adapter.out.analytics;

import com.example.search.adapter.out.persistence.jpa.SearchClickSpringDataRepository;
import com.example.search.domain.analytics.LatencyPercentiles;
import com.example.search.domain.analytics.QueryStat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchEventAnalyticsRepositoryAdapterTest {

    private static final Instant FROM = Instant.parse("2026-05-08T00:00:00Z");
    private static final Instant TO   = Instant.parse("2026-05-09T00:00:00Z");

    @Mock
    SearchEventSpringDataRepository events;
    @Mock
    SearchClickSpringDataRepository clicks;

    @Test
    void topQueries_row를_QueryStat로_매핑() {
        Object[] row1 = new Object[]{"nike", 120L};
        Object[] row2 = new Object[]{"zoom", 90L};
        // List.of(arr) 가 Object[] 단일 vararg 전개로 추론되는 문제 회피 위해 ArrayList 직접 build.
        java.util.ArrayList<Object[]> rows = new java.util.ArrayList<>();
        rows.add(row1);
        rows.add(row2);
        when(events.topKeywords(eq(FROM), eq(TO), any(Pageable.class)))
                .thenReturn(rows);

        SearchEventAnalyticsRepositoryAdapter adapter = new SearchEventAnalyticsRepositoryAdapter(events, clicks);
        List<QueryStat> result = adapter.topQueries(FROM, TO, 10);

        assertThat(result).containsExactly(
                new QueryStat("nike", 120),
                new QueryStat("zoom", 90));
    }

    @Test
    void zeroResultQueries_row를_QueryStat로_매핑() {
        Object[] row = new Object[]{"aj1", 5L};
        java.util.ArrayList<Object[]> rows = new java.util.ArrayList<>();
        rows.add(row);
        when(events.zeroResultKeywords(eq(FROM), eq(TO), any(Pageable.class)))
                .thenReturn(rows);

        SearchEventAnalyticsRepositoryAdapter adapter = new SearchEventAnalyticsRepositoryAdapter(events, clicks);
        assertThat(adapter.zeroResultQueries(FROM, TO, 5))
                .containsExactly(new QueryStat("aj1", 5));
    }

    @Test
    void latencyPercentiles_빈_리스트면_empty_반환() {
        when(events.latenciesAsc(FROM, TO)).thenReturn(List.of());
        SearchEventAnalyticsRepositoryAdapter adapter = new SearchEventAnalyticsRepositoryAdapter(events, clicks);

        LatencyPercentiles p = adapter.latencyPercentiles(FROM, TO);
        assertThat(p.sampleSize()).isZero();
        assertThat(p.p50()).isZero();
        assertThat(p.p99()).isZero();
    }

    @Test
    void latencyPercentiles_100개_정확한_nearest_rank_계산() {
        // 1..100 ms 정렬 — p50 = 50ms, p95 = 95ms, p99 = 99ms (nearest-rank).
        List<Long> latencies = java.util.stream.LongStream.rangeClosed(1, 100).boxed().toList();
        when(events.latenciesAsc(FROM, TO)).thenReturn(latencies);
        SearchEventAnalyticsRepositoryAdapter adapter = new SearchEventAnalyticsRepositoryAdapter(events, clicks);

        LatencyPercentiles p = adapter.latencyPercentiles(FROM, TO);
        assertThat(p.sampleSize()).isEqualTo(100);
        assertThat(p.p50()).isEqualTo(50);
        assertThat(p.p95()).isEqualTo(95);
        assertThat(p.p99()).isEqualTo(99);
    }

    @Test
    void latencyPercentiles_단일_표본이면_모두_같은_값() {
        when(events.latenciesAsc(FROM, TO)).thenReturn(List.of(42L));
        SearchEventAnalyticsRepositoryAdapter adapter = new SearchEventAnalyticsRepositoryAdapter(events, clicks);

        LatencyPercentiles p = adapter.latencyPercentiles(FROM, TO);
        assertThat(p.p50()).isEqualTo(42);
        assertThat(p.p95()).isEqualTo(42);
        assertThat(p.p99()).isEqualTo(42);
        assertThat(p.sampleSize()).isEqualTo(1);
    }

    @Test
    void countSearchesWithResults_위임() {
        when(events.countSearchesWithResults(FROM, TO)).thenReturn(150L);
        SearchEventAnalyticsRepositoryAdapter adapter = new SearchEventAnalyticsRepositoryAdapter(events, clicks);
        assertThat(adapter.countSearchesWithResults(FROM, TO)).isEqualTo(150L);
    }

    @Test
    void countClicks_clicks_repository에_위임() {
        when(clicks.countByOccurredBetween(FROM, TO)).thenReturn(45L);
        SearchEventAnalyticsRepositoryAdapter adapter = new SearchEventAnalyticsRepositoryAdapter(events, clicks);
        assertThat(adapter.countClicks(FROM, TO)).isEqualTo(45L);
    }
}
