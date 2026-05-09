package com.example.search.domain.synonym;

import java.util.Objects;
import java.util.UUID;

/**
 * 동의어 그룹 식별자. 외부 노출은 String — 내부 generation 은 UUID.
 *
 * <p>{@link com.example.search.domain.savedsearch.SavedSearchId} 와 같은 패턴 — raw String 을 도메인
 * 코드가 직접 다루지 않게 하기 위함.</p>
 */
public record SynonymGroupId(String value) {

    public SynonymGroupId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("SynonymGroupId 빈 값 불가");
        }
    }

    public static SynonymGroupId of(String value) {
        return new SynonymGroupId(value);
    }

    public static SynonymGroupId newId() {
        return new SynonymGroupId(UUID.randomUUID().toString());
    }
}
