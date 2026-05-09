package com.example.search.domain.analytics;

import java.util.Objects;

/**
 * keyword 별 집계 한 행 — top queries / zero result query 양쪽이 같은 모양.
 *
 * <p>{@code count} 는 일반 top 에서는 검색 횟수, zero-result top 에서는 결과 0건이 났던 횟수.</p>
 */
public record QueryStat(String keyword, long count) {

    public QueryStat {
        Objects.requireNonNull(keyword, "keyword");
        if (count < 0) {
            throw new IllegalArgumentException("count 음수 불가: " + count);
        }
    }
}
