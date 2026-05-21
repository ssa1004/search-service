package com.example.search.adapter.out.savedsearch

import com.example.search.application.savedsearch.port.`in`.EvaluateSavedSearchesUseCase
import io.micrometer.core.instrument.MeterRegistry
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled

/**
 * SavedSearch 5분 평가 스케줄러 — 멀티 인스턴스에서 ShedLock 으로 한 인스턴스만 실행.
 *
 * `lockAtMostFor=4m` — 다음 5분 사이클 전에 lock 자동 만료, 인스턴스 crash 후에도 다음
 * 인스턴스가 빠르게 이어받음. `lockAtLeastFor=1m` — 매우 짧게 끝나도 1분간은 lock 보유해
 * polling drift 로 인한 중복 실행 방지.
 *
 * 실패 시 metric `savedsearch.evaluator.failures` 증가 — 운영 알람 hook.
 *
 * 빈 등록은 bootstrap 의 `SavedSearchConfig` 가 책임 — [EvaluateSavedSearchesUseCase]
 * 빈이 존재할 때만 활성 (memory 모드 / kafka 비활성 환경에서 미동작).
 */
// open: CGLIB AOP proxy 대상 (@Scheduled/@SchedulerLock) — @Bean 등록이라 클래스 레벨
// 스테레오타입이 없어 plugin.spring allOpen 이 open 처리하지 않는다. Kotlin 클래스는 기본 final.
open class SavedSearchEvaluatorJob(
    private val useCase: EvaluateSavedSearchesUseCase,
    private val meterRegistry: MeterRegistry
) {

    @Scheduled(
        fixedDelayString = "\${search.savedsearch.evaluate-interval-ms:300000}",
        initialDelayString = "\${search.savedsearch.evaluate-initial-delay-ms:60000}"
    )
    @SchedulerLock(name = "savedsearch-evaluator", lockAtMostFor = "4m", lockAtLeastFor = "1m")
    // open: 인터페이스 override 가 아니므로 명시적으로 open — final 메서드는 CGLIB 가 가로채지 못한다.
    open fun run() {
        try {
            val evaluated = useCase.evaluateAll()
            meterRegistry.counter("savedsearch.evaluator.evaluated").increment(evaluated.toDouble())
        } catch (e: RuntimeException) {
            meterRegistry.counter("savedsearch.evaluator.failures").increment()
            log.error("SavedSearch 평가 사이클 실패: {}", e.message, e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(SavedSearchEvaluatorJob::class.java)
    }
}
