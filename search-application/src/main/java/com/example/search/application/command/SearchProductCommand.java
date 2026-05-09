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
 */
public record SearchProductCommand(
        SearchQuery query,
        List<FacetSpec> facetSpecs,
        BoostRule boostRule
) {

    public SearchProductCommand {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(facetSpecs, "facetSpecs");
        Objects.requireNonNull(boostRule, "boostRule");
        facetSpecs = List.copyOf(facetSpecs);
    }
}
