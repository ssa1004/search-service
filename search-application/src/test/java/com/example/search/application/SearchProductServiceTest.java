package com.example.search.application;

import com.example.search.application.analytics.port.out.SearchEventRecorder;
import com.example.search.application.command.SearchProductCommand;
import com.example.search.application.port.out.SearchEnginePort;
import com.example.search.application.service.SearchProductService;
import com.example.search.domain.analytics.SearchEvent;
import com.example.search.domain.index.BoostRule;
import com.example.search.domain.query.Page;
import com.example.search.domain.query.SearchQuery;
import com.example.search.domain.query.SearchResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchProductServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-09T10:00:00Z");

    @Mock
    SearchEnginePort searchEngine;
    @Mock
    ObjectProvider<SearchEventRecorder> recorderProvider;
    @Mock
    SearchEventRecorder recorder;

    @Test
    void 결과를_그대로_반환() {
        SearchResult expected = new SearchResult(0L, 5L, List.of(), List.of());
        when(searchEngine.search(ArgumentMatchers.any())).thenReturn(expected);
        when(recorderProvider.getIfAvailable()).thenReturn(null);

        SearchProductService service = new SearchProductService(
                searchEngine, recorderProvider, Clock.fixed(NOW, ZoneOffset.UTC));
        SearchProductCommand cmd = new SearchProductCommand(
                SearchQuery.byKeyword("nike", Page.first(20)),
                List.of(),
                BoostRule.defaults());

        SearchResult actual = service.search(cmd);
        assertThat(actual).isEqualTo(expected);
        verify(recorder, never()).record(ArgumentMatchers.any());
    }

    @Test
    void slow_query_여부와_무관하게_정상_반환() {
        SearchResult slow = new SearchResult(10L, 1000L, List.of(), List.of());
        when(searchEngine.search(ArgumentMatchers.any())).thenReturn(slow);
        when(recorderProvider.getIfAvailable()).thenReturn(null);

        SearchProductService service = new SearchProductService(
                searchEngine, recorderProvider, Clock.fixed(NOW, ZoneOffset.UTC));
        SearchProductCommand cmd = new SearchProductCommand(
                SearchQuery.byKeyword("zoom", Page.first(20)),
                List.of(),
                BoostRule.defaults());

        SearchResult actual = service.search(cmd);
        assertThat(actual.took()).isEqualTo(1000L);
    }

    @Test
    void recorder_빈_있으면_keyword_정규화_후_event_기록() {
        SearchResult result = new SearchResult(7L, 42L, List.of(), List.of());
        when(searchEngine.search(ArgumentMatchers.any())).thenReturn(result);
        when(recorderProvider.getIfAvailable()).thenReturn(recorder);

        SearchProductService service = new SearchProductService(
                searchEngine, recorderProvider, Clock.fixed(NOW, ZoneOffset.UTC));
        SearchProductCommand cmd = new SearchProductCommand(
                SearchQuery.byKeyword("  Nike  ", Page.first(20)),
                List.of(),
                BoostRule.defaults(),
                "search-001",
                "user-42");

        service.search(cmd);

        ArgumentCaptor<SearchEvent> captor = ArgumentCaptor.forClass(SearchEvent.class);
        verify(recorder).record(captor.capture());
        SearchEvent recorded = captor.getValue();
        assertThat(recorded.searchId()).isEqualTo("search-001");
        assertThat(recorded.keyword()).isEqualTo("nike");
        assertThat(recorded.userId()).isEqualTo("user-42");
        assertThat(recorded.resultCount()).isEqualTo(7L);
        assertThat(recorded.latencyMs()).isEqualTo(42L);
        assertThat(recorded.occurredAt()).isEqualTo(NOW);
    }

    @Test
    void recorder_실패시_검색_응답은_정상() {
        SearchResult result = new SearchResult(0L, 10L, List.of(), List.of());
        when(searchEngine.search(ArgumentMatchers.any())).thenReturn(result);
        when(recorderProvider.getIfAvailable()).thenReturn(recorder);
        org.mockito.Mockito.doThrow(new RuntimeException("DB down"))
                .when(recorder).record(ArgumentMatchers.any());

        SearchProductService service = new SearchProductService(
                searchEngine, recorderProvider, Clock.fixed(NOW, ZoneOffset.UTC));
        SearchProductCommand cmd = new SearchProductCommand(
                SearchQuery.byKeyword("k", Page.first(20)),
                List.of(),
                BoostRule.defaults());

        // 예외 전파 X — 검색 응답은 그대로.
        SearchResult actual = service.search(cmd);
        assertThat(actual.totalHits()).isZero();
    }

    @Test
    void searchId_없으면_자동_생성() {
        SearchResult result = new SearchResult(1L, 5L, List.of(), List.of());
        when(searchEngine.search(ArgumentMatchers.any())).thenReturn(result);
        when(recorderProvider.getIfAvailable()).thenReturn(recorder);

        SearchProductService service = new SearchProductService(
                searchEngine, recorderProvider, Clock.fixed(NOW, ZoneOffset.UTC));
        SearchProductCommand cmd = new SearchProductCommand(
                SearchQuery.byKeyword("k", Page.first(20)),
                List.of(),
                BoostRule.defaults());
        service.search(cmd);

        ArgumentCaptor<SearchEvent> captor = ArgumentCaptor.forClass(SearchEvent.class);
        verify(recorder).record(captor.capture());
        assertThat(captor.getValue().searchId()).isNotBlank();
    }
}
