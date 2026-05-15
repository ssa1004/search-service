package com.example.search.domain.savedsearch

import com.example.search.domain.query.SearchQuery
import java.time.Instant

/**
 * 사용자가 저장한 검색 조건 + 알림 설정.
 *
 * 흐름:
 * 1. 사용자가 현재 검색 query 를 "저장" 요청 → [SavedSearch] 생성.
 * 2. 스케줄러가 5분마다 모든 active SavedSearch 를 평가 — `lastEvaluatedAt` 이후 신규 매치.
 * 3. 매치 발견 시 `SavedSearchAlertPublisher` 로 알림 발행.
 *
 * `lastEvaluatedAt` 은 평가가 끝난 시점 — 매치 여부와 무관하게 갱신. 매치된 시점만 별도로
 * 추적하려면 별도 record (SavedSearchMatch) 가 책임진다.
 *
 * active 상태가 false 이면 스케줄러가 건너뜀 — 사용자가 일시 중지 / 재개 가능. 영구 삭제는 별도
 * delete use case.
 */
@JvmRecord
data class SavedSearch(
    val id: SavedSearchId,
    val ownerId: String,
    val label: String,
    val query: SearchQuery,
    val notifyChannel: NotifyChannel,
    val active: Boolean,
    val createdAt: Instant,
    // 신규 등록 직후에는 평가 이력이 없어 null — Java record 와 동일하게 nullable.
    val lastEvaluatedAt: Instant?
) {
    init {
        require(ownerId.isNotBlank()) { "ownerId 빈 값 불가" }
        require(label.isNotBlank()) { "label 빈 값 불가" }
        require(label.length <= MAX_LABEL_LENGTH) {
            "label 길이 $MAX_LABEL_LENGTH 이하: ${label.length}"
        }
    }

    /** 평가 완료 시 lastEvaluatedAt 만 갱신한 새 인스턴스. record 의 immutability 유지. */
    fun markEvaluated(at: Instant): SavedSearch =
        SavedSearch(id, ownerId, label, query, notifyChannel, active, createdAt, at)

    fun deactivate(): SavedSearch =
        SavedSearch(id, ownerId, label, query, notifyChannel, false, createdAt, lastEvaluatedAt)

    /** 스케줄러가 첫 평가 직전 lastEvaluatedAt 을 createdAt 으로 fallback — 신규 등록 시 과거 매치 폭주 방지. */
    fun evaluationCursor(): Instant = lastEvaluatedAt ?: createdAt

    companion object {
        /** 한 사용자가 등록 가능한 SavedSearch 상한 — 무한 등록 방지. */
        const val MAX_PER_OWNER: Int = 50

        /** 라벨 길이 상한 — DB column 200 에 맞춤. */
        const val MAX_LABEL_LENGTH: Int = 200

        /** 신규 등록 — id 자동 발급, active=true, lastEvaluatedAt=null. */
        @JvmStatic
        fun create(
            ownerId: String,
            label: String,
            query: SearchQuery,
            channel: NotifyChannel,
            now: Instant
        ): SavedSearch = SavedSearch(
            SavedSearchId.newId(), ownerId, label, query, channel, true, now, null
        )
    }
}
