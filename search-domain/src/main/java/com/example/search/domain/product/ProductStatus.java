package com.example.search.domain.product;

/**
 * 상품 라이프사이클.
 *
 * <ul>
 *   <li>{@code AVAILABLE} — 정상 판매. 검색 결과 노출 + boost 대상.</li>
 *   <li>{@code SOLD_OUT} — 일시 품절. 검색 결과에 노출은 되지만 boost 미적용 (관련 상품 추천만).</li>
 *   <li>{@code DISCONTINUED} — 단종. 검색 결과에서 기본 제외 (관리자 옵션으로만 노출).</li>
 * </ul>
 */
public enum ProductStatus {
    AVAILABLE,
    SOLD_OUT,
    DISCONTINUED
}
