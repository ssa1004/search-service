package com.example.search.adapter.out.savedsearch

import com.example.search.application.savedsearch.port.out.SavedSearchRepository
import com.example.search.domain.savedsearch.SavedSearch
import com.example.search.domain.savedsearch.SavedSearchId
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.Optional

@Component
class SavedSearchRepositoryAdapter(
    private val delegate: SavedSearchSpringDataRepository,
    private val codec: SearchQueryJsonCodec
) : SavedSearchRepository {

    override fun save(savedSearch: SavedSearch): SavedSearch {
        val entity = SavedSearchJpaEntity.from(savedSearch, codec.serialize(savedSearch.query))
        val saved = delegate.save(entity)
        return saved.toDomain(savedSearch.query)
    }

    override fun findById(id: SavedSearchId): Optional<SavedSearch> =
        delegate.findById(id.value).map { it.toDomain(codec.deserialize(it.queryJson)) }

    override fun findByOwner(ownerId: String): List<SavedSearch> =
        delegate.findByOwnerIdOrderByCreatedAtDesc(ownerId)
            .map { it.toDomain(codec.deserialize(it.queryJson)) }

    override fun countByOwner(ownerId: String): Long = delegate.countByOwnerId(ownerId)

    override fun deleteById(id: SavedSearchId) {
        delegate.deleteById(id.value)
    }

    override fun findActiveBatchAfter(cursor: SavedSearchId?, batchSize: Int): List<SavedSearch> {
        val cursorValue = cursor?.value
        return delegate.findActiveAfter(cursorValue, PageRequest.of(0, batchSize))
            .map { it.toDomain(codec.deserialize(it.queryJson)) }
    }

    override fun touchEvaluatedAt(id: SavedSearchId, evaluatedAt: Instant) {
        delegate.updateLastEvaluatedAt(id.value, evaluatedAt)
    }
}
