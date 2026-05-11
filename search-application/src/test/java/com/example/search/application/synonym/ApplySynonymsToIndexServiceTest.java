package com.example.search.application.synonym;

import com.example.search.application.synonym.port.out.SynonymGroupRepository;
import com.example.search.application.synonym.port.out.SynonymIndexUpdaterPort;
import com.example.search.application.synonym.service.ApplySynonymsToIndexService;
import com.example.search.domain.synonym.SynonymDirection;
import com.example.search.domain.synonym.SynonymGroup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplySynonymsToIndexServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-09T10:00:00Z");

    @Mock
    SynonymGroupRepository repository;
    @Mock
    SynonymIndexUpdaterPort updater;

    @Test
    void 모든_그룹을_updater에_전달하고_적용수_반환() {
        SynonymGroup g1 = SynonymGroup.create(List.of("a", "b"), SynonymDirection.BIDIRECTIONAL, NOW, "op");
        SynonymGroup g2 = SynonymGroup.create(List.of("x", "y"), SynonymDirection.ONE_WAY, NOW, "op");
        when(repository.findAll()).thenReturn(List.of(g1, g2));
        when(updater.reload(List.of(g1, g2))).thenReturn(2);

        ApplySynonymsToIndexService service = new ApplySynonymsToIndexService(repository, updater);
        int applied = service.apply();

        assertThat(applied).isEqualTo(2);
        ArgumentCaptor<List<SynonymGroup>> captor = ArgumentCaptor.captor();
        verify(updater).reload(captor.capture());
        assertThat(captor.getValue()).containsExactly(g1, g2);
    }

    @Test
    void 그룹_없으면_빈_리스트_전달() {
        when(repository.findAll()).thenReturn(List.of());
        when(updater.reload(List.of())).thenReturn(0);
        ApplySynonymsToIndexService service = new ApplySynonymsToIndexService(repository, updater);

        assertThat(service.apply()).isZero();
    }
}
