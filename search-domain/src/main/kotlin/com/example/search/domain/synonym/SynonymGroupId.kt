package com.example.search.domain.synonym

import java.util.UUID

/**
 * 동의어 그룹 식별자. 외부 노출은 String — 내부 generation 은 UUID.
 *
 * [com.example.search.domain.savedsearch.SavedSearchId] 와 같은 패턴 — raw String 을 도메인
 * 코드가 직접 다루지 않게 하기 위함.
 */
data class SynonymGroupId(
    @get:JvmName("value") val value: String
) {
    init {
        require(value.isNotBlank()) { "SynonymGroupId 빈 값 불가" }
    }

    companion object {
        @JvmStatic
        fun of(value: String): SynonymGroupId = SynonymGroupId(value)

        @JvmStatic
        fun newId(): SynonymGroupId = SynonymGroupId(UUID.randomUUID().toString())
    }
}
