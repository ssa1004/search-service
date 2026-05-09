package com.example.search.application.command;

import java.util.Objects;

/**
 * 자동완성 명령. {@code prefix} 입력에 대한 {@code limit} 개 후보를 반환받는다.
 *
 * <p>prefix 가 빈 문자열이면 use case 가 빈 결과를 반환 — ES 쿼리 자체를 보내지 않아 비용 0.</p>
 */
public record AutocompleteCommand(String prefix, int limit) {

    public static final int MAX_LIMIT = 20;

    public AutocompleteCommand {
        Objects.requireNonNull(prefix, "prefix");
        if (limit <= 0 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit 1.." + MAX_LIMIT + ": " + limit);
        }
    }

    public boolean isEmpty() {
        return prefix.isBlank();
    }
}
