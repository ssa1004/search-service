package com.example.search.bootstrap.health

import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.boot.actuate.health.Status
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.availability.AvailabilityChangeEvent
import org.springframework.boot.availability.ReadinessState
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicReference

/**
 * ES 헬스를 주기적으로 sampling 하여 Spring Boot 의 readiness state 를 갱신 — 검색 서비스에서 ES 는
 * 실질적 핵심이라 ES DOWN 이면 traffic 을 받지 않도록 K8s 에 알린다 (ADR-0010).
 *
 * 전략:
 *  - Kafka 일시 단절은 unready 사유 아님 — CDC consumer 는 본인 retry 로 회복.
 *  - ES ping 실패가 *연속 N회* 면 `REFUSING_TRAFFIC` 으로 publish — flap 방지.
 *  - ping 성공이 *연속 1회* 면 즉시 `ACCEPTING_TRAFFIC` 복귀 — 회복 빠르게.
 *
 * memory 모드에서는 ES indicator 가 없으므로 본 빈도 비활성.
 */
@Component
@ConditionalOnProperty(name = ["search.engine"], havingValue = "elasticsearch", matchIfMissing = true)
class ApplicationReadinessCoordinator(
    private val engineHealth: SearchEngineHealthIndicator,
    private val publisher: ApplicationEventPublisher,
    meterRegistry: MeterRegistry,
) {

    private val currentState: AtomicReference<ReadinessState> =
        AtomicReference(ReadinessState.ACCEPTING_TRAFFIC)
    private var consecutiveFailures: Int = 0

    init {
        // 게이지로 현재 상태 노출 — 1=accepting, 0=refusing.
        meterRegistry.gauge("application.readiness.accepting", currentState) { ref ->
            if (ref.get() == ReadinessState.ACCEPTING_TRAFFIC) 1.0 else 0.0
        }
    }

    /**
     * 5초마다 ES ping. K8s 의 readinessProbe periodSeconds=5 와 같은 주기로 — probe 와 sampling 의
     * 위상차로 인한 추가 지연 없게.
     */
    @Scheduled(fixedDelay = 5_000L, initialDelay = 5_000L)
    fun poll() {
        val health = engineHealth.health()
        if (Status.UP == health.status) {
            consecutiveFailures = 0
            transitionTo(ReadinessState.ACCEPTING_TRAFFIC)
        } else {
            consecutiveFailures++
            if (consecutiveFailures >= FAIL_THRESHOLD) {
                transitionTo(ReadinessState.REFUSING_TRAFFIC)
            }
        }
    }

    private fun transitionTo(next: ReadinessState) {
        val prev = currentState.getAndSet(next)
        if (prev != next) {
            log.warn("readiness state 전환 {} -> {} (ES 헬스 기반)", prev, next)
            AvailabilityChangeEvent.publish(publisher, this, next)
        }
    }

    companion object {
        /** 연속 N회 fail 이어야 traffic 차단 — flap (ES 의 짧은 GC pause 등) 방어. */
        private const val FAIL_THRESHOLD = 3
        private val log = LoggerFactory.getLogger(ApplicationReadinessCoordinator::class.java)
    }
}
