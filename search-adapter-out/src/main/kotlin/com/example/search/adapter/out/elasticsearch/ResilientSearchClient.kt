package com.example.search.adapter.out.elasticsearch

import com.example.search.application.command.SearchProductCommand
import com.example.search.application.port.out.SearchEnginePort
import com.example.search.domain.query.SearchResult
import com.example.search.domain.suggest.AutocompleteSuggestion
import com.example.search.domain.suggest.RelatedSuggestion
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.retry.Retry
import java.util.function.Supplier

/**
 * [SearchEnginePort] 의 데코레이터 — Resilience4j Retry + CircuitBreaker chain (ADR-0012).
 *
 * chain 순서: **Retry → CircuitBreaker** (Retry 가 바깥). CB 가 OPEN 상태면
 * Retry 도 즉시 실패 — caller 에 빠르게 전달.
 *
 * - 일시 IO timeout / GC pause 같은 transient fault: Retry 가 (max 3회, 100ms exp jitter)
 *   흡수.
 * - 지속 fault (cluster down): CB 가 OPEN 으로 전환되어 Retry 도 즉시 fail — 검색 endpoint
 *   의 latency p99 보호.
 * - annotation 기반보다 *bean 단계에서 명시 wrap* — chain 순서 / 명칭이 코드에 잡힘.
 *
 * 이 클래스 자체는 Spring 무관 — 단위 테스트가 가능하다. bootstrap 의 ElasticsearchConfig 가
 * Retry + CB 를 주입해 wrap.
 */
class ResilientSearchClient(
    private val delegate: SearchEnginePort,
    private val retry: Retry,
    private val circuitBreaker: CircuitBreaker
) : SearchEnginePort {

    override fun search(command: SearchProductCommand): SearchResult =
        execute { delegate.search(command) }

    override fun autocomplete(prefix: String, limit: Int): List<AutocompleteSuggestion> =
        execute { delegate.autocomplete(prefix, limit) }

    override fun findRelatedKeywords(keyword: String, limit: Int, maxDistance: Int): List<RelatedSuggestion> =
        execute { delegate.findRelatedKeywords(keyword, limit, maxDistance) }

    /**
     * Retry → CB 순서로 supplier 를 wrap. CB 가 outer 면 Retry 의 모든 시도를 한 호출로 보아 실패율
     * 산정이 왜곡됨 — Retry 가 outer 여야 각 시도를 독립 호출로 카운트.
     *
     * resilience4j-decorators 모듈을 안 쓰고 직접 합성 — Spring Boot starter 에 transitive 로
     * 안 끌려오기 때문에 dep 추가 회피.
     */
    private fun <T> execute(action: () -> T): T {
        val supplier: Supplier<T> = Supplier { action() }
        val cbWrapped: Supplier<T> = CircuitBreaker.decorateSupplier(circuitBreaker, supplier) // 안쪽
        val retryWrapped: Supplier<T> = Retry.decorateSupplier(retry, cbWrapped)               // 바깥
        return retryWrapped.get()
    }
}
