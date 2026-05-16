package com.example.search.application.savedsearch.service

import com.example.search.application.savedsearch.port.`in`.ListMySavedSearchesUseCase
import com.example.search.application.savedsearch.port.out.SavedSearchRepository
import com.example.search.domain.savedsearch.SavedSearch
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ListMySavedSearchesService(
    private val repository: SavedSearchRepository
) : ListMySavedSearchesUseCase {

    @Transactional(readOnly = true)
    override fun findByOwner(ownerId: String): List<SavedSearch> {
        return repository.findByOwner(ownerId)
    }
}
