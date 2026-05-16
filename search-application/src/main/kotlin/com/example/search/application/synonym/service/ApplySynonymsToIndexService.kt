package com.example.search.application.synonym.service

import com.example.search.application.synonym.port.`in`.ApplySynonymsToIndexUseCase
import com.example.search.application.synonym.port.out.SynonymGroupRepository
import com.example.search.application.synonym.port.out.SynonymIndexUpdaterPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * RDB 의 모든 SynonymGroup → ES 인덱스 settings 에 reload.
 *
 * readOnly tx 로 조회만 — ES 호출은 tx 밖에서 일어나도 무방 (ES 는 자체 트랜잭션이 없음).
 * 호출 도중 ES 측 실패는 RuntimeException 으로 호출자에게 전파 — 컨트롤러가 5xx 로 매핑.
 */
@Service
class ApplySynonymsToIndexService(
    private val repository: SynonymGroupRepository,
    private val updater: SynonymIndexUpdaterPort
) : ApplySynonymsToIndexUseCase {

    @Transactional(readOnly = true)
    override fun apply(): Int {
        val groups = repository.findAll()
        val applied = updater.reload(groups)
        log.info("synonym ES 적용 완료 groups={} applied={}", groups.size, applied)
        return applied
    }

    companion object {
        private val log = LoggerFactory.getLogger(ApplySynonymsToIndexService::class.java)
    }
}
