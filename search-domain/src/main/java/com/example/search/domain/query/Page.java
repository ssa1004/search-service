package com.example.search.domain.query;

/**
 * 검색 페이지네이션. 0-based.
 *
 * <p>{@code size} 상한은 도메인이 100 으로 강제 — ES 의 deep pagination 비용을 방어한다. 1만 건
 * 이상 스캔이 필요하면 search_after / scroll API 를 별도로 분리해야 한다.</p>
 */
public record Page(int number, int size) {

    public static final int MAX_SIZE = 100;

    public Page {
        if (number < 0) {
            throw new IllegalArgumentException("page.number 는 음수 불가: " + number);
        }
        if (size <= 0) {
            throw new IllegalArgumentException("page.size 는 1 이상: " + size);
        }
        if (size > MAX_SIZE) {
            throw new IllegalArgumentException("page.size 는 " + MAX_SIZE + " 이하: " + size);
        }
    }

    public static Page first(int size) {
        return new Page(0, size);
    }

    public int from() {
        return number * size;
    }
}
