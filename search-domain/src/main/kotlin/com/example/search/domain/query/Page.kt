package com.example.search.domain.query

/**
 * 검색 페이지네이션. 0-based.
 *
 * [size] 상한은 도메인이 100 으로 강제 — ES 의 deep pagination 비용을 방어한다. 1만 건
 * 이상 스캔이 필요하면 search_after / scroll API 를 별도로 분리해야 한다.
 *
 * `(number + 1) * size` 가 [MAX_WINDOW] (= 10,000 — ES 의 기본
 * `index.max_result_window`) 를 넘으면 거부한다. 도메인이 거부하지 않으면 ES 가
 * `result_window_too_large` 로 500 응답을 내기 전까지 cluster 가 scan 비용을 부담하므로
 * 입력 단계에서 컷.
 */
@JvmRecord
data class Page(
    val number: Int,
    val size: Int
) {
    init {
        require(number >= 0) { "page.number 는 음수 불가: $number" }
        require(size > 0) { "page.size 는 1 이상: $size" }
        require(size <= MAX_SIZE) { "page.size 는 $MAX_SIZE 이하: $size" }
        // (number + 1) * size 가 ES result window 한계 (10,000) 를 넘으면 거부. long 으로 계산해
        // overflow 회피.
        val window = (number + 1).toLong() * size.toLong()
        require(window <= MAX_WINDOW) {
            "page (number + 1) * size 는 $MAX_WINDOW 이하 (deep pagination 보호): $window"
        }
    }

    fun from(): Int = number * size

    companion object {
        const val MAX_SIZE: Int = 100

        /** ES 기본 `index.max_result_window` 와 같음 — from + size 합산 상한. */
        const val MAX_WINDOW: Int = 10_000

        @JvmStatic
        fun first(size: Int): Page = Page(0, size)
    }
}
