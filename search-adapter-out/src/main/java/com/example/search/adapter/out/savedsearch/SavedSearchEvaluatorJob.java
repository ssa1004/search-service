package com.example.search.adapter.out.savedsearch;

import com.example.search.application.savedsearch.port.in.EvaluateSavedSearchesUseCase;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * SavedSearch 5분 평가 스케줄러 — 멀티 인스턴스에서 ShedLock 으로 한 인스턴스만 실행.
 *
 * <p>{@code lockAtMostFor=4m} — 다음 5분 사이클 전에 lock 자동 만료, 인스턴스 crash 후에도 다음
 * 인스턴스가 빠르게 이어받음. {@code lockAtLeastFor=1m} — 매우 짧게 끝나도 1분간은 lock 보유해
 * polling drift 로 인한 중복 실행 방지.</p>
 *
 * <p>실패 시 metric {@code savedsearch.evaluator.failures} 증가 — 운영 알람 hook.</p>
 *
 * <p>빈 등록은 bootstrap 의 {@code SavedSearchConfig} 가 책임 — {@link EvaluateSavedSearchesUseCase}
 * 빈이 존재할 때만 활성 (memory 모드 / kafka 비활성 환경에서 미동작).</p>
 */
@RequiredArgsConstructor
@Slf4j
public class SavedSearchEvaluatorJob {

    private final EvaluateSavedSearchesUseCase useCase;
    private final MeterRegistry meterRegistry;

    @Scheduled(fixedDelayString = "${search.savedsearch.evaluate-interval-ms:300000}",
            initialDelayString = "${search.savedsearch.evaluate-initial-delay-ms:60000}")
    @SchedulerLock(name = "savedsearch-evaluator", lockAtMostFor = "4m", lockAtLeastFor = "1m")
    public void run() {
        try {
            int evaluated = useCase.evaluateAll();
            meterRegistry.counter("savedsearch.evaluator.evaluated").increment(evaluated);
        } catch (RuntimeException e) {
            meterRegistry.counter("savedsearch.evaluator.failures").increment();
            log.error("SavedSearch 평가 사이클 실패: {}", e.getMessage(), e);
        }
    }
}
