package com.example.search.bootstrap.analytics;

import com.example.search.adapter.out.analytics.SearchEventJpaEntity;
import com.example.search.adapter.out.analytics.SearchEventSpringDataRepository;
import com.example.search.adapter.out.persistence.jpa.SearchClickJpaEntity;
import com.example.search.adapter.out.persistence.jpa.SearchClickSpringDataRepository;
import com.example.search.application.analytics.port.in.QueryAnalyticsUseCase;
import com.example.search.domain.analytics.ClickThroughRate;
import com.example.search.domain.analytics.LatencyPercentiles;
import com.example.search.domain.analytics.QueryStat;
import com.example.search.domain.analytics.SearchEvent;
import com.example.search.domain.event.SearchClick;
import com.example.search.domain.product.ProductId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Analytics adapter + use case 의 H2 통합 — 실제 SQL group-by / count 가 의도대로 동작하는지 검증.
 *
 * <p>{@code memory-search} 프로필 + H2 in-memory — Testcontainers 없이 빠르게.</p>
 */
@SpringBootTest
@ActiveProfiles({"memory-search", "test"})
@TestPropertySource(properties = {
        "search.engine=memory"
})
class SearchEventAnalyticsIntegrationTest {

    private static final Instant FROM = Instant.parse("2026-05-08T00:00:00Z");
    private static final Instant TO   = Instant.parse("2026-05-09T00:00:00Z");

    @Autowired
    QueryAnalyticsUseCase analytics;
    @Autowired
    SearchEventSpringDataRepository events;
    @Autowired
    SearchClickSpringDataRepository clicks;

    @BeforeEach
    void clean() {
        events.deleteAll();
        clicks.deleteAll();
    }

    @Test
    void topQueries_빈도_내림차순으로_반환() {
        save("nike", 5, 50);
        save("nike", 5, 50);
        save("nike", 5, 50);
        save("zoom", 3, 60);
        save("zoom", 3, 60);
        save("aj1", 0, 30);

        List<QueryStat> top = analytics.topQueries(FROM, TO, 10);

        assertThat(top).extracting(QueryStat::keyword).containsExactly("nike", "zoom", "aj1");
        assertThat(top).extracting(QueryStat::count).containsExactly(3L, 2L, 1L);
    }

    @Test
    void zeroResultQueries_resultCount_0만_집계() {
        save("nike", 5, 50);          // 결과 있음 — zero-result 에서 제외.
        save("aj1", 0, 30);
        save("aj1", 0, 30);
        save("덩크하이", 0, 40);

        List<QueryStat> zero = analytics.zeroResultQueries(FROM, TO, 10);

        assertThat(zero).extracting(QueryStat::keyword).containsExactly("aj1", "덩크하이");
        assertThat(zero).extracting(QueryStat::count).containsExactly(2L, 1L);
    }

    @Test
    void latencyPercentiles_샘플_사이즈_누적() {
        for (int i = 1; i <= 100; i++) {
            save("k", 1, i);
        }

        LatencyPercentiles p = analytics.queryLatencyPercentiles(FROM, TO);

        assertThat(p.sampleSize()).isEqualTo(100);
        assertThat(p.p50()).isEqualTo(50);
        assertThat(p.p95()).isEqualTo(95);
        assertThat(p.p99()).isEqualTo(99);
    }

    @Test
    void ctr_분모는_결과_있는_검색만_분자는_클릭() {
        // 5 건 검색 — 4 건은 결과 있음, 1 건은 zero-result.
        save("nike", 1, 50);
        save("nike", 2, 50);
        save("zoom", 3, 60);
        save("aj", 1, 40);
        save("nothing", 0, 30);

        // 2 건 클릭.
        clicks.save(SearchClickJpaEntity.from(new SearchClick(
                "s-1", "u-1", ProductId.of("p-1"), "nike", 1, FROM.plusSeconds(60))));
        clicks.save(SearchClickJpaEntity.from(new SearchClick(
                "s-2", "u-2", ProductId.of("p-2"), "zoom", 1, FROM.plusSeconds(120))));

        ClickThroughRate ctr = analytics.clickThroughRate(FROM, TO);

        assertThat(ctr.searchesWithResults()).isEqualTo(4);
        assertThat(ctr.clicks()).isEqualTo(2);
        assertThat(ctr.rate()).isEqualTo(0.5);
    }

    @Test
    void 구간_밖_이벤트는_제외() {
        Instant before = FROM.minusSeconds(3600);
        Instant after = TO.plusSeconds(3600);
        save("nike", 1, 50, before);
        save("nike", 1, 50, after);
        save("nike", 1, 50, FROM.plusSeconds(60));

        List<QueryStat> top = analytics.topQueries(FROM, TO, 10);
        assertThat(top).hasSize(1);
        assertThat(top.get(0).count()).isEqualTo(1);
    }

    private void save(String keyword, long resultCount, long latency) {
        save(keyword, resultCount, latency, FROM.plusSeconds(60));
    }

    private void save(String keyword, long resultCount, long latency, Instant occurredAt) {
        SearchEvent e = new SearchEvent(
                "search-" + System.nanoTime(), keyword, "u-1",
                resultCount, latency, occurredAt);
        events.save(SearchEventJpaEntity.from(e));
    }
}
