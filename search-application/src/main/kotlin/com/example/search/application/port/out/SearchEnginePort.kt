package com.example.search.application.port.out

import com.example.search.application.command.SearchProductCommand
import com.example.search.domain.query.SearchResult
import com.example.search.domain.suggest.AutocompleteSuggestion
import com.example.search.domain.suggest.RelatedSuggestion

/**
 * 검색 엔진 호출 port — 검색 + 자동완성 + 관련 검색어. 구현체는 Elasticsearch / OpenSearch / 메모리
 * (테스트용) 중 하나.
 *
 * application 모듈은 ES SDK 를 직접 알지 못한다. boost rule 도 도메인 표현 그대로 받아 adapter
 * 가 function_score 로 번역.
 *
 * Resilience4j Circuit Breaker + Retry 가 이 port 의 운영 구현체에 적용된다 (ADR-0012 참조).
 */
interface SearchEnginePort {

    fun search(command: SearchProductCommand): SearchResult

    fun autocomplete(prefix: String, limit: Int): List<AutocompleteSuggestion>

    /**
     * fuzzy match 기반 가까운 키워드 검색.
     */
    fun findRelatedKeywords(keyword: String, limit: Int, maxDistance: Int): List<RelatedSuggestion>
}
