package com.example.search.application.synonym;

import com.example.search.application.synonym.command.RegisterSynonymGroupCommand;
import com.example.search.application.synonym.port.out.SynonymGroupRepository;
import com.example.search.application.synonym.service.RegisterSynonymGroupService;
import com.example.search.domain.synonym.SynonymDirection;
import com.example.search.domain.synonym.SynonymGroup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterSynonymGroupServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-09T10:00:00Z");

    @Mock
    SynonymGroupRepository repository;

    @Test
    void 등록시_도메인_생성_후_save() {
        when(repository.save(any(SynonymGroup.class))).thenAnswer(inv -> inv.getArgument(0));
        RegisterSynonymGroupService service = new RegisterSynonymGroupService(
                repository, Clock.fixed(NOW, ZoneOffset.UTC));

        SynonymGroup result = service.register(new RegisterSynonymGroupCommand(
                List.of("조던1", "에어조던1"), SynonymDirection.BIDIRECTIONAL, "ops-1"));

        assertThat(result.terms()).containsExactly("조던1", "에어조던1");
        assertThat(result.direction()).isEqualTo(SynonymDirection.BIDIRECTIONAL);
        assertThat(result.updatedAt()).isEqualTo(NOW);
        assertThat(result.updatedBy()).isEqualTo("ops-1");

        ArgumentCaptor<SynonymGroup> captor = ArgumentCaptor.forClass(SynonymGroup.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().toElasticsearchRule()).isEqualTo("조던1, 에어조던1");
    }
}
