package com.example.search.adapter.out.analytics;

import com.example.search.domain.analytics.SearchEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchEventRecorderAdapterTest {

    private static final Instant NOW = Instant.parse("2026-05-09T10:00:00Z");

    @Mock
    SearchEventSpringDataRepository repository;

    @Test
    void record_저장시_도메인_그대로_persist() {
        SearchEventRecorderAdapter adapter = new SearchEventRecorderAdapter(repository);
        SearchEvent event = new SearchEvent("s-1", "nike", "u-1", 5, 42, NOW);

        adapter.record(event);

        ArgumentCaptor<SearchEventJpaEntity> captor = ArgumentCaptor.forClass(SearchEventJpaEntity.class);
        verify(repository).save(captor.capture());
        SearchEventJpaEntity saved = captor.getValue();
        assertThatCode(() -> {
            assert saved.getKeyword().equals("nike");
            assert saved.getResultCount() == 5;
            assert saved.getLatencyMs() == 42;
            assert saved.getOccurredAt().equals(NOW);
            assert saved.getSearchId().equals("s-1");
            assert "u-1".equals(saved.getUserId());
        }).doesNotThrowAnyException();
    }

    @Test
    void record_저장_실패는_예외_전파_없음() {
        when(repository.save(any())).thenThrow(new RuntimeException("DB down"));
        SearchEventRecorderAdapter adapter = new SearchEventRecorderAdapter(repository);
        SearchEvent event = new SearchEvent("s-1", "nike", null, 0, 10, NOW);

        assertThatCode(() -> adapter.record(event)).doesNotThrowAnyException();
    }
}
