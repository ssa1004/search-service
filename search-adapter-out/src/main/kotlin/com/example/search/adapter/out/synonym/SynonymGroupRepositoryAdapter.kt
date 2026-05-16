package com.example.search.adapter.out.synonym

import com.example.search.application.synonym.port.out.SynonymGroupRepository
import com.example.search.domain.synonym.SynonymGroup
import com.example.search.domain.synonym.SynonymGroupId
import org.springframework.stereotype.Component
import java.util.Optional

@Component
class SynonymGroupRepositoryAdapter(
    private val delegate: SynonymGroupSpringDataRepository
) : SynonymGroupRepository {

    override fun save(group: SynonymGroup): SynonymGroup {
        val entity = SynonymGroupJpaEntity.from(group)
        return delegate.save(entity).toDomain()
    }

    override fun findById(id: SynonymGroupId): Optional<SynonymGroup> =
        delegate.findById(id.value).map { it.toDomain() }

    override fun findAll(): List<SynonymGroup> =
        delegate.findAllByOrderByUpdatedAtDesc().map { it.toDomain() }

    override fun deleteById(id: SynonymGroupId) {
        delegate.deleteById(id.value)
    }

    override fun count(): Long = delegate.count()
}
