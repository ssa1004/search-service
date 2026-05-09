package com.example.search.domain.savedsearch;

import java.util.Objects;
import java.util.UUID;

/**
 * SavedSearch 의 식별자. 외부 노출은 String — 내부 generation 은 UUID.
 *
 * <p>도메인 record 의 nested 가 아닌 별도 value object 로 둔 이유는 ProductId 와 같은 패턴 — 도메인
 * 코드가 raw String 을 직접 다루지 않게 하기 위함이다.</p>
 */
public record SavedSearchId(String value) {

    public SavedSearchId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("SavedSearchId 빈 값 불가");
        }
    }

    public static SavedSearchId of(String value) {
        return new SavedSearchId(value);
    }

    public static SavedSearchId newId() {
        return new SavedSearchId(UUID.randomUUID().toString());
    }
}
