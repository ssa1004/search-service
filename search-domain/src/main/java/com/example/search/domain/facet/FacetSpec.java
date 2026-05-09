package com.example.search.domain.facet;

import java.util.List;
import java.util.Objects;

/**
 * 단일 facet 요청 명세. ES 의 aggregation 한 개에 대응.
 *
 * <p>두 형태:</p>
 * <ul>
 *   <li>{@link Terms} — keyword 필드의 값 분포 (brand 별 개수).</li>
 *   <li>{@link Range} — 숫자 필드의 구간 분포 (가격대별 개수).</li>
 * </ul>
 *
 * <p>도메인이 cardinality 상한 ({@link Terms#size}) 을 강제 — ADR-0008 의 메모리 보호.</p>
 */
public sealed interface FacetSpec {

    String name();

    String field();

    record Terms(String name, String field, int size) implements FacetSpec {

        /**
         * terms aggregation 의 default 상한. 한 facet 이 100 개 bucket 을 넘으면 UI 도 의미 없고
         * ES 메모리 사용량도 급증.
         */
        public static final int MAX_SIZE = 100;

        public Terms {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(field, "field");
            if (size <= 0 || size > MAX_SIZE) {
                throw new IllegalArgumentException("terms facet size 1.." + MAX_SIZE + ": " + size);
            }
        }
    }

    record Range(String name, String field, List<Bucket> buckets) implements FacetSpec {

        public Range {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(field, "field");
            Objects.requireNonNull(buckets, "buckets");
            if (buckets.isEmpty()) {
                throw new IllegalArgumentException("range facet bucket 은 1개 이상");
            }
            buckets = List.copyOf(buckets);
        }

        /**
         * 범위 한 개. {@code from} / {@code to} 둘 다 nullable — null 은 무한대.
         */
        public record Bucket(String key, Long from, Long to) {
            public Bucket {
                Objects.requireNonNull(key, "key");
                if (from == null && to == null) {
                    throw new IllegalArgumentException("bucket from/to 둘 다 null 불가: " + key);
                }
            }
        }
    }
}
