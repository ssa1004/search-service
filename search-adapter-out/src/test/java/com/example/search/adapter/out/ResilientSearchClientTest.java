package com.example.search.adapter.out;

import com.example.search.adapter.out.elasticsearch.ResilientSearchClient;
import com.example.search.application.command.SearchProductCommand;
import com.example.search.application.port.out.SearchEnginePort;
import com.example.search.domain.facet.FacetSpec;
import com.example.search.domain.index.BoostRule;
import com.example.search.domain.query.Page;
import com.example.search.domain.query.SearchQuery;
import com.example.search.domain.query.SearchResult;
import com.example.search.domain.suggest.AutocompleteSuggestion;
import com.example.search.domain.suggest.RelatedSuggestion;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ResilientSearchClient 단위 테스트 — Retry → CB chain 의 핵심 동작 검증.
 *
 * <p>두 가지 검증:</p>
 * <ol>
 *   <li>일시 fail 후 성공 — Retry 가 흡수.</li>
 *   <li>지속 fail 로 CB OPEN → 이후 호출이 즉시 실패 (느린 retry 안 들어감).</li>
 * </ol>
 */
class ResilientSearchClientTest {

    private static final SearchProductCommand DUMMY_COMMAND = new SearchProductCommand(
            SearchQuery.byKeyword("test", new Page(0, 10)),
            List.<FacetSpec>of(),
            BoostRule.defaults()
    );

    @Test
    void Retry_가_일시_fail_을_흡수하고_성공한다() {
        AtomicInteger calls = new AtomicInteger();
        SearchEnginePort flaky = new StubPort() {
            @Override
            public SearchResult search(SearchProductCommand command) {
                int n = calls.incrementAndGet();
                if (n < 3) {
                    throw new RuntimeException("일시 IO");
                }
                return new SearchResult(0L, 1L, List.of(), List.of());
            }
        };

        ResilientSearchClient client = new ResilientSearchClient(
                flaky,
                Retry.of("test", RetryConfig.custom()
                        .maxAttempts(3)
                        .waitDuration(Duration.ofMillis(1))
                        .build()),
                neverOpenCb()
        );

        SearchResult result = client.search(DUMMY_COMMAND);

        assertThat(result).isNotNull();
        assertThat(calls.get()).isEqualTo(3);    // 2번 fail + 1번 success.
    }

    @Test
    void CB_가_OPEN_되면_이후_호출은_즉시_차단된다() {
        AtomicInteger calls = new AtomicInteger();
        SearchEnginePort alwaysFail = new StubPort() {
            @Override
            public SearchResult search(SearchProductCommand command) {
                calls.incrementAndGet();
                throw new RuntimeException("ES down");
            }
        };

        // CB: minimumNumberOfCalls=4, slidingWindow=4, failureRate=50% — 4번 fail 후 OPEN.
        CircuitBreaker cb = CircuitBreaker.of("test", CircuitBreakerConfig.custom()
                .slidingWindowSize(4)
                .minimumNumberOfCalls(4)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMinutes(1))
                .build());

        ResilientSearchClient client = new ResilientSearchClient(
                alwaysFail,
                Retry.of("test", RetryConfig.custom().maxAttempts(1).build()),     // retry 비활성으로 단순화.
                cb
        );

        // 4회 fail 후 CB OPEN.
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> client.search(DUMMY_COMMAND))
                    .isInstanceOf(RuntimeException.class);
        }
        assertThat(calls.get()).isEqualTo(4);
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // 5번째는 delegate 호출 안 되고 즉시 CallNotPermitted.
        assertThatThrownBy(() -> client.search(DUMMY_COMMAND))
                .isInstanceOf(CallNotPermittedException.class);
        assertThat(calls.get()).isEqualTo(4);    // delegate 호출 변화 없음.
    }

    private static CircuitBreaker neverOpenCb() {
        return CircuitBreaker.of("never-open", CircuitBreakerConfig.custom()
                .slidingWindowSize(100)
                .minimumNumberOfCalls(100)
                .failureRateThreshold(99)
                .build());
    }

    /**
     * 테스트용 stub — search 만 override 하고 나머지는 throw.
     */
    private static class StubPort implements SearchEnginePort {
        @Override
        public SearchResult search(SearchProductCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<AutocompleteSuggestion> autocomplete(String prefix, int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<RelatedSuggestion> findRelatedKeywords(String keyword, int limit, int maxDistance) {
            throw new UnsupportedOperationException();
        }
    }
}
