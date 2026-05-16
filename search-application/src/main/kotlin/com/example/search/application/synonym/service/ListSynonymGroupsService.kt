package com.example.search.application.synonym.service

import com.example.search.application.synonym.port.`in`.ListSynonymGroupsUseCase
import com.example.search.application.synonym.port.out.SynonymGroupRepository
import com.example.search.domain.synonym.SynonymGroup
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ListSynonymGroupsService(
    private val repository: SynonymGroupRepository
) : ListSynonymGroupsUseCase {

    @Transactional(readOnly = true)
    override fun listAll(): List<SynonymGroup> {
        return repository.findAll()
    }
}
