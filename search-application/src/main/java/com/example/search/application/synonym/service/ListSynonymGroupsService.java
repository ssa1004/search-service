package com.example.search.application.synonym.service;

import com.example.search.application.synonym.port.in.ListSynonymGroupsUseCase;
import com.example.search.application.synonym.port.out.SynonymGroupRepository;
import com.example.search.domain.synonym.SynonymGroup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListSynonymGroupsService implements ListSynonymGroupsUseCase {

    private final SynonymGroupRepository repository;

    @Override
    @Transactional(readOnly = true)
    public List<SynonymGroup> listAll() {
        return repository.findAll();
    }
}
