package com.example.search.application.service;

import com.example.search.application.command.AutocompleteCommand;
import com.example.search.application.port.in.AutocompleteUseCase;
import com.example.search.application.port.out.SearchEnginePort;
import com.example.search.domain.suggest.AutocompleteSuggestion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 자동완성 use case.
 *
 * <p>prefix 가 빈 문자열이면 ES 호출 자체를 생략 (불필요한 비용 0). 자동완성은 검색창 키 입력마다
 * 호출되므로 호출 빈도가 매우 높아 짧은 cutoff 가 중요.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutocompleteService implements AutocompleteUseCase {

    private final SearchEnginePort searchEngine;

    @Override
    public List<AutocompleteSuggestion> suggest(AutocompleteCommand command) {
        if (command.isEmpty()) {
            return List.of();
        }
        return searchEngine.autocomplete(command.prefix(), command.limit());
    }
}
