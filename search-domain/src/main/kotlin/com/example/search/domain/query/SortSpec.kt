package com.example.search.domain.query

/**
 * 정렬 옵션. null 이면 ES 의 기본 _score (boost 적용 결과) 정렬.
 *
 * 성능상 `keyword` / 숫자 필드만 정렬 대상으로 허용. `text` 필드는 fielddata 가
 * 비싸므로 금지 — 도메인이 명시적으로 막는다.
 */
@JvmRecord
data class SortSpec(
    val field: String,
    val direction: Direction
) {
    enum class Direction {
        ASC, DESC
    }

    companion object {
        @JvmStatic
        fun asc(field: String): SortSpec = SortSpec(field, Direction.ASC)

        @JvmStatic
        fun desc(field: String): SortSpec = SortSpec(field, Direction.DESC)
    }
}
