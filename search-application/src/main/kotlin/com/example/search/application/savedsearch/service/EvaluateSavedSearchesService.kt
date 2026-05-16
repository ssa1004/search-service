package com.example.search.application.savedsearch.service

import com.example.search.application.savedsearch.port.`in`.EvaluateSavedSearchesUseCase
import com.example.search.application.savedsearch.port.out.SavedSearchAlertPublisher
import com.example.search.application.savedsearch.port.out.SavedSearchMatchFinder
import com.example.search.application.savedsearch.port.out.SavedSearchRepository
import com.example.search.domain.savedsearch.SavedSearch
import com.example.search.domain.savedsearch.SavedSearchAlert
import com.example.search.domain.savedsearch.SavedSearchId
import org.slf4j.LoggerFactory
import java.time.Clock

/**
 * 모든 active SavedSearch 평가 → 신규 매치 발견 시 알림 발행.
 *
 * 한 batch (BATCH_SIZE) 씩 cursor 기반 paging — 큰 테이블도 메모리 안 터지고 안전.
 *
 * 한 SavedSearch 평가 실패는 다른 SavedSearch 처리에 영향 X — try/catch 로 격리해 다음 row 로
 * 진행. 실패한 row 는 lastEvaluatedAt 갱신 안 됨 → 다음 사이클에 재평가 (재시도).
 *
 * 빈 등록은 bootstrap 의 `SavedSearchConfig` 가 책임 — match finder + publisher 빈이
 * 모두 존재할 때만 활성 (memory 모드 / kafka 비활성 환경에서 미동작).
 */
class EvaluateSavedSearchesService(
    private val repository: SavedSearchRepository,
    private val matchFinder: SavedSearchMatchFinder,
    private val publisher: SavedSearchAlertPublisher,
    private val clock: Clock
) : EvaluateSavedSearchesUseCase {

    override fun evaluateAll(): Int {
        var evaluated = 0
        var firedAlerts = 0
        var cursor: SavedSearchId? = null

        while (true) {
            val batch = repository.findActiveBatchAfter(cursor, BATCH_SIZE)
            if (batch.isEmpty()) break

            for (saved in batch) {
                if (evaluateOne(saved)) {
                    firedAlerts++
                }
                evaluated++
                cursor = saved.id
            }

            if (batch.size < BATCH_SIZE) break
        }

        if (evaluated > 0) {
            log.info("SavedSearch 평가 완료 evaluated={} alertsFired={}", evaluated, firedAlerts)
        }
        return evaluated
    }

    /**
     * @return 알림 발행 여부 — 매치 0건이면 false.
     */
    private fun evaluateOne(saved: SavedSearch): Boolean {
        val now = clock.instant()
        return try {
            val matches = matchFinder.findNewMatches(
                saved, saved.evaluationCursor(), MAX_MATCHES_PER_SEARCH
            )

            if (matches.isNotEmpty()) {
                val alert = SavedSearchAlert(
                    saved.id, saved.ownerId, saved.label, matches, matches.size.toLong(), now
                )
                publisher.publish(alert, saved.notifyChannel)
            }
            // 매치 유무와 무관하게 평가 완료 — 다음 사이클의 since 기준점.
            repository.touchEvaluatedAt(saved.id, now)
            matches.isNotEmpty()
        } catch (e: RuntimeException) {
            // 한 row 실패가 batch 전체를 멈추지 않게. lastEvaluatedAt 갱신 X → 재시도 보장.
            log.warn(
                "SavedSearch 평가 실패 id={} owner={} err={}",
                saved.id.value, saved.ownerId, e.message
            )
            false
        }
    }

    companion object {
        /** 한 batch — DB / ES 양쪽에 부담을 분산. 테스트에서 검증 가능하도록 public. */
        const val BATCH_SIZE: Int = 100

        /** 한 SavedSearch 가 한 사이클에 반환하는 최대 product 수. 알림 message 폭주 방지. */
        const val MAX_MATCHES_PER_SEARCH: Int = 50

        private val log = LoggerFactory.getLogger(EvaluateSavedSearchesService::class.java)
    }
}
