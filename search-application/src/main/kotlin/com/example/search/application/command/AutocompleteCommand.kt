package com.example.search.application.command

/**
 * 자동완성 명령. [prefix] 입력에 대한 [limit] 개 후보를 반환받는다.
 *
 * prefix 가 빈 문자열이면 use case 가 빈 결과를 반환 — ES 쿼리 자체를 보내지 않아 비용 0.
 */
@JvmRecord
data class AutocompleteCommand(val prefix: String, val limit: Int) {

    init {
        if (prefix.length > MAX_PREFIX_LENGTH) {
            throw IllegalArgumentException(
                "prefix 길이 $MAX_PREFIX_LENGTH 이하: ${prefix.length}"
            )
        }
        if (limit <= 0 || limit > MAX_LIMIT) {
            throw IllegalArgumentException("limit 1..$MAX_LIMIT: $limit")
        }
    }

    fun isEmpty(): Boolean = prefix.isBlank()

    companion object {
        const val MAX_LIMIT: Int = 20

        /**
         * prefix 길이 상한 — 비정상적으로 긴 입력 (예: 수 KB) 이 multi_match / edge_ngram 토큰화를
         * 거치며 비용 폭증하는 것 방지. 사용자가 실수로 paste 한 긴 문자열도 빠른 fail.
         */
        const val MAX_PREFIX_LENGTH: Int = 100
    }
}
