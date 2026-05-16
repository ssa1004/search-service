package com.example.search.application.savedsearch.port.out

import com.example.search.domain.savedsearch.SavedSearch
import com.example.search.domain.savedsearch.SavedSearchId
import java.time.Instant
import java.util.Optional

/**
 * SavedSearch 영속화 port. owner 기준 / id 기준 조회 + active 필터링 + insert/update/delete.
 *
 * 스케줄러는 큰 테이블을 한 번에 읽지 않도록 페이지 단위 ([findActiveBatchAfter]) 로 가져간다.
 * 100k+ 규모는 병렬 worker + 파티셔닝이 필요하지만 그 단계 전까지는 단일 polling 으로 충분.
 */
interface SavedSearchRepository {

    fun save(savedSearch: SavedSearch): SavedSearch

    fun findById(id: SavedSearchId): Optional<SavedSearch>

    fun findByOwner(ownerId: String): List<SavedSearch>

    fun countByOwner(ownerId: String): Long

    fun deleteById(id: SavedSearchId)

    /**
     * active = true 인 SavedSearch 한 batch 를 id asc 순으로. cursor 는 마지막 id (exclusive).
     * 스케줄러가 페이지를 누적 호출하며 끝까지 평가.
     */
    fun findActiveBatchAfter(cursor: SavedSearchId?, batchSize: Int): List<SavedSearch>

    /**
     * 평가 후 lastEvaluatedAt 만 partial update — 전체 record save 보다 가볍다.
     */
    fun touchEvaluatedAt(id: SavedSearchId, evaluatedAt: Instant)
}
