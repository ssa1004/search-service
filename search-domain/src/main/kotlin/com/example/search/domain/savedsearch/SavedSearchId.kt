package com.example.search.domain.savedsearch

import java.util.UUID

/**
 * SavedSearch 의 식별자. 외부 노출은 String — 내부 generation 은 UUID.
 *
 * 도메인 record 의 nested 가 아닌 별도 value object 로 둔 이유는 ProductId 와 같은 패턴 — 도메인
 * 코드가 raw String 을 직접 다루지 않게 하기 위함이다.
 */
data class SavedSearchId(
    @get:JvmName("value") val value: String
) {
    init {
        require(value.isNotBlank()) { "SavedSearchId 빈 값 불가" }
    }

    companion object {
        @JvmStatic
        fun of(value: String): SavedSearchId = SavedSearchId(value)

        @JvmStatic
        fun newId(): SavedSearchId = SavedSearchId(UUID.randomUUID().toString())
    }
}
