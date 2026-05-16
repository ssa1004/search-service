package com.example.search.application.port.`in`

import com.example.search.application.command.SuggestRelatedCommand
import com.example.search.domain.suggest.RelatedSuggestion

/**
 * 관련 검색어 제안 — 검색 결과 0건 시 fuzzy match (Levenshtein 1-2) 로 가까운 인기 키워드를 찾는다.
 *
 * 입력 키워드와 편집 거리가 가까운 ES 의 `name` 토큰들을 추출하고, 인기도 (clickCount 합산) 로
 * 정렬해 상위 N건 반환.
 */
interface SuggestRelatedUseCase {
    fun suggest(command: SuggestRelatedCommand): List<RelatedSuggestion>
}
