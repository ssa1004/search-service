package com.example.search.application.command;

import com.example.search.domain.facet.FacetSpec;
import com.example.search.domain.query.SearchQuery;
import com.example.search.domain.index.BoostRule;

import java.util.List;
import java.util.Objects;

/**
 * 상품 검색 명령. {@link SearchQuery} (입력 표현) + {@code facetSpecs} + {@code boostRule} 조합.
 *
 * <p>SearchQuery 의 {@code facets} 는 facet 식별자 (이름) 만 담고, 실제 ES aggregation 정의는
 * {@code facetSpecs} 가 담당 — 도메인이 facet 정의의 sane range (cardinality 상한) 를 강제한다.</p>
 *
 * <p>{@code searchId} / {@code userId} 는 분석용 추적 식별자 — null 가능 (외부 / 익명 검색).
 * SearchProductService 가 호출 후 SearchEvent 비동기 기록에 사용하고, 동일 searchId 로 클릭
 * 이벤트가 join 되어 CTR 계산에 활용된다 (ADR-0018).</p>
 */
public record SearchProductCommand(
        SearchQuery query,
        List<FacetSpec> facetSpecs,
        BoostRule boostRule,
        String searchId,
        String userId
) {

    public SearchProductCommand {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(facetSpecs, "facetSpecs");
        Objects.requireNonNull(boostRule, "boostRule");
        facetSpecs = List.copyOf(facetSpecs);
        // searchId / userId 는 익명 / 외부 검색 호환 위해 null 허용 — 분석에서는 anonymous 처리.
    }

    /** 분석 식별자가 필요 없는 호출자 (단위 테스트 등) 용 단축 생성. */
    public SearchProductCommand(SearchQuery query, List<FacetSpec> facetSpecs, BoostRule boostRule) {
        this(query, facetSpecs, boostRule, null, null);
    }
}
