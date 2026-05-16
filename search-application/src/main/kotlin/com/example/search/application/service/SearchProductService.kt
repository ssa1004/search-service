package com.example.search.application.service

import com.example.search.application.analytics.port.out.SearchEventRecorder
import com.example.search.application.command.SearchProductCommand
import com.example.search.application.port.`in`.SearchProductUseCase
import com.example.search.application.port.out.SearchEnginePort
import com.example.search.domain.analytics.SearchEvent
import com.example.search.domain.query.SearchResult
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * 키워드 + filter + facet 검색 use case.
 *
 * 이 service 자체는 매우 얇다 — 실제 query DSL 빌딩은 adapter-out 의 ES 구현체가 담당. 여기서는
 * 공통 로깅 / metric 과 분석용 SearchEvent 기록을 책임진다.
 *
 * SearchEvent 기록은 ADR-0018 — recorder 는 fire-and-forget 이고 실패 전파 X. recorder 빈이
 * 등록되지 않은 환경 (단위 테스트 / 분석 비활성) 에서는 자동으로 no-op.
 */
@Service
class SearchProductService(
    private val searchEngine: SearchEnginePort,
    private val recorderProvider: ObjectProvider<SearchEventRecorder>,
    private val clock: Clock
) : SearchProductUseCase {

    override fun search(command: SearchProductCommand): SearchResult {
        val result = searchEngine.search(command)
        if (result.took > SLOW_QUERY_THRESHOLD_MS) {
            log.warn(
                "slow search took={}ms keyword='{}' filters={} hits={}",
                result.took, command.query.keyword,
                command.query.filters.size, result.totalHits
            )
        }
        recordEvent(command, result)
        return result
    }

    private fun recordEvent(command: SearchProductCommand, result: SearchResult) {
        val recorder = recorderProvider.ifAvailable ?: return
        try {
            val keyword = normalizeKeyword(command.query.keyword)
            val searchId = command.searchId ?: UUID.randomUUID().toString()
            val event = SearchEvent(
                searchId, keyword, command.userId,
                result.totalHits, result.took,
                Instant.now(clock)
            )
            recorder.record(event)
        } catch (e: RuntimeException) {
            // 분석 기록 실패는 검색 응답을 깨뜨리지 않는다 — debug 레벨 로그만.
            log.debug("SearchEvent 기록 skip: {}", e.message)
        }
    }

    companion object {
        /** 운영 slow log 임계치 — ms. ADR-0008 의 성능 보호 정책과 함께 본다. */
        private const val SLOW_QUERY_THRESHOLD_MS: Long = 200L

        private val log = LoggerFactory.getLogger(SearchProductService::class.java)

        /** keyword 정규화 — trim + lowercase. 분석 group-by 시 표기 변동 흡수. */
        @JvmStatic
        private fun normalizeKeyword(raw: String?): String =
            raw?.trim()?.lowercase() ?: ""
    }
}
