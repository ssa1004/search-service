package com.example.search.application.port.`in`

import com.example.search.application.command.AutocompleteCommand
import com.example.search.domain.suggest.AutocompleteSuggestion

/**
 * 자동완성 — prefix → 후보 N건. ES 의 edge_ngram analyzer 가 토큰화한 `name.autocomplete`
 * 필드를 prefix matching.
 *
 * completion suggester 가 아닌 edge_ngram 을 쓰는 이유는 ADR-0007 참조 — 자동완성 결과에 boost
 * (인기도/신상품) 를 그대로 적용 가능하기 때문.
 */
interface AutocompleteUseCase {
    fun suggest(command: AutocompleteCommand): List<AutocompleteSuggestion>
}
