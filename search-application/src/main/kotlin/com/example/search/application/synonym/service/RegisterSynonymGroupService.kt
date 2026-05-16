package com.example.search.application.synonym.service

import com.example.search.application.synonym.command.RegisterSynonymGroupCommand
import com.example.search.application.synonym.port.`in`.RegisterSynonymGroupUseCase
import com.example.search.application.synonym.port.out.SynonymGroupRepository
import com.example.search.domain.synonym.SynonymGroup
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
class RegisterSynonymGroupService(
    private val repository: SynonymGroupRepository,
    private val clock: Clock
) : RegisterSynonymGroupUseCase {

    @Transactional
    override fun register(command: RegisterSynonymGroupCommand): SynonymGroup {
        val group = SynonymGroup.create(
            command.terms, command.direction, clock.instant(), command.operatorId
        )
        val saved = repository.save(group)
        log.info(
            "synonym 그룹 등록 id={} terms={} direction={} by={}",
            saved.id.value, saved.terms, saved.direction, saved.updatedBy
        )
        return saved
    }

    companion object {
        private val log = LoggerFactory.getLogger(RegisterSynonymGroupService::class.java)
    }
}
