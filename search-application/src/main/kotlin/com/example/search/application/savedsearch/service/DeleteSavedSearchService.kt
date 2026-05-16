package com.example.search.application.savedsearch.service

import com.example.search.application.savedsearch.port.`in`.DeleteSavedSearchUseCase
import com.example.search.application.savedsearch.port.`in`.SavedSearchNotOwnedException
import com.example.search.application.savedsearch.port.out.SavedSearchRepository
import com.example.search.domain.savedsearch.SavedSearchId
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DeleteSavedSearchService(
    private val repository: SavedSearchRepository
) : DeleteSavedSearchUseCase {

    @Transactional
    override fun delete(ownerId: String, id: SavedSearchId) {
        val existing = repository.findById(id).orElse(null)
            // 멱등 — 이미 삭제됐으면 silent.
            ?: return
        if (existing.ownerId != ownerId) {
            throw SavedSearchNotOwnedException(ownerId, id)
        }
        repository.deleteById(id)
        log.info("SavedSearch 삭제 owner={} id={}", ownerId, id.value)
    }

    companion object {
        private val log = LoggerFactory.getLogger(DeleteSavedSearchService::class.java)
    }
}
