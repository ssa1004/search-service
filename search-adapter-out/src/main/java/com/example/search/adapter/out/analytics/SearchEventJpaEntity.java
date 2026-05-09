package com.example.search.adapter.out.analytics;

import com.example.search.domain.analytics.SearchEvent;
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
 * 검색 이벤트 테이블 — 분석 자료 (ADR-0018).
 *
 * <p>인덱스:</p>
 * <ul>
 *   <li>{@code (occurred_at)} — 모든 분석 query 가 시간 구간 필터.</li>
 *   <li>{@code (occurred_at, keyword)} — top queries / zero-result group-by 의 covering.</li>
 * </ul>
 *
 * <p>partition 은 후속 ADR — 월별 파티션이 필요한 규모는 검색 단위 1억건/월 이상.</p>
 */
@Entity
@Table(name = "search_events", indexes = {
        @Index(name = "ix_search_events_occurred", columnList = "occurred_at"),
        @Index(name = "ix_search_events_occurred_keyword", columnList = "occurred_at, keyword")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class SearchEventJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "search_id", nullable = false, length = 64)
    private String searchId;

    @Column(name = "keyword", nullable = false, length = 200)
    private String keyword;

    @Column(name = "user_id", length = 64)
    private String userId;

    @Column(name = "result_count", nullable = false)
    private long resultCount;

    @Column(name = "latency_ms", nullable = false)
    private long latencyMs;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    public static SearchEventJpaEntity from(SearchEvent e) {
        SearchEventJpaEntity entity = new SearchEventJpaEntity();
        entity.searchId = e.searchId();
        entity.keyword = e.keyword();
        entity.userId = e.userId();
        entity.resultCount = e.resultCount();
        entity.latencyMs = e.latencyMs();
        entity.occurredAt = e.occurredAt();
        return entity;
    }
}
