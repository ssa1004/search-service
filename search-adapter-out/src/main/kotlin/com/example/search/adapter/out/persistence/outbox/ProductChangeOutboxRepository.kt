package com.example.search.adapter.out.persistence.outbox

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

interface ProductChangeOutboxRepository : JpaRepository<ProductChangeOutboxEntity, Long> {

    /**
     * 미발행 outbox 한 batch — 오래된 순 (id asc) 으로 가져와 순서 보존. relay 가 발행 후 bulk update
     * 로 published_at 채움.
     */
    @Query("SELECT o FROM ProductChangeOutboxEntity o WHERE o.publishedAt IS NULL ORDER BY o.id ASC")
    fun findUnpublished(pageable: Pageable): List<ProductChangeOutboxEntity>

    /**
     * retention 정리 (ADR-0014) — 발행 완료 + cutoff 이전 row 한 batch 삭제. 반환 = 실제 삭제된 수.
     *
     * Postgres / H2 둘 다 지원하는 표준 형태. JPA 가 생성하는 native delete 를 한정된 row 수
     * 안에 끝내기 위해 PK 부분 query 후 in() 으로 위임.
     */
    @Modifying
    @Transactional
    @Query(
        value = "DELETE FROM product_change_outbox WHERE id IN (" +
            "  SELECT id FROM product_change_outbox " +
            "  WHERE published_at IS NOT NULL AND published_at < :cutoff " +
            "  ORDER BY id ASC FETCH FIRST :batchSize ROWS ONLY)",
        nativeQuery = true
    )
    fun deletePublishedBefore(@Param("cutoff") cutoff: Instant, @Param("batchSize") batchSize: Int): Int
}
