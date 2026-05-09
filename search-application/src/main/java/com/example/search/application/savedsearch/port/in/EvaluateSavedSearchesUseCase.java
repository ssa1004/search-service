package com.example.search.application.savedsearch.port.in;

/**
 * 모든 active SavedSearch 평가 — 5분 주기 스케줄러가 호출.
 *
 * <p>구현체는 ShedLock 으로 멀티 인스턴스 중복 실행 방지. 평가 결과는 {@code SavedSearchAlertPublisher}
 * 로 발행.</p>
 */
public interface EvaluateSavedSearchesUseCase {

    /**
     * @return 평가한 SavedSearch 수 (active 만 카운트)
     */
    int evaluateAll();
}
