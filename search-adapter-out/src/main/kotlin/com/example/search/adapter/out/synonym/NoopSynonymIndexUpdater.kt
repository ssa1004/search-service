package com.example.search.adapter.out.synonym

import com.example.search.application.synonym.port.out.SynonymIndexUpdaterPort
import com.example.search.domain.synonym.SynonymGroup
import org.slf4j.LoggerFactory

/**
 * memory 모드 / 단위 테스트용 no-op 어댑터 — RDB 등록만 검증하고 ES 호출은 무시.
 *
 * 실 운영 모드에서는 [ElasticsearchSynonymIndexUpdater] 가 이 빈을 대체한다.
 */
class NoopSynonymIndexUpdater : SynonymIndexUpdaterPort {

    override fun reload(groups: List<SynonymGroup>): Int {
        log.debug("synonym reload skipped (memory 모드) groups={}", groups.size)
        return groups.size
    }

    companion object {
        private val log = LoggerFactory.getLogger(NoopSynonymIndexUpdater::class.java)
    }
}
