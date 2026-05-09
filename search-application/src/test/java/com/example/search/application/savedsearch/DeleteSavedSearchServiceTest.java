package com.example.search.application.savedsearch;

import com.example.search.application.savedsearch.port.in.SavedSearchNotOwnedException;
import com.example.search.application.savedsearch.port.out.SavedSearchRepository;
import com.example.search.application.savedsearch.service.DeleteSavedSearchService;
import com.example.search.domain.query.Page;
import com.example.search.domain.query.SearchQuery;
import com.example.search.domain.savedsearch.NotifyChannel;
import com.example.search.domain.savedsearch.SavedSearch;
import com.example.search.domain.savedsearch.SavedSearchId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeleteSavedSearchServiceTest {

    @Mock
    SavedSearchRepository repository;

    @InjectMocks
    DeleteSavedSearchService service;

    @Test
    void owner_일치시_삭제() {
        SavedSearchId id = SavedSearchId.newId();
        SavedSearch saved = SavedSearch.create(
                "u-1", "label",
                SearchQuery.byKeyword("k", Page.first(20)),
                NotifyChannel.kafka("topic"),
                Instant.parse("2026-01-01T00:00:00Z"));
        // factory 가 새 id 를 만들어 우리가 모르는 상태이므로 repository 가 그 id 로 저장된 row 를 반환하도록 stub.
        when(repository.findById(saved.id())).thenReturn(Optional.of(saved));

        service.delete("u-1", saved.id());

        verify(repository).deleteById(saved.id());
    }

    @Test
    void owner_불일치시_예외() {
        SavedSearch saved = SavedSearch.create(
                "u-1", "label",
                SearchQuery.byKeyword("k", Page.first(20)),
                NotifyChannel.kafka("topic"),
                Instant.parse("2026-01-01T00:00:00Z"));
        when(repository.findById(saved.id())).thenReturn(Optional.of(saved));

        assertThatThrownBy(() -> service.delete("attacker", saved.id()))
                .isInstanceOf(SavedSearchNotOwnedException.class);
        verify(repository, never()).deleteById(saved.id());
    }

    @Test
    void 존재하지_않으면_silent() {
        SavedSearchId id = SavedSearchId.newId();
        when(repository.findById(id)).thenReturn(Optional.empty());

        service.delete("u-1", id);
        verify(repository, never()).deleteById(id);
    }
}
