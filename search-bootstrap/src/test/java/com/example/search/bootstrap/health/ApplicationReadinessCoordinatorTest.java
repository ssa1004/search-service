package com.example.search.bootstrap.health;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * ApplicationReadinessCoordinator 의 핵심 동작 — flap 방지 + 빠른 회복 — 단위 검증.
 */
@ExtendWith(MockitoExtension.class)
class ApplicationReadinessCoordinatorTest {

    @Mock
    private SearchEngineHealthIndicator engineHealth;

    private ApplicationReadinessCoordinator coordinator;
    private List<ReadinessState> publishedStates;

    @BeforeEach
    void setUp() {
        publishedStates = new ArrayList<>();
        ApplicationEventPublisher publisher = event -> {
            if (event instanceof AvailabilityChangeEvent<?> ace
                    && ace.getState() instanceof ReadinessState rs) {
                publishedStates.add(rs);
            }
        };
        coordinator = new ApplicationReadinessCoordinator(
                engineHealth, publisher, new SimpleMeterRegistry());
    }

    @Test
    void ES_가_연속_3회_DOWN_이어야_REFUSING_TRAFFIC_으로_전환된다() {
        when(engineHealth.health()).thenReturn(Health.down().build());

        coordinator.poll(); // 1회
        assertThat(publishedStates).isEmpty();

        coordinator.poll(); // 2회
        assertThat(publishedStates).isEmpty();

        coordinator.poll(); // 3회
        assertThat(publishedStates).containsExactly(ReadinessState.REFUSING_TRAFFIC);
    }

    @Test
    void 연속_2회_DOWN_후_UP_이면_traffic_차단_안된다() {
        when(engineHealth.health())
                .thenReturn(Health.down().build())
                .thenReturn(Health.down().build())
                .thenReturn(Health.up().build());

        coordinator.poll();
        coordinator.poll();
        coordinator.poll();

        // 처음부터 ACCEPTING 상태였으므로 같은 상태로 publish 없음.
        assertThat(publishedStates).isEmpty();
    }

    @Test
    void REFUSING_상태에서_UP_1회면_ACCEPTING_으로_즉시_복귀() {
        when(engineHealth.health())
                .thenReturn(Health.down().build())
                .thenReturn(Health.down().build())
                .thenReturn(Health.down().build())
                .thenReturn(Health.up().build());

        coordinator.poll(); // 1회 fail
        coordinator.poll(); // 2회 fail
        coordinator.poll(); // 3회 fail → REFUSING
        coordinator.poll(); // UP → ACCEPTING

        assertThat(publishedStates).containsExactly(
                ReadinessState.REFUSING_TRAFFIC,
                ReadinessState.ACCEPTING_TRAFFIC);
    }
}
