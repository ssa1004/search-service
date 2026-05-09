package com.example.search.application.savedsearch.service;

import com.example.search.application.savedsearch.command.SaveSearchCommand;
import com.example.search.application.savedsearch.port.in.SaveSearchUseCase;
import com.example.search.application.savedsearch.port.in.SavedSearchQuotaExceededException;
import com.example.search.application.savedsearch.port.out.SavedSearchRepository;
import com.example.search.domain.savedsearch.SavedSearch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * SavedSearch 등록 use case.
 *
 * <p>quota 검사 → 도메인 객체 생성 → repository 저장. 트랜잭션 안에서 count + insert 를 같이 보지만
 * 동시 등록 race 에서 정확히 50개를 넘길 가능성은 무시 (eventual cap). 하드 cap 이 필요하면 unique
 * constraint 또는 advisory lock.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SaveSearchService implements SaveSearchUseCase {

    private final SavedSearchRepository repository;
    private final Clock clock;

    @Override
    @Transactional
    public SavedSearch save(SaveSearchCommand command) {
        long current = repository.countByOwner(command.ownerId());
        if (current >= SavedSearch.MAX_PER_OWNER) {
            throw new SavedSearchQuotaExceededException(command.ownerId(), (int) current);
        }
        SavedSearch saved = SavedSearch.create(
                command.ownerId(), command.label(), command.query(),
                command.notifyChannel(), clock.instant());
        SavedSearch persisted = repository.save(saved);
        log.info("SavedSearch 등록 owner={} id={} label='{}' channel={}/{}",
                persisted.ownerId(), persisted.id().value(), persisted.label(),
                persisted.notifyChannel().type(), persisted.notifyChannel().target());
        return persisted;
    }
}
