package com.example.search.application.synonym.service;

import com.example.search.application.synonym.port.in.DeleteSynonymGroupUseCase;
import com.example.search.application.synonym.port.out.SynonymGroupRepository;
import com.example.search.domain.synonym.SynonymGroupId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeleteSynonymGroupService implements DeleteSynonymGroupUseCase {

    private final SynonymGroupRepository repository;

    @Override
    @Transactional
    public void delete(SynonymGroupId id) {
        repository.deleteById(id);
        log.info("synonym 그룹 삭제 id={}", id.value());
    }
}
