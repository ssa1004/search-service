package com.example.search.adapter.out.synonym;

import com.example.search.application.synonym.port.out.SynonymGroupRepository;
import com.example.search.domain.synonym.SynonymGroup;
import com.example.search.domain.synonym.SynonymGroupId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class SynonymGroupRepositoryAdapter implements SynonymGroupRepository {

    private final SynonymGroupSpringDataRepository delegate;

    @Override
    public SynonymGroup save(SynonymGroup group) {
        SynonymGroupJpaEntity entity = SynonymGroupJpaEntity.from(group);
        return delegate.save(entity).toDomain();
    }

    @Override
    public Optional<SynonymGroup> findById(SynonymGroupId id) {
        return delegate.findById(id.value()).map(SynonymGroupJpaEntity::toDomain);
    }

    @Override
    public List<SynonymGroup> findAll() {
        return delegate.findAllByOrderByUpdatedAtDesc().stream()
                .map(SynonymGroupJpaEntity::toDomain)
                .toList();
    }

    @Override
    public void deleteById(SynonymGroupId id) {
        delegate.deleteById(id.value());
    }

    @Override
    public long count() {
        return delegate.count();
    }
}
