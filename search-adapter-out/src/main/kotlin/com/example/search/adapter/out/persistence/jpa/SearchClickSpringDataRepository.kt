package com.example.search.adapter.out.persistence.jpa

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface SearchClickSpringDataRepository : JpaRepository<SearchClickJpaEntity, Long> {

    /**
     * 특정 product 의 since 이후 click 합산. boost rule 의 popularity 시그널 — clickCount 자체를 ES 에
     * 캐시하지만, reindex 시 정합성 회복용 진실값.
     */
    @Query(
        "SELECT COUNT(c) FROM SearchClickJpaEntity c WHERE c.productId = :productId " +
            "AND c.occurredAt >= :since"
    )
    fun countByProductSince(@Param("productId") productId: String, @Param("since") since: Instant): Long

    /**
     * 구간 클릭 수 — CTR 분자 (ADR-0018).
     */
    @Query(
        "SELECT COUNT(c) FROM SearchClickJpaEntity c " +
            "WHERE c.occurredAt >= :from AND c.occurredAt < :to"
    )
    fun countByOccurredBetween(@Param("from") from: Instant, @Param("to") to: Instant): Long
}
