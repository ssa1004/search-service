package com.example.search.domain.savedsearch;

import com.example.search.domain.product.ProductId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * SavedSearch 매치 결과 — 알림 채널로 발행되는 메시지.
 *
 * <p>{@code matchedProductIds} 는 신규 매치된 product 들 (이전 평가 이후 추가). 다운스트림 (사용자
 * push / email) 에서 ID 만으로 product 상세 lookup.</p>
 *
 * <p>구독자 측이 이전 알림과 dedup 가능하도록 {@code firedAt} + {@code savedSearchId} 조합을 키로
 * 사용한다.</p>
 */
public record SavedSearchAlert(
        SavedSearchId savedSearchId,
        String ownerId,
        String label,
        List<ProductId> matchedProductIds,
        long totalNewMatches,
        Instant firedAt
) {

    public SavedSearchAlert {
        Objects.requireNonNull(savedSearchId, "savedSearchId");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(matchedProductIds, "matchedProductIds");
        Objects.requireNonNull(firedAt, "firedAt");
        if (totalNewMatches < 0) {
            throw new IllegalArgumentException("totalNewMatches 음수 불가: " + totalNewMatches);
        }
        matchedProductIds = List.copyOf(matchedProductIds);
    }

    public boolean isEmpty() {
        return matchedProductIds.isEmpty();
    }
}
