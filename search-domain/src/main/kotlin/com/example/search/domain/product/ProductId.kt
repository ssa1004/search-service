package com.example.search.domain.product

import java.util.UUID

/**
 * 상품 식별자. UUID 문자열을 그대로 ES `_id` 로 사용 — ES 측 reindex 시 같은 id 로 upsert 되므로
 * alias swap 시 추가 매핑이 필요 없다.
 */
data class ProductId(
    @get:JvmName("value") val value: String
) {
    init {
        require(value.isNotBlank()) { "ProductId 는 빈 문자열 불가" }
    }

    override fun toString(): String = value

    companion object {
        @JvmStatic
        fun random(): ProductId = ProductId(UUID.randomUUID().toString())

        @JvmStatic
        fun of(raw: String): ProductId = ProductId(raw)
    }
}
