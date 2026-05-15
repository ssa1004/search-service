package com.example.search.domain.suggest

import com.example.search.domain.product.ProductId

/**
 * 자동완성 한 건 — prefix 입력에 대한 매칭 후보.
 *
 * [text] 는 사용자에게 그대로 보여줄 문자열, [score] 는 ES 의 매칭 점수 (boost 반영).
 * [productId] 는 클릭 시 즉시 상품 이동을 위해 동봉.
 */
@JvmRecord
data class AutocompleteSuggestion(
    val text: String,
    val productId: ProductId,
    val score: Double
) {
    init {
        require(text.isNotBlank()) { "suggestion text 빈 값 불가" }
    }
}
