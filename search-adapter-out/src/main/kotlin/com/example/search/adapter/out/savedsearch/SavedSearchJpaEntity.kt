package com.example.search.adapter.out.savedsearch

import com.example.search.domain.query.SearchQuery
import com.example.search.domain.savedsearch.NotifyChannel
import com.example.search.domain.savedsearch.SavedSearch
import com.example.search.domain.savedsearch.SavedSearchId
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant

/**
 * SavedSearch persistence 매핑.
 *
 * `queryJson` 은 도메인 [com.example.search.domain.query.SearchQuery] 의 직렬화 결과를
 * 그대로 보관 — 컬럼 모양은 안정적이고, query 구조 변경 (필드 추가) 시 마이그레이션 부담이 적다.
 * 단점은 query 안의 특정 필드로 SQL 검색 불가 — 운영 자체엔 그게 필요 없어서 trade-off 수용.
 *
 * 인덱스:
 * - `(owner_id)` — 사용자별 목록 조회.
 * - `(active, id)` — 스케줄러 cursor paging.
 */
@Entity
@Table(
    name = "saved_searches",
    indexes = [
        Index(name = "ix_saved_searches_owner", columnList = "owner_id"),
        Index(name = "ix_saved_searches_active_id", columnList = "active, id")
    ]
)
class SavedSearchJpaEntity protected constructor() {

    @Id
    @Column(name = "id", length = 64)
    var id: String = ""
        private set

    @Column(name = "owner_id", nullable = false, length = 64)
    var ownerId: String = ""
        private set

    @Column(name = "label", nullable = false, length = 200)
    var label: String = ""
        private set

    @Column(name = "query_json", nullable = false, columnDefinition = "TEXT")
    var queryJson: String = ""
        private set

    @Enumerated(EnumType.STRING)
    @Column(name = "channel_type", nullable = false, length = 16)
    var channelType: NotifyChannel.Type = NotifyChannel.Type.KAFKA
        private set

    @Column(name = "channel_target", nullable = false, length = 500)
    var channelTarget: String = ""
        private set

    @Column(name = "active", nullable = false)
    var active: Boolean = true
        private set

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.EPOCH
        private set

    @Column(name = "last_evaluated_at")
    var lastEvaluatedAt: Instant? = null

    fun toDomain(query: SearchQuery): SavedSearch = SavedSearch(
        SavedSearchId.of(id), ownerId, label, query,
        NotifyChannel(channelType, channelTarget),
        active, createdAt, lastEvaluatedAt
    )

    companion object {
        @JvmStatic
        fun from(s: SavedSearch, queryJson: String): SavedSearchJpaEntity {
            val e = SavedSearchJpaEntity()
            e.id = s.id.value
            e.ownerId = s.ownerId
            e.label = s.label
            e.queryJson = queryJson
            e.channelType = s.notifyChannel.type
            e.channelTarget = s.notifyChannel.target
            e.active = s.active
            e.createdAt = s.createdAt
            e.lastEvaluatedAt = s.lastEvaluatedAt
            return e
        }
    }
}
