package com.example.search.domain.analytics

import java.time.Instant

/**
 * 한 번의 검색 호출의 관측 결과 — 모든 검색 종료 후 비동기 INSERT.
 *
 * 이 이벤트는 운영팀이 검색 품질을 보는 표준 분석 자료다:
 * - top queries — 가장 많이 검색된 keyword.
 * - zero result — 결과 0건이 자주 났던 keyword (= 동의어 / 사전 보강 후보).
 * - latency p50 / p95 / p99 — 검색 응답 분포.
 * - (클릭 데이터와 join) CTR — 검색 후 결과를 클릭한 비율.
 *
 * [keyword] 는 정규화된 표현 — 입력 그대로가 아니라 trim + lowercase 등 적용. 분석은
 * 표기 변동을 흡수해야 의미 있다.
 *
 * [searchId] 는 동일 검색 → 클릭 join 키 — `RecordSearchClickUseCase` 가 같은 id 로
 * 클릭 이벤트를 저장하므로 (searchId, productId) 로 CTR 계산 가능.
 */
@JvmRecord
data class SearchEvent(
    val searchId: String,
    val keyword: String,
    // 익명 사용자 검색은 userId 가 null — Java record 가 null 을 허용하므로 nullable 유지.
    val userId: String?,
    val resultCount: Long,
    val latencyMs: Long,
    val occurredAt: Instant
) {
    init {
        require(resultCount >= 0) { "resultCount 음수 불가: $resultCount" }
        require(latencyMs >= 0) { "latencyMs 음수 불가: $latencyMs" }
        require(keyword.length <= MAX_KEYWORD_LENGTH) {
            "keyword 길이 $MAX_KEYWORD_LENGTH 이하: ${keyword.length}"
        }
    }

    fun isZeroResult(): Boolean = resultCount == 0L

    companion object {
        /** 분석 group-by 의 cardinality 폭주를 막기 위해 keyword 길이 200 자 cap — DB 컬럼과 동일. */
        const val MAX_KEYWORD_LENGTH: Int = 200
    }
}
