package com.example.search.application.savedsearch;

import com.example.search.application.savedsearch.port.out.SavedSearchAlertPublisher;
import com.example.search.application.savedsearch.port.out.SavedSearchMatchFinder;
import com.example.search.application.savedsearch.port.out.SavedSearchRepository;
import com.example.search.application.savedsearch.service.EvaluateSavedSearchesService;
import com.example.search.domain.product.ProductId;
import com.example.search.domain.query.Page;
import com.example.search.domain.query.SearchQuery;
import com.example.search.domain.savedsearch.NotifyChannel;
import com.example.search.domain.savedsearch.SavedSearch;
import com.example.search.domain.savedsearch.SavedSearchAlert;
import com.example.search.domain.savedsearch.SavedSearchId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EvaluateSavedSearchesServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-09T10:00:00Z");

    @Mock
    SavedSearchRepository repository;
    @Mock
    SavedSearchMatchFinder matchFinder;
    @Mock
    SavedSearchAlertPublisher publisher;

    private EvaluateSavedSearchesService newService() {
        return new EvaluateSavedSearchesService(
                repository, matchFinder, publisher, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private SavedSearch sample(String owner) {
        return SavedSearch.create(owner, "label",
                SearchQuery.byKeyword("nike", Page.first(20)),
                NotifyChannel.kafka("search.alert.fired"),
                NOW.minusSeconds(3600));
    }

    @Test
    void 매치_있으면_publish_와_touch() {
        SavedSearch s = sample("u-1");
        when(repository.findActiveBatchAfter(null, EvaluateSavedSearchesService.BATCH_SIZE))
                .thenReturn(List.of(s));
        when(repository.findActiveBatchAfter(eq(s.id()), any(Integer.class)))
                .thenReturn(List.of());
        when(matchFinder.findNewMatches(s, s.evaluationCursor(),
                EvaluateSavedSearchesService.MAX_MATCHES_PER_SEARCH))
                .thenReturn(List.of(ProductId.of("p-1"), ProductId.of("p-2")));

        int evaluated = newService().evaluateAll();

        assertThat(evaluated).isEqualTo(1);
        ArgumentCaptor<SavedSearchAlert> captor = ArgumentCaptor.forClass(SavedSearchAlert.class);
        verify(publisher).publish(captor.capture(), eq(s.notifyChannel()));
        SavedSearchAlert alert = captor.getValue();
        assertThat(alert.matchedProductIds()).extracting(ProductId::value).containsExactly("p-1", "p-2");
        assertThat(alert.firedAt()).isEqualTo(NOW);
        assertThat(alert.totalNewMatches()).isEqualTo(2);
        verify(repository).touchEvaluatedAt(s.id(), NOW);
    }

    @Test
    void 매치_없으면_publish_생략_touch_는_수행() {
        SavedSearch s = sample("u-1");
        when(repository.findActiveBatchAfter(null, EvaluateSavedSearchesService.BATCH_SIZE))
                .thenReturn(List.of(s));
        when(matchFinder.findNewMatches(any(), any(), any(Integer.class)))
                .thenReturn(List.of());

        newService().evaluateAll();

        verify(publisher, never()).publish(any(), any());
        verify(repository).touchEvaluatedAt(s.id(), NOW);
    }

    @Test
    void 한_row_실패가_다른_row_평가를_막지_않음() {
        SavedSearch ok = sample("u-ok");
        SavedSearch bad = sample("u-bad");
        when(repository.findActiveBatchAfter(null, EvaluateSavedSearchesService.BATCH_SIZE))
                .thenReturn(List.of(bad, ok));
        when(matchFinder.findNewMatches(eq(bad), any(), any(Integer.class)))
                .thenThrow(new RuntimeException("ES timeout"));
        when(matchFinder.findNewMatches(eq(ok), any(), any(Integer.class)))
                .thenReturn(List.of(ProductId.of("p-1")));

        int evaluated = newService().evaluateAll();

        assertThat(evaluated).isEqualTo(2);
        // 실패한 row 는 lastEvaluatedAt 갱신 안 함 → 다음 사이클 재시도.
        verify(repository, never()).touchEvaluatedAt(bad.id(), NOW);
        verify(repository, times(1)).touchEvaluatedAt(ok.id(), NOW);
        verify(publisher, times(1)).publish(any(), eq(ok.notifyChannel()));
    }

    @Test
    void cursor_paging_으로_여러_batch_처리() {
        SavedSearch a = sample("u-1");
        SavedSearch b = sample("u-2");
        // 첫 batch 가 BATCH_SIZE 만큼 차야 두 번째 호출 발생 — 시뮬을 위해 BATCH_SIZE 만큼 채운 List 구성.
        java.util.ArrayList<SavedSearch> first = new java.util.ArrayList<>();
        for (int i = 0; i < EvaluateSavedSearchesService.BATCH_SIZE; i++) first.add(a);
        when(repository.findActiveBatchAfter(null, EvaluateSavedSearchesService.BATCH_SIZE))
                .thenReturn(first);
        when(repository.findActiveBatchAfter(eq(a.id()), any(Integer.class)))
                .thenReturn(List.of(b));
        when(matchFinder.findNewMatches(any(), any(), any(Integer.class)))
                .thenReturn(List.of());

        int evaluated = newService().evaluateAll();
        assertThat(evaluated).isEqualTo(EvaluateSavedSearchesService.BATCH_SIZE + 1);
    }
}
