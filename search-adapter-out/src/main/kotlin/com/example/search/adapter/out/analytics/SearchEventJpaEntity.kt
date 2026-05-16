package com.example.search.adapter.out.analytics

import com.example.search.domain.analytics.SearchEvent
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant

/**
 * 검색 이벤트 테이블 — 분석 자료 (ADR-0018).
 *
 * 인덱스:
 * - `(occurred_at)` — 모든 분석 query 가 시간 구간 필터.
 * - `(occurred_at, keyword)` — top queries / zero-result group-by 의 covering.
 *
 * partition 은 후속 ADR — 월별 파티션이 필요한 규모는 검색 단위 1억건/월 이상.
 */
@Entity
@Table(
    name = "search_events",
    indexes = [
        Index(name = "ix_search_events_occurred", columnList = "occurred_at"),
        Index(name = "ix_search_events_occurred_keyword", columnList = "occurred_at, keyword")
    ]
)
class SearchEventJpaEntity protected constructor() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null
        private set

    @Column(name = "search_id", nullable = false, length = 64)
    var searchId: String = ""
        private set

    @Column(name = "keyword", nullable = false, length = 200)
    var keyword: String = ""
        private set

    @Column(name = "user_id", length = 64)
    var userId: String? = null
        private set

    @Column(name = "result_count", nullable = false)
    var resultCount: Long = 0
        private set

    @Column(name = "latency_ms", nullable = false)
    var latencyMs: Long = 0
        private set

    @Column(name = "occurred_at", nullable = false)
    var occurredAt: Instant = Instant.EPOCH
        private set

    companion object {
        @JvmStatic
        fun from(e: SearchEvent): SearchEventJpaEntity {
            val entity = SearchEventJpaEntity()
            entity.searchId = e.searchId
            entity.keyword = e.keyword
            entity.userId = e.userId
            entity.resultCount = e.resultCount
            entity.latencyMs = e.latencyMs
            entity.occurredAt = e.occurredAt
            return entity
        }
    }
}
