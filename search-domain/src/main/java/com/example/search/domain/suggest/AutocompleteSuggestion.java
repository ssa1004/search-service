package com.example.search.domain.suggest;

import com.example.search.domain.product.ProductId;

import java.util.Objects;

/**
 * 자동완성 한 건 — prefix 입력에 대한 매칭 후보.
 *
 * <p>{@code text} 는 사용자에게 그대로 보여줄 문자열, {@code score} 는 ES 의 매칭 점수 (boost 반영).
 * {@code productId} 는 클릭 시 즉시 상품 이동을 위해 동봉.</p>
 */
public record AutocompleteSuggestion(String text, ProductId productId, double score) {

    public AutocompleteSuggestion {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(productId, "productId");
        if (text.isBlank()) {
            throw new IllegalArgumentException("suggestion text 빈 값 불가");
        }
    }
}
