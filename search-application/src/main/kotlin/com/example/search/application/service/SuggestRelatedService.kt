package com.example.search.application.service

import com.example.search.application.command.SuggestRelatedCommand
import com.example.search.application.port.`in`.SuggestRelatedUseCase
import com.example.search.application.port.out.SearchEnginePort
import com.example.search.domain.suggest.RelatedSuggestion
import org.springframework.stereotype.Service

/**
 * 관련 검색어 제안 — fuzzy match (Levenshtein).
 *
 * 호출 시점은 보통 검색 결과 0건일 때. ES 의 `fuzziness: AUTO` 또는 명시적 거리로 가까운
 * 키워드를 찾고, 인기도 (clickCount 합산) 로 정렬한다. 정렬은 adapter-out 에서 처리.
 */
@Service
class SuggestRelatedService(
    private val searchEngine: SearchEnginePort
) : SuggestRelatedUseCase {

    override fun suggest(command: SuggestRelatedCommand): List<RelatedSuggestion> {
        return searchEngine.findRelatedKeywords(
            command.keyword, command.limit, command.maxDistance
        )
    }
}
