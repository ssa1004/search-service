package com.example.search.domain.event;

import com.example.search.domain.product.ProductId;

import java.time.Instant;
import java.util.Objects;

/**
 * 사용자가 검색 결과에서 어떤 상품을 클릭했는지의 로그. 다음 검색의 boost rule 학습 시그널이다.
 *
 * <p>흐름:</p>
 * <ol>
 *   <li>사용자가 검색 → 결과 노출.</li>
 *   <li>결과 중 한 건 클릭 → REST POST /searches/{searchId}/clicks {productId}</li>
 *   <li>{@link com.example.search.domain.event.SearchClick} 이벤트 저장.</li>
 *   <li>indexer 가 해당 product 의 ES 문서 {@code clickCount} += 1 (partial update).</li>
 *   <li>다음 검색에서 function_score 의 popularity 가중치에 반영.</li>
 * </ol>
 *
 * <p>{@code rank} 는 결과 페이지에서 클릭된 위치 (1-based). 1순위 클릭과 50순위 클릭은 시그널 강도가
 * 다름 — 후속 학습 단계에서 weight 분리 가능.</p>
 */
public record SearchClick(
        String searchId,
        String userId,
        ProductId productId,
        String keyword,
        int rank,
        Instant occurredAt
) {

    public SearchClick {
        Objects.requireNonNull(searchId, "searchId");
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(keyword, "keyword");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (rank < 1) {
            throw new IllegalArgumentException("rank 1 이상이어야 함: " + rank);
        }
    }
}
