package com.example.search.domain.query;

import java.util.Objects;

/**
 * 정렬 옵션. null 이면 ES 의 기본 _score (boost 적용 결과) 정렬.
 *
 * <p>성능상 {@code keyword} / 숫자 필드만 정렬 대상으로 허용. {@code text} 필드는 fielddata 가
 * 비싸므로 금지 — 도메인이 명시적으로 막는다.</p>
 */
public record SortSpec(String field, Direction direction) {

    public SortSpec {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(direction, "direction");
    }

    public enum Direction {
        ASC, DESC
    }

    public static SortSpec asc(String field) {
        return new SortSpec(field, Direction.ASC);
    }

    public static SortSpec desc(String field) {
        return new SortSpec(field, Direction.DESC);
    }
}
