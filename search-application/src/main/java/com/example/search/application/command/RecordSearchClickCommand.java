package com.example.search.application.command;

import com.example.search.domain.product.ProductId;

import java.util.Objects;

/**
 * 검색 결과 클릭 기록 명령. boost rule 학습을 위한 시그널.
 */
public record RecordSearchClickCommand(
        String searchId,
        String userId,
        ProductId productId,
        String keyword,
        int rank
) {

    public RecordSearchClickCommand {
        Objects.requireNonNull(searchId, "searchId");
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(keyword, "keyword");
        if (rank < 1) {
            throw new IllegalArgumentException("rank 1 이상: " + rank);
        }
    }
}
