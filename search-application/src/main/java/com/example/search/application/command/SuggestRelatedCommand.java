package com.example.search.application.command;

import java.util.Objects;

/**
 * 관련 검색어 제안 명령. 보통 검색 결과 0건일 때 호출.
 *
 * <p>{@code maxDistance} 는 Levenshtein 편집 거리 상한 — ES 의 fuzziness 파라미터로 매핑.
 * 값이 너무 크면 무관한 제안이 섞이므로 도메인이 1..2 로 제한.</p>
 */
public record SuggestRelatedCommand(String keyword, int limit, int maxDistance) {

    public static final int MAX_LIMIT = 10;
    public static final int MAX_DISTANCE = 2;

    public SuggestRelatedCommand {
        Objects.requireNonNull(keyword, "keyword");
        if (keyword.isBlank()) {
            throw new IllegalArgumentException("keyword 빈 값 불가");
        }
        if (limit <= 0 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit 1.." + MAX_LIMIT + ": " + limit);
        }
        if (maxDistance < 1 || maxDistance > MAX_DISTANCE) {
            throw new IllegalArgumentException("maxDistance 1.." + MAX_DISTANCE + ": " + maxDistance);
        }
    }
}
