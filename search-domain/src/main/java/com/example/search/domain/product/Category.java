package com.example.search.domain.product;

/**
 * 상품 카테고리. faceted aggregation 의 일급 차원 — terms aggregation 으로 분포를 반환한다.
 *
 * <p>실제 commerce 라면 카테고리 트리 (계층) 가 들어가지만, 여기서는 단순 도메인이라
 * flat enum 으로 충분하다.</p>
 */
public enum Category {
    SNEAKERS,
    APPAREL,
    BAGS,
    ACCESSORIES,
    COLLECTIBLES
}
