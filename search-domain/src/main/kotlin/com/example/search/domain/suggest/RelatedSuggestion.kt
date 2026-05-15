package com.example.search.domain.suggest

/**
 * 검색 결과 0건일 때 제안되는 관련 검색어. fuzzy match (Levenshtein 1-2) 로 가까운 인기 검색어를
 * 찾아서 반환.
 *
 * [distance] 는 원본 키워드와의 편집 거리 — UI 에서 정렬 / 필터 기준으로 활용.
 */
@JvmRecord
data class RelatedSuggestion(
    val suggestedKeyword: String,
    val popularity: Long,
    val distance: Int
) {
    init {
        require(suggestedKeyword.isNotBlank()) { "suggestedKeyword 빈 값 불가" }
        require(popularity >= 0) { "popularity 음수 불가: $popularity" }
        require(distance >= 0) { "distance 음수 불가: $distance" }
    }
}
