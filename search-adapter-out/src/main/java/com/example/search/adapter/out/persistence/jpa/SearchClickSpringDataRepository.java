package com.example.search.adapter.out.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface SearchClickSpringDataRepository extends JpaRepository<SearchClickJpaEntity, Long> {

    /**
     * 특정 product 의 since 이후 click 합산. boost rule 의 popularity 시그널 — clickCount 자체를 ES 에
     * 캐시하지만, reindex 시 정합성 회복용 진실값.
     */
    @Query("SELECT COUNT(c) FROM SearchClickJpaEntity c WHERE c.productId = :productId "
            + "AND c.occurredAt >= :since")
    long countByProductSince(@Param("productId") String productId, @Param("since") Instant since);

    /**
     * 구간 클릭 수 — CTR 분자 (ADR-0018).
     */
    @Query("SELECT COUNT(c) FROM SearchClickJpaEntity c "
            + "WHERE c.occurredAt >= :from AND c.occurredAt < :to")
    long countByOccurredBetween(@Param("from") Instant from, @Param("to") Instant to);
}
