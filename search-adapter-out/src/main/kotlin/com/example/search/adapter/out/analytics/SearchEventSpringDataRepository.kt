package com.example.search.adapter.out.analytics

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface SearchEventSpringDataRepository : JpaRepository<SearchEventJpaEntity, Long> {

    /**
     * keyword 별 검색 횟수 — top queries. native query 가 아니라 JPQL 로 두 가지 type 안전 보장.
     * 결과의 두 번째 컬럼은 long count.
     */
    @Query(
        "SELECT e.keyword AS keyword, COUNT(e) AS cnt FROM SearchEventJpaEntity e " +
            "WHERE e.occurredAt >= :from AND e.occurredAt < :to " +
            "GROUP BY e.keyword " +
            "ORDER BY COUNT(e) DESC"
    )
    fun topKeywords(@Param("from") from: Instant, @Param("to") to: Instant, pageable: Pageable): List<Array<Any>>

    /**
     * 결과 0건이 자주 났던 keyword — 동의어 / 사전 보강 후보.
     */
    @Query(
        "SELECT e.keyword AS keyword, COUNT(e) AS cnt FROM SearchEventJpaEntity e " +
            "WHERE e.occurredAt >= :from AND e.occurredAt < :to " +
            "AND e.resultCount = 0 " +
            "GROUP BY e.keyword " +
            "ORDER BY COUNT(e) DESC"
    )
    fun zeroResultKeywords(
        @Param("from") from: Instant,
        @Param("to") to: Instant,
        pageable: Pageable
    ): List<Array<Any>>

    /**
     * 구간 latency — percentile 계산을 위해 정렬된 전체 latency 만 받아 메모리에서 계산.
     * H2 / Postgres 양쪽 호환 (Postgres percentile_cont 은 H2 미지원).
     * 큰 표본에서는 reservoir sampling / OLAP 도구로 옮기는 게 자연스럽다.
     */
    @Query(
        "SELECT e.latencyMs FROM SearchEventJpaEntity e " +
            "WHERE e.occurredAt >= :from AND e.occurredAt < :to " +
            "ORDER BY e.latencyMs ASC"
    )
    fun latenciesAsc(@Param("from") from: Instant, @Param("to") to: Instant): List<Long>

    @Query(
        "SELECT COUNT(e) FROM SearchEventJpaEntity e " +
            "WHERE e.occurredAt >= :from AND e.occurredAt < :to " +
            "AND e.resultCount > 0"
    )
    fun countSearchesWithResults(@Param("from") from: Instant, @Param("to") to: Instant): Long
}
