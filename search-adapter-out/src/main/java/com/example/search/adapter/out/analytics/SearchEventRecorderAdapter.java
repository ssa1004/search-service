package com.example.search.adapter.out.analytics;

import com.example.search.application.analytics.port.out.SearchEventRecorder;
import com.example.search.domain.analytics.SearchEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * SearchEvent 비동기 INSERT 어댑터.
 *
 * <p>{@code @Async} — bootstrap 의 {@code searchAnalyticsExecutor} 풀에서 처리. 검색 응답 thread
 * 가 분석 INSERT 를 기다리지 않도록 분리.</p>
 *
 * <p>{@code REQUIRES_NEW} — 호출자 (검색) 의 트랜잭션 경계 안에서 실패해도 검색이 롤백되지 않도록.
 * 어차피 @Async 로 분리되어 있어 같은 트랜잭션은 아니지만 명시적 안전장치.</p>
 *
 * <p>실패 시 예외를 호출자에게 전파하지 않는다 — try/catch 로 swallow + 로그.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SearchEventRecorderAdapter implements SearchEventRecorder {

    private final SearchEventSpringDataRepository repository;

    @Override
    @Async("searchAnalyticsExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(SearchEvent event) {
        try {
            repository.save(SearchEventJpaEntity.from(event));
        } catch (RuntimeException e) {
            // 분석 기록 실패는 검색 자체에 영향 없게 swallow. INSERT 부하 / 풀 고갈 / DB outage 등.
            log.warn("SearchEvent INSERT 실패 keyword='{}' err={}", event.keyword(), e.getMessage());
        }
    }
}
