package com.example.search.domain.query;

/**
 * 검색 페이지네이션. 0-based.
 *
 * <p>{@code size} 상한은 도메인이 100 으로 강제 — ES 의 deep pagination 비용을 방어한다. 1만 건
 * 이상 스캔이 필요하면 search_after / scroll API 를 별도로 분리해야 한다.</p>
 *
 * <p>{@code (number + 1) * size} 가 {@link #MAX_WINDOW} (= 10,000 — ES 의 기본
 * {@code index.max_result_window}) 를 넘으면 거부한다. 도메인이 거부하지 않으면 ES 가
 * {@code result_window_too_large} 로 500 응답을 내기 전까지 cluster 가 scan 비용을 부담하므로
 * 입력 단계에서 컷.</p>
 */
public record Page(int number, int size) {

    public static final int MAX_SIZE = 100;

    /** ES 기본 {@code index.max_result_window} 와 같음 — from + size 합산 상한. */
    public static final int MAX_WINDOW = 10_000;

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
        // (number + 1) * size 가 ES result window 한계 (10,000) 를 넘으면 거부. long 으로 계산해
        // overflow 회피.
        long window = (long) (number + 1) * (long) size;
        if (window > MAX_WINDOW) {
            throw new IllegalArgumentException(
                    "page (number + 1) * size 는 " + MAX_WINDOW + " 이하 (deep pagination 보호): "
                            + window);
        }
    }

    public static Page first(int size) {
        return new Page(0, size);
    }

    public int from() {
        return number * size;
    }
}
