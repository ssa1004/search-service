package com.example.search.application.synonym.service

import com.example.search.application.synonym.port.`in`.DeleteSynonymGroupUseCase
import com.example.search.application.synonym.port.out.SynonymGroupRepository
import com.example.search.domain.synonym.SynonymGroupId
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DeleteSynonymGroupService(
    private val repository: SynonymGroupRepository
) : DeleteSynonymGroupUseCase {

    @Transactional
    override fun delete(id: SynonymGroupId) {
        repository.deleteById(id)
        log.info("synonym 그룹 삭제 id={}", id.value)
    }

    companion object {
        private val log = LoggerFactory.getLogger(DeleteSynonymGroupService::class.java)
    }
}
