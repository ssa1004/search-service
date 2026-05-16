package com.example.search.application.service

import com.example.search.application.command.AutocompleteCommand
import com.example.search.application.port.`in`.AutocompleteUseCase
import com.example.search.application.port.out.SearchEnginePort
import com.example.search.domain.suggest.AutocompleteSuggestion
import org.springframework.stereotype.Service

/**
 * 자동완성 use case.
 *
 * prefix 가 빈 문자열이면 ES 호출 자체를 생략 (불필요한 비용 0). 자동완성은 검색창 키 입력마다
 * 호출되므로 호출 빈도가 매우 높아 짧은 cutoff 가 중요.
 */
@Service
class AutocompleteService(
    private val searchEngine: SearchEnginePort
) : AutocompleteUseCase {

    override fun suggest(command: AutocompleteCommand): List<AutocompleteSuggestion> {
        if (command.isEmpty()) {
            return emptyList()
        }
        return searchEngine.autocomplete(command.prefix, command.limit)
    }
}
