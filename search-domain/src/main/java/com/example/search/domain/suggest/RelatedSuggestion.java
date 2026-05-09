package com.example.search.domain.suggest;

import java.util.Objects;

/**
 * 검색 결과 0건일 때 제안되는 관련 검색어. fuzzy match (Levenshtein 1-2) 로 가까운 인기 검색어를
 * 찾아서 반환.
 *
 * <p>{@code distance} 는 원본 키워드와의 편집 거리 — UI 에서 정렬 / 필터 기준으로 활용.</p>
 */
public record RelatedSuggestion(String suggestedKeyword, long popularity, int distance) {

    public RelatedSuggestion {
        Objects.requireNonNull(suggestedKeyword, "suggestedKeyword");
        if (suggestedKeyword.isBlank()) {
            throw new IllegalArgumentException("suggestedKeyword 빈 값 불가");
        }
        if (popularity < 0) {
            throw new IllegalArgumentException("popularity 음수 불가: " + popularity);
        }
        if (distance < 0) {
            throw new IllegalArgumentException("distance 음수 불가: " + distance);
        }
    }
}
