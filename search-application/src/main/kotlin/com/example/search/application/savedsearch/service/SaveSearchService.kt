package com.example.search.application.savedsearch.service

import com.example.search.application.savedsearch.command.SaveSearchCommand
import com.example.search.application.savedsearch.port.`in`.SaveSearchUseCase
import com.example.search.application.savedsearch.port.`in`.SavedSearchQuotaExceededException
import com.example.search.application.savedsearch.port.out.SavedSearchRepository
import com.example.search.domain.savedsearch.SavedSearch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * SavedSearch 등록 use case.
 *
 * quota 검사 → 도메인 객체 생성 → repository 저장. 트랜잭션 안에서 count + insert 를 같이 보지만
 * 동시 등록 race 에서 정확히 50개를 넘길 가능성은 무시 (eventual cap). 하드 cap 이 필요하면 unique
 * constraint 또는 advisory lock.
 */
@Service
class SaveSearchService(
    private val repository: SavedSearchRepository,
    private val clock: Clock
) : SaveSearchUseCase {

    @Transactional
    override fun save(command: SaveSearchCommand): SavedSearch {
        val current = repository.countByOwner(command.ownerId)
        if (current >= SavedSearch.MAX_PER_OWNER) {
            throw SavedSearchQuotaExceededException(command.ownerId, current.toInt())
        }
        val saved = SavedSearch.create(
            command.ownerId, command.label, command.query,
            command.notifyChannel, clock.instant()
        )
        val persisted = repository.save(saved)
        log.info(
            "SavedSearch 등록 owner={} id={} label='{}' channel={}/{}",
            persisted.ownerId, persisted.id.value, persisted.label,
            persisted.notifyChannel.type, persisted.notifyChannel.target
        )
        return persisted
    }

    companion object {
        private val log = LoggerFactory.getLogger(SaveSearchService::class.java)
    }
}
