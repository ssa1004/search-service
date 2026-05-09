package com.example.search.application;

import com.example.search.application.command.RecordSearchClickCommand;
import com.example.search.application.port.out.IndexWriterPort;
import com.example.search.application.port.out.SearchClickRepository;
import com.example.search.application.service.RecordSearchClickService;
import com.example.search.domain.event.SearchClick;
import com.example.search.domain.product.ProductId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RecordSearchClickServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-09T10:00:00Z");

    @Mock
    SearchClickRepository clicks;
    @Mock
    IndexWriterPort indexWriter;

    @Test
    void click_저장_후_ES_clickCount_증가() {
        RecordSearchClickService service = new RecordSearchClickService(
                clicks, indexWriter, Clock.fixed(NOW, ZoneOffset.UTC));

        service.record(new RecordSearchClickCommand(
                "search-1", "user-1", ProductId.of("p-1"), "nike", 3));

        ArgumentCaptor<SearchClick> captor = ArgumentCaptor.forClass(SearchClick.class);
        verify(clicks).save(captor.capture());
        SearchClick saved = captor.getValue();
        assertThat(saved.searchId()).isEqualTo("search-1");
        assertThat(saved.rank()).isEqualTo(3);
        assertThat(saved.occurredAt()).isEqualTo(NOW);

        verify(indexWriter).incrementClickCount(ProductId.of("p-1"));
    }
}
