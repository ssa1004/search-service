package com.example.search.application;

import com.example.search.application.command.AutocompleteCommand;
import com.example.search.application.port.out.SearchEnginePort;
import com.example.search.application.service.AutocompleteService;
import com.example.search.domain.product.ProductId;
import com.example.search.domain.suggest.AutocompleteSuggestion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutocompleteServiceTest {

    @Mock
    SearchEnginePort searchEngine;

    @InjectMocks
    AutocompleteService service;

    @Test
    void 빈_prefix_는_ES_호출_없이_빈_결과() {
        List<AutocompleteSuggestion> result = service.suggest(new AutocompleteCommand("  ", 10));
        assertThat(result).isEmpty();
        verify(searchEngine, never()).autocomplete(anyString(), anyInt());
    }

    @Test
    void prefix_가_있으면_ES_위임() {
        when(searchEngine.autocomplete("nik", 10)).thenReturn(List.of(
                new AutocompleteSuggestion("nike air max", ProductId.of("p-1"), 5.0),
                new AutocompleteSuggestion("nike dunk", ProductId.of("p-2"), 4.0)
        ));
        List<AutocompleteSuggestion> result = service.suggest(new AutocompleteCommand("nik", 10));
        assertThat(result).hasSize(2);
    }

    @Test
    void limit_상한_초과_거부() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> new AutocompleteCommand("a", AutocompleteCommand.MAX_LIMIT + 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
