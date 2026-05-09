package com.example.search.domain.query;

import java.util.List;
import java.util.Objects;

/**
 * 필터 한 개. ES query 의 {@code filter} context 에 들어가므로 score 에 영향을 주지 않는다 (정확
 * 일치만, 점수 가중 없음).
 *
 * <p>4가지 형태:</p>
 * <ul>
 *   <li>{@code term} — 단일 값 정확 일치 (brand=nike).</li>
 *   <li>{@code terms} — 다중 값 OR (brand IN nike, adidas).</li>
 *   <li>{@code range} — 숫자 범위 (price >= 100000 AND price < 200000).</li>
 *   <li>{@code exists} — 필드 존재 여부.</li>
 * </ul>
 */
public sealed interface FilterCriterion {

    String field();

    record Term(String field, String value) implements FilterCriterion {
        public Term {
            Objects.requireNonNull(field, "field");
            Objects.requireNonNull(value, "value");
        }
    }

    record Terms(String field, List<String> values) implements FilterCriterion {
        public Terms {
            Objects.requireNonNull(field, "field");
            Objects.requireNonNull(values, "values");
            if (values.isEmpty()) {
                throw new IllegalArgumentException("Terms 필터는 빈 값 불가: " + field);
            }
            values = List.copyOf(values);
        }
    }

    /**
     * 숫자 범위. {@code from}, {@code to} 둘 다 nullable — null 은 무한대를 의미. 양쪽 inclusive
     * 여부는 {@code fromInclusive}, {@code toInclusive} 로 분리.
     */
    record Range(String field, Long from, boolean fromInclusive, Long to, boolean toInclusive)
            implements FilterCriterion {
        public Range {
            Objects.requireNonNull(field, "field");
            if (from == null && to == null) {
                throw new IllegalArgumentException("Range 필터는 from/to 둘 다 null 불가: " + field);
            }
            if (from != null && to != null && from > to) {
                throw new IllegalArgumentException("Range from > to: " + field);
            }
        }

        public static Range gte(String field, long from) {
            return new Range(field, from, true, null, false);
        }

        public static Range lt(String field, long to) {
            return new Range(field, null, false, to, false);
        }

        public static Range between(String field, long from, long to) {
            return new Range(field, from, true, to, false);
        }
    }

    record Exists(String field) implements FilterCriterion {
        public Exists {
            Objects.requireNonNull(field, "field");
        }
    }
}
