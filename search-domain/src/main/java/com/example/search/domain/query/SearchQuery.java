package com.example.search.domain.query;

import java.util.List;
import java.util.Objects;

/**
 * 검색 입력 표현 — REST 요청 / use case 입력의 단일 통로.
 *
 * <p>도메인 객체이지만 ES 와 직접 매핑되는 형태는 아니다 — adapter-out 의 ES query builder 가 이
 * 객체를 받아 ES `_search` body 로 직렬화한다.</p>
 *
 * <ul>
 *   <li>{@code keyword} — 빈 문자열 가능 (그 경우는 filter 만으로 검색).</li>
 *   <li>{@code filters} — 정확 일치 (term/terms/range). brand=["nike"], price=[0..200000].</li>
 *   <li>{@code facets} — 결과로 받고 싶은 분포 차원 (brand, price-range, size).</li>
 *   <li>{@code sort} — null 이면 relevance score (boost 적용 결과) 기준.</li>
 *   <li>{@code page} — 0-based.</li>
 * </ul>
 */
public record SearchQuery(
        String keyword,
        List<FilterCriterion> filters,
        List<String> facets,
        SortSpec sort,
        Page page
) {

    public SearchQuery {
        Objects.requireNonNull(keyword, "keyword");
        Objects.requireNonNull(filters, "filters");
        Objects.requireNonNull(facets, "facets");
        Objects.requireNonNull(page, "page");
        filters = List.copyOf(filters);
        facets = List.copyOf(facets);
    }

    public static SearchQuery byKeyword(String keyword, Page page) {
        return new SearchQuery(keyword, List.of(), List.of(), null, page);
    }

    public boolean hasKeyword() {
        return !keyword.isBlank();
    }

    public boolean hasFilters() {
        return !filters.isEmpty();
    }

    public boolean wantsFacets() {
        return !facets.isEmpty();
    }
}
