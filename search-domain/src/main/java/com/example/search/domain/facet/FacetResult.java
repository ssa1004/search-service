package com.example.search.domain.facet;

import java.util.List;
import java.util.Objects;

/**
 * facet 결과 한 개. {@code name} 은 요청 시 지정한 식별자 (UI 가 어떤 facet 인지 구분).
 *
 * <p>ES aggregation 응답을 도메인 형태로 변환한 결과 — adapter 가 매핑하고 use case 는 그대로
 * 클라이언트에 돌려준다.</p>
 */
public record FacetResult(String name, List<Bucket> buckets) {

    public FacetResult {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(buckets, "buckets");
        buckets = List.copyOf(buckets);
    }

    /**
     * facet bucket. {@code key} 는 분포 단위 (brand=nike → "nike", price-range → "100k-200k").
     */
    public record Bucket(String key, long count) {
        public Bucket {
            Objects.requireNonNull(key, "key");
            if (count < 0) {
                throw new IllegalArgumentException("bucket count 음수 불가: " + count);
            }
        }
    }
}
