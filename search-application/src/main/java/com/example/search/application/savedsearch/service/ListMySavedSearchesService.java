package com.example.search.application.savedsearch.service;

import com.example.search.application.savedsearch.port.in.ListMySavedSearchesUseCase;
import com.example.search.application.savedsearch.port.out.SavedSearchRepository;
import com.example.search.domain.savedsearch.SavedSearch;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListMySavedSearchesService implements ListMySavedSearchesUseCase {

    private final SavedSearchRepository repository;

    @Override
    @Transactional(readOnly = true)
    public List<SavedSearch> findByOwner(String ownerId) {
        return repository.findByOwner(ownerId);
    }
}
