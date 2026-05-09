package com.example.search.adapter.out.savedsearch;

import com.example.search.domain.savedsearch.NotifyChannel;
import com.example.search.domain.savedsearch.SavedSearch;
import com.example.search.domain.savedsearch.SavedSearchId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * SavedSearch persistence 매핑.
 *
 * <p>{@code queryJson} 은 도메인 {@link com.example.search.domain.query.SearchQuery} 의 직렬화 결과를
 * 그대로 보관 — 컬럼 모양은 안정적이고, query 구조 변경 (필드 추가) 시 마이그레이션 부담이 적다.
 * 단점은 query 안의 특정 필드로 SQL 검색 불가 — 운영 자체엔 그게 필요 없어서 trade-off 수용.</p>
 *
 * <p>인덱스:</p>
 * <ul>
 *   <li>{@code (owner_id)} — 사용자별 목록 조회.</li>
 *   <li>{@code (active, id)} — 스케줄러 cursor paging.</li>
 * </ul>
 */
@Entity
@Table(name = "saved_searches", indexes = {
        @Index(name = "ix_saved_searches_owner", columnList = "owner_id"),
        @Index(name = "ix_saved_searches_active_id", columnList = "active, id")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class SavedSearchJpaEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "owner_id", nullable = false, length = 64)
    private String ownerId;

    @Column(name = "label", nullable = false, length = 200)
    private String label;

    @Column(name = "query_json", nullable = false, columnDefinition = "TEXT")
    private String queryJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel_type", nullable = false, length = 16)
    private NotifyChannel.Type channelType;

    @Column(name = "channel_target", nullable = false, length = 500)
    private String channelTarget;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Setter
    @Column(name = "last_evaluated_at")
    private Instant lastEvaluatedAt;

    public static SavedSearchJpaEntity from(SavedSearch s, String queryJson) {
        SavedSearchJpaEntity e = new SavedSearchJpaEntity();
        e.id = s.id().value();
        e.ownerId = s.ownerId();
        e.label = s.label();
        e.queryJson = queryJson;
        e.channelType = s.notifyChannel().type();
        e.channelTarget = s.notifyChannel().target();
        e.active = s.active();
        e.createdAt = s.createdAt();
        e.lastEvaluatedAt = s.lastEvaluatedAt();
        return e;
    }

    public SavedSearch toDomain(com.example.search.domain.query.SearchQuery query) {
        return new SavedSearch(
                SavedSearchId.of(id), ownerId, label, query,
                new NotifyChannel(channelType, channelTarget),
                active, createdAt, lastEvaluatedAt);
    }
}
