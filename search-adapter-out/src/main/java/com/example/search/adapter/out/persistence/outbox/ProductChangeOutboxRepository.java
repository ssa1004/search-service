package com.example.search.adapter.out.persistence.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProductChangeOutboxRepository extends JpaRepository<ProductChangeOutboxEntity, Long> {

    /**
     * 미발행 outbox 한 batch — 오래된 순 (id asc) 으로 가져와 순서 보존. relay 가 발행 후 bulk update
     * 로 published_at 채움.
     */
    @Query("SELECT o FROM ProductChangeOutboxEntity o WHERE o.publishedAt IS NULL ORDER BY o.id ASC")
    List<ProductChangeOutboxEntity> findUnpublished(Pageable pageable);
}
