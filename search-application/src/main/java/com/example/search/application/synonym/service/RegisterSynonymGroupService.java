package com.example.search.application.synonym.service;

import com.example.search.application.synonym.command.RegisterSynonymGroupCommand;
import com.example.search.application.synonym.port.in.RegisterSynonymGroupUseCase;
import com.example.search.application.synonym.port.out.SynonymGroupRepository;
import com.example.search.domain.synonym.SynonymGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegisterSynonymGroupService implements RegisterSynonymGroupUseCase {

    private final SynonymGroupRepository repository;
    private final Clock clock;

    @Override
    @Transactional
    public SynonymGroup register(RegisterSynonymGroupCommand command) {
        SynonymGroup group = SynonymGroup.create(
                command.terms(), command.direction(), clock.instant(), command.operatorId());
        SynonymGroup saved = repository.save(group);
        log.info("synonym 그룹 등록 id={} terms={} direction={} by={}",
                saved.id().value(), saved.terms(), saved.direction(), saved.updatedBy());
        return saved;
    }
}
