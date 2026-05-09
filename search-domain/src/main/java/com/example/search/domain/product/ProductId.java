package com.example.search.domain.product;

import java.util.Objects;
import java.util.UUID;

/**
 * 상품 식별자. UUID 문자열을 그대로 ES `_id` 로 사용 — ES 측 reindex 시 같은 id 로 upsert 되므로
 * alias swap 시 추가 매핑이 필요 없다.
 */
public record ProductId(String value) {

    public ProductId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("ProductId 는 빈 문자열 불가");
        }
    }

    public static ProductId random() {
        return new ProductId(UUID.randomUUID().toString());
    }

    public static ProductId of(String raw) {
        return new ProductId(raw);
    }

    @Override
    public String toString() {
        return value;
    }
}
