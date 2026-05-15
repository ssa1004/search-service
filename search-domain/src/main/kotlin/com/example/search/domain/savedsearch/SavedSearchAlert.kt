package com.example.search.domain.savedsearch

import com.example.search.domain.product.ProductId

/**
 * SavedSearch 매치 결과 — 알림 채널로 발행되는 메시지.
 *
 * `matchedProductIds` 는 신규 매치된 product 들 (이전 평가 이후 추가). 다운스트림 (사용자
 * push / email) 에서 ID 만으로 product 상세 lookup.
 *
 * 구독자 측이 이전 알림과 dedup 가능하도록 `firedAt` + `savedSearchId` 조합을 키로
 * 사용한다.
 *
 * record 의 compact constructor 가 `matchedProductIds` 를 방어 복사하므로 data class 가
 * 아닌 일반 class — equals / hashCode 는 정규화된 필드 기준으로 직접 정의한다.
 */
class SavedSearchAlert(
    savedSearchId: SavedSearchId,
    ownerId: String,
    label: String,
    matchedProductIds: List<ProductId>,
    totalNewMatches: Long,
    firedAt: java.time.Instant
) {

    @get:JvmName("savedSearchId")
    val savedSearchId: SavedSearchId

    @get:JvmName("ownerId")
    val ownerId: String

    @get:JvmName("label")
    val label: String

    @get:JvmName("matchedProductIds")
    val matchedProductIds: List<ProductId>

    @get:JvmName("totalNewMatches")
    val totalNewMatches: Long

    @get:JvmName("firedAt")
    val firedAt: java.time.Instant

    init {
        require(totalNewMatches >= 0) { "totalNewMatches 음수 불가: $totalNewMatches" }
        this.savedSearchId = savedSearchId
        this.ownerId = ownerId
        this.label = label
        this.matchedProductIds = java.util.List.copyOf(matchedProductIds)
        this.totalNewMatches = totalNewMatches
        this.firedAt = firedAt
    }

    fun isEmpty(): Boolean = matchedProductIds.isEmpty()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SavedSearchAlert) return false
        return savedSearchId == other.savedSearchId &&
            ownerId == other.ownerId &&
            label == other.label &&
            matchedProductIds == other.matchedProductIds &&
            totalNewMatches == other.totalNewMatches &&
            firedAt == other.firedAt
    }

    override fun hashCode(): Int {
        var result = savedSearchId.hashCode()
        result = 31 * result + ownerId.hashCode()
        result = 31 * result + label.hashCode()
        result = 31 * result + matchedProductIds.hashCode()
        result = 31 * result + totalNewMatches.hashCode()
        result = 31 * result + firedAt.hashCode()
        return result
    }

    override fun toString(): String =
        "SavedSearchAlert[savedSearchId=$savedSearchId, ownerId=$ownerId, label=$label, " +
            "matchedProductIds=$matchedProductIds, totalNewMatches=$totalNewMatches, " +
            "firedAt=$firedAt]"
}
