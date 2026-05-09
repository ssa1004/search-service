package com.example.search.application;

import com.example.search.application.command.SearchProductCommand;
import com.example.search.application.port.out.SearchEnginePort;
import com.example.search.application.service.SearchProductService;
import com.example.search.domain.index.BoostRule;
import com.example.search.domain.query.Page;
import com.example.search.domain.query.SearchQuery;
import com.example.search.domain.query.SearchResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchProductServiceTest {

    @Mock
    SearchEnginePort searchEngine;

    @InjectMocks
    SearchProductService service;

    @Test
    void 결과를_그대로_반환() {
        SearchResult expected = new SearchResult(0L, 5L, List.of(), List.of());
        when(searchEngine.search(ArgumentMatchers.any())).thenReturn(expected);

        SearchProductCommand cmd = new SearchProductCommand(
                SearchQuery.byKeyword("nike", Page.first(20)),
                List.of(),
                BoostRule.defaults());

        SearchResult actual = service.search(cmd);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void slow_query_여부와_무관하게_정상_반환() {
        SearchResult slow = new SearchResult(10L, 1000L, List.of(), List.of());
        when(searchEngine.search(ArgumentMatchers.any())).thenReturn(slow);

        SearchProductCommand cmd = new SearchProductCommand(
                SearchQuery.byKeyword("zoom", Page.first(20)),
                List.of(),
                BoostRule.defaults());

        SearchResult actual = service.search(cmd);
        assertThat(actual.took()).isEqualTo(1000L);
    }
}
