package com.example.search.adapter.out.persistence.jpa

import com.example.search.domain.event.SearchClick
import com.example.search.domain.product.ProductId
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant

/**
 * 검색 클릭 로그 테이블 — boost rule 학습 자료.
 *
 * `product_id` 인덱스로 조회 — reindex 시 product 별 click 합산을 빠르게.
 */
@Entity
@Table(
    name = "search_clicks",
    indexes = [Index(name = "ix_search_clicks_product", columnList = "product_id, occurred_at")]
)
class SearchClickJpaEntity protected constructor() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null
        private set

    @Column(name = "search_id", nullable = false, length = 64)
    var searchId: String = ""
        private set

    @Column(name = "user_id", length = 64)
    var userId: String? = null
        private set

    @Column(name = "product_id", nullable = false, length = 64)
    var productId: String = ""
        private set

    @Column(name = "keyword", nullable = false, length = 200)
    var keyword: String = ""
        private set

    @Column(name = "rank_position", nullable = false)
    var rankPosition: Int = 0
        private set

    @Column(name = "occurred_at", nullable = false)
    var occurredAt: Instant = Instant.EPOCH
        private set

    fun toDomain(): SearchClick = SearchClick(
        searchId, userId, ProductId.of(productId),
        keyword, rankPosition, occurredAt
    )

    companion object {
        @JvmStatic
        fun from(click: SearchClick): SearchClickJpaEntity {
            val e = SearchClickJpaEntity()
            e.searchId = click.searchId
            e.userId = click.userId
            e.productId = click.productId.value
            e.keyword = click.keyword
            e.rankPosition = click.rank
            e.occurredAt = click.occurredAt
            return e
        }
    }
}
