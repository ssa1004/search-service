package com.example.search.application.savedsearch.port.out;

import com.example.search.domain.savedsearch.SavedSearch;
import com.example.search.domain.savedsearch.SavedSearchId;

import java.util.List;
import java.util.Optional;

/**
 * SavedSearch 영속화 port. owner 기준 / id 기준 조회 + active 필터링 + insert/update/delete.
 *
 * <p>스케줄러는 큰 테이블을 한 번에 읽지 않도록 페이지 단위 ({@code findActiveBatch}) 로 가져간다.
 * 100k+ 규모는 병렬 worker + 파티셔닝이 필요하지만 그 단계 전까지는 단일 polling 으로 충분.</p>
 */
public interface SavedSearchRepository {

    SavedSearch save(SavedSearch savedSearch);

    Optional<SavedSearch> findById(SavedSearchId id);

    List<SavedSearch> findByOwner(String ownerId);

    long countByOwner(String ownerId);

    void deleteById(SavedSearchId id);

    /**
     * active = true 인 SavedSearch 한 batch 를 id asc 순으로. cursor 는 마지막 id (exclusive).
     * 스케줄러가 페이지를 누적 호출하며 끝까지 평가.
     */
    List<SavedSearch> findActiveBatchAfter(SavedSearchId cursor, int batchSize);

    /**
     * 평가 후 lastEvaluatedAt 만 partial update — 전체 record save 보다 가볍다.
     */
    void touchEvaluatedAt(SavedSearchId id, java.time.Instant evaluatedAt);
}
