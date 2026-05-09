package com.example.search.application.savedsearch;

import com.example.search.application.savedsearch.command.SaveSearchCommand;
import com.example.search.application.savedsearch.port.in.SavedSearchQuotaExceededException;
import com.example.search.application.savedsearch.port.out.SavedSearchRepository;
import com.example.search.application.savedsearch.service.SaveSearchService;
import com.example.search.domain.query.Page;
import com.example.search.domain.query.SearchQuery;
import com.example.search.domain.savedsearch.NotifyChannel;
import com.example.search.domain.savedsearch.SavedSearch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SaveSearchServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-09T10:00:00Z");

    @Mock
    SavedSearchRepository repository;

    @Test
    void quota_여유시_저장() {
        when(repository.countByOwner("u-1")).thenReturn(3L);
        when(repository.save(org.mockito.ArgumentMatchers.any(SavedSearch.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SaveSearchService service = new SaveSearchService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
        SavedSearch result = service.save(new SaveSearchCommand(
                "u-1", "나이키 신상",
                SearchQuery.byKeyword("nike", Page.first(20)),
                NotifyChannel.kafka("search.alert.fired")));

        assertThat(result.ownerId()).isEqualTo("u-1");
        assertThat(result.label()).isEqualTo("나이키 신상");
        assertThat(result.active()).isTrue();
        assertThat(result.createdAt()).isEqualTo(NOW);

        ArgumentCaptor<SavedSearch> captor = ArgumentCaptor.forClass(SavedSearch.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().notifyChannel().target()).isEqualTo("search.alert.fired");
    }

    @Test
    void quota_초과시_예외_저장_안함() {
        when(repository.countByOwner("u-1")).thenReturn((long) SavedSearch.MAX_PER_OWNER);
        SaveSearchService service = new SaveSearchService(repository, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.save(new SaveSearchCommand(
                "u-1", "label",
                SearchQuery.byKeyword("k", Page.first(20)),
                NotifyChannel.kafka("topic"))))
                .isInstanceOf(SavedSearchQuotaExceededException.class);

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
