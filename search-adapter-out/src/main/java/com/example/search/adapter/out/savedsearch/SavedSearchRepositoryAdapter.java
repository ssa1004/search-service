package com.example.search.adapter.out.savedsearch;

import com.example.search.application.savedsearch.port.out.SavedSearchRepository;
import com.example.search.domain.savedsearch.SavedSearch;
import com.example.search.domain.savedsearch.SavedSearchId;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class SavedSearchRepositoryAdapter implements SavedSearchRepository {

    private final SavedSearchSpringDataRepository delegate;
    private final SearchQueryJsonCodec codec;

    @Override
    public SavedSearch save(SavedSearch savedSearch) {
        SavedSearchJpaEntity entity = SavedSearchJpaEntity.from(
                savedSearch, codec.serialize(savedSearch.query()));
        SavedSearchJpaEntity saved = delegate.save(entity);
        return saved.toDomain(savedSearch.query());
    }

    @Override
    public Optional<SavedSearch> findById(SavedSearchId id) {
        return delegate.findById(id.value())
                .map(e -> e.toDomain(codec.deserialize(e.getQueryJson())));
    }

    @Override
    public List<SavedSearch> findByOwner(String ownerId) {
        return delegate.findByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                .map(e -> e.toDomain(codec.deserialize(e.getQueryJson())))
                .toList();
    }

    @Override
    public long countByOwner(String ownerId) {
        return delegate.countByOwnerId(ownerId);
    }

    @Override
    public void deleteById(SavedSearchId id) {
        delegate.deleteById(id.value());
    }

    @Override
    public List<SavedSearch> findActiveBatchAfter(SavedSearchId cursor, int batchSize) {
        String cursorValue = cursor != null ? cursor.value() : null;
        return delegate.findActiveAfter(cursorValue, PageRequest.of(0, batchSize)).stream()
                .map(e -> e.toDomain(codec.deserialize(e.getQueryJson())))
                .toList();
    }

    @Override
    public void touchEvaluatedAt(SavedSearchId id, Instant evaluatedAt) {
        delegate.updateLastEvaluatedAt(id.value(), evaluatedAt);
    }
}
