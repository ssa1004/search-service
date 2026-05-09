package com.example.search.adapter.out.persistence.jpa;

import com.example.search.domain.event.SearchClick;
import com.example.search.domain.product.ProductId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 검색 클릭 로그 테이블 — boost rule 학습 자료.
 *
 * <p>{@code product_id} 인덱스로 조회 — reindex 시 product 별 click 합산을 빠르게.</p>
 */
@Entity
@Table(name = "search_clicks", indexes = {
        @Index(name = "ix_search_clicks_product", columnList = "product_id, occurred_at")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class SearchClickJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "search_id", nullable = false, length = 64)
    private String searchId;

    @Column(name = "user_id", length = 64)
    private String userId;

    @Column(name = "product_id", nullable = false, length = 64)
    private String productId;

    @Column(name = "keyword", nullable = false, length = 200)
    private String keyword;

    @Column(name = "rank_position", nullable = false)
    private int rankPosition;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    public static SearchClickJpaEntity from(SearchClick click) {
        SearchClickJpaEntity e = new SearchClickJpaEntity();
        e.searchId = click.searchId();
        e.userId = click.userId();
        e.productId = click.productId().value();
        e.keyword = click.keyword();
        e.rankPosition = click.rank();
        e.occurredAt = click.occurredAt();
        return e;
    }

    public SearchClick toDomain() {
        return new SearchClick(searchId, userId, ProductId.of(productId),
                keyword, rankPosition, occurredAt);
    }
}
