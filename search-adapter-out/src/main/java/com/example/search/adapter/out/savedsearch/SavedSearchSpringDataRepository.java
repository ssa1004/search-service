package com.example.search.adapter.out.savedsearch;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface SavedSearchSpringDataRepository extends JpaRepository<SavedSearchJpaEntity, String> {

    List<SavedSearchJpaEntity> findByOwnerIdOrderByCreatedAtDesc(String ownerId);

    long countByOwnerId(String ownerId);

    /**
     * cursor 기반 paging — id > cursor 인 active row 한 batch (id asc). cursor 가 null 이면 처음부터.
     */
    @Query("SELECT s FROM SavedSearchJpaEntity s "
            + "WHERE s.active = true AND (:cursor IS NULL OR s.id > :cursor) "
            + "ORDER BY s.id ASC")
    List<SavedSearchJpaEntity> findActiveAfter(@Param("cursor") String cursor, Pageable pageable);

    /**
     * 평가 완료 시 lastEvaluatedAt 만 갱신. 전체 record save 보다 가볍다 (1 row UPDATE).
     */
    @Modifying
    @Query("UPDATE SavedSearchJpaEntity s SET s.lastEvaluatedAt = :at WHERE s.id = :id")
    int updateLastEvaluatedAt(@Param("id") String id, @Param("at") Instant at);
}
