package com.example.search.application.analytics;

import com.example.search.application.analytics.port.out.SearchEventAnalyticsRepository;
import com.example.search.application.analytics.service.QueryAnalyticsService;
import com.example.search.domain.analytics.ClickThroughRate;
import com.example.search.domain.analytics.LatencyPercentiles;
import com.example.search.domain.analytics.QueryStat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryAnalyticsServiceTest {

    private static final Instant FROM = Instant.parse("2026-05-08T00:00:00Z");
    private static final Instant TO   = Instant.parse("2026-05-09T00:00:00Z");

    @Mock
    SearchEventAnalyticsRepository repository;

    @Test
    void topQueries_정상_위임() {
        List<QueryStat> stats = List.of(new QueryStat("nike", 120), new QueryStat("zoom", 90));
        when(repository.topQueries(FROM, TO, 10)).thenReturn(stats);
        QueryAnalyticsService service = new QueryAnalyticsService(repository);

        assertThat(service.topQueries(FROM, TO, 10)).isEqualTo(stats);
    }

    @Test
    void zeroResultQueries_정상_위임() {
        when(repository.zeroResultQueries(any(), any(), anyInt()))
                .thenReturn(List.of(new QueryStat("aj1", 10)));
        QueryAnalyticsService service = new QueryAnalyticsService(repository);

        assertThat(service.zeroResultQueries(FROM, TO, 5))
                .extracting(QueryStat::keyword)
                .containsExactly("aj1");
    }

    @Test
    void latencyPercentiles_정상_위임() {
        when(repository.latencyPercentiles(FROM, TO))
                .thenReturn(new LatencyPercentiles(50, 200, 500, 1234));
        QueryAnalyticsService service = new QueryAnalyticsService(repository);

        LatencyPercentiles p = service.queryLatencyPercentiles(FROM, TO);
        assertThat(p.p99()).isEqualTo(500);
        assertThat(p.sampleSize()).isEqualTo(1234);
    }

    @Test
    void ctr_분모_0이면_rate_0() {
        when(repository.countSearchesWithResults(FROM, TO)).thenReturn(0L);
        when(repository.countClicks(FROM, TO)).thenReturn(5L);
        QueryAnalyticsService service = new QueryAnalyticsService(repository);

        ClickThroughRate ctr = service.clickThroughRate(FROM, TO);
        assertThat(ctr.rate()).isEqualTo(0.0);
    }

    @Test
    void ctr_정상_계산() {
        when(repository.countSearchesWithResults(FROM, TO)).thenReturn(200L);
        when(repository.countClicks(FROM, TO)).thenReturn(50L);
        QueryAnalyticsService service = new QueryAnalyticsService(repository);

        ClickThroughRate ctr = service.clickThroughRate(FROM, TO);
        assertThat(ctr.rate()).isEqualTo(0.25);
        assertThat(ctr.searchesWithResults()).isEqualTo(200);
    }

    @Test
    void from_to_역전시_예외() {
        QueryAnalyticsService service = new QueryAnalyticsService(repository);
        assertThatThrownBy(() -> service.topQueries(TO, FROM, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("from < to");
    }

    @Test
    void limit_0_이하_예외() {
        QueryAnalyticsService service = new QueryAnalyticsService(repository);
        assertThatThrownBy(() -> service.topQueries(FROM, TO, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
    }

    @Test
    void limit_상한_clamp() {
        when(repository.topQueries(any(), any(), anyInt())).thenReturn(List.of());
        QueryAnalyticsService service = new QueryAnalyticsService(repository);
        service.topQueries(FROM, TO, 10000);

        // service 가 100 으로 자름 — repository 호출 인자 확인.
        verify(repository).topQueries(FROM, TO, 100);
    }
}
