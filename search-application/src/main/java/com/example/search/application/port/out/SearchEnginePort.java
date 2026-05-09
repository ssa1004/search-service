package com.example.search.application.port.out;

import com.example.search.application.command.SearchProductCommand;
import com.example.search.domain.query.SearchResult;
import com.example.search.domain.suggest.AutocompleteSuggestion;
import com.example.search.domain.suggest.RelatedSuggestion;

import java.util.List;

/**
 * 검색 엔진 호출 port — 검색 + 자동완성 + 관련 검색어. 구현체는 Elasticsearch / OpenSearch / 메모리
 * (테스트용) 중 하나.
 *
 * <p>application 모듈은 ES SDK 를 직접 알지 못한다. boost rule 도 도메인 표현 그대로 받아 adapter
 * 가 function_score 로 번역.</p>
 *
 * <p>Resilience4j Circuit Breaker + Retry 가 이 port 의 운영 구현체에 적용된다 (ADR-0008
 * 참조).</p>
 */
public interface SearchEnginePort {

    SearchResult search(SearchProductCommand command);

    List<AutocompleteSuggestion> autocomplete(String prefix, int limit);

    /**
     * fuzzy match 기반 가까운 키워드 검색.
     */
    List<RelatedSuggestion> findRelatedKeywords(String keyword, int limit, int maxDistance);
}
