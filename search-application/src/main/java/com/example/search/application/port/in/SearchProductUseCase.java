package com.example.search.application.port.in;

import com.example.search.application.command.SearchProductCommand;
import com.example.search.domain.query.SearchResult;

/**
 * 상품 검색 — 키워드 + filter + facet + sort + pagination → ES.
 *
 * <p>구현체는 ES query 빌딩을 adapter-out 의 port (SearchEnginePort) 로 위임. 결과의 boost 적용은
 * adapter 측 function_score 표현이 책임지고, use case 는 그 결과를 도메인 형태로 받아 그대로 반환.</p>
 */
public interface SearchProductUseCase {
    SearchResult search(SearchProductCommand command);
}
