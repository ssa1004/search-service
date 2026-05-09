package com.example.search.application.service;

import com.example.search.application.command.SearchProductCommand;
import com.example.search.application.port.in.SearchProductUseCase;
import com.example.search.application.port.out.SearchEnginePort;
import com.example.search.domain.query.SearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 키워드 + filter + facet 검색 use case.
 *
 * <p>이 service 자체는 매우 얇다 — 실제 query DSL 빌딩은 adapter-out 의 ES 구현체가 담당. 여기서는
 * 공통 로깅과 metric 만 책임진다 ({@code took} 시간이 임계치를 넘으면 slow query 로 표시).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SearchProductService implements SearchProductUseCase {

    /** 운영 slow log 임계치 — ms. ADR-0008 의 성능 보호 정책과 함께 본다. */
    private static final long SLOW_QUERY_THRESHOLD_MS = 200L;

    private final SearchEnginePort searchEngine;

    @Override
    public SearchResult search(SearchProductCommand command) {
        SearchResult result = searchEngine.search(command);
        if (result.took() > SLOW_QUERY_THRESHOLD_MS) {
            log.warn("slow search took={}ms keyword='{}' filters={} hits={}",
                    result.took(), command.query().keyword(),
                    command.query().filters().size(), result.totalHits());
        }
        return result;
    }
}
