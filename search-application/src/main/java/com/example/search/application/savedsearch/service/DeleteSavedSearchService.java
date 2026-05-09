package com.example.search.application.savedsearch.service;

import com.example.search.application.savedsearch.port.in.DeleteSavedSearchUseCase;
import com.example.search.application.savedsearch.port.in.SavedSearchNotOwnedException;
import com.example.search.application.savedsearch.port.out.SavedSearchRepository;
import com.example.search.domain.savedsearch.SavedSearch;
import com.example.search.domain.savedsearch.SavedSearchId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeleteSavedSearchService implements DeleteSavedSearchUseCase {

    private final SavedSearchRepository repository;

    @Override
    @Transactional
    public void delete(String ownerId, SavedSearchId id) {
        SavedSearch existing = repository.findById(id).orElse(null);
        if (existing == null) {
            // 멱등 — 이미 삭제됐으면 silent.
            return;
        }
        if (!existing.ownerId().equals(ownerId)) {
            throw new SavedSearchNotOwnedException(ownerId, id);
        }
        repository.deleteById(id);
        log.info("SavedSearch 삭제 owner={} id={}", ownerId, id.value());
    }
}
