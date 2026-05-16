package com.example.search.application.command

/**
 * 관련 검색어 제안 명령. 보통 검색 결과 0건일 때 호출.
 *
 * [maxDistance] 는 Levenshtein 편집 거리 상한 — ES 의 fuzziness 파라미터로 매핑.
 * 값이 너무 크면 무관한 제안이 섞이므로 도메인이 1..2 로 제한.
 */
@JvmRecord
data class SuggestRelatedCommand(val keyword: String, val limit: Int, val maxDistance: Int) {

    init {
        if (keyword.isBlank()) {
            throw IllegalArgumentException("keyword 빈 값 불가")
        }
        if (keyword.length > MAX_KEYWORD_LENGTH) {
            throw IllegalArgumentException(
                "keyword 길이 $MAX_KEYWORD_LENGTH 이하: ${keyword.length}"
            )
        }
        if (limit <= 0 || limit > MAX_LIMIT) {
            throw IllegalArgumentException("limit 1..$MAX_LIMIT: $limit")
        }
        if (maxDistance < 1 || maxDistance > MAX_DISTANCE) {
            throw IllegalArgumentException("maxDistance 1..$MAX_DISTANCE: $maxDistance")
        }
    }

    companion object {
        const val MAX_LIMIT: Int = 10
        const val MAX_DISTANCE: Int = 2

        /**
         * 키워드 길이 상한 — fuzzy match + Levenshtein 계산은 O(N*M) 이라 비정상 long 입력은 비용
         * 폭증의 원인. 단일 검색어 길이로 200자면 충분.
         */
        const val MAX_KEYWORD_LENGTH: Int = 200
    }
}
