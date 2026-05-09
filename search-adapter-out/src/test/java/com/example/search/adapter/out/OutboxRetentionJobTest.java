package com.example.search.adapter.out;

import com.example.search.adapter.out.cdc.OutboxRetentionJob;
import com.example.search.adapter.out.persistence.outbox.ProductChangeOutboxRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OutboxRetentionJob 단위 검증 (ADR-0014):
 * <ol>
 *   <li>cutoff = now - 7d 로 호출하는지.</li>
 *   <li>한 번에 BATCH_SIZE 가 반환되면 다시 호출 (loop) 하는지.</li>
 *   <li>BATCH_SIZE 미만이 반환되면 종료하는지.</li>
 *   <li>총 삭제 수가 메트릭에 누적되는지.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class OutboxRetentionJobTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-05-09T03:00:00Z");

    @Mock
    private ProductChangeOutboxRepository outbox;

    private SimpleMeterRegistry meterRegistry;
    private OutboxRetentionJob job;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        job = new OutboxRetentionJob(outbox, meterRegistry, fixedClock);
    }

    @Test
    void cutoff_은_지금부터_7일전() {
        when(outbox.deletePublishedBefore(any(Instant.class), eq(1000))).thenReturn(0);

        job.purgeOldRows();

        Instant expectedCutoff = FIXED_NOW.minus(Duration.ofDays(7));
        verify(outbox).deletePublishedBefore(eq(expectedCutoff), eq(1000));
    }

    @Test
    void batch_size_가_가득_차면_재호출하여_drain_한다() {
        // 첫 두 호출은 1000 (BATCH 가득), 세 번째는 250 (drain).
        when(outbox.deletePublishedBefore(any(Instant.class), eq(1000)))
                .thenReturn(1000)
                .thenReturn(1000)
                .thenReturn(250);

        job.purgeOldRows();

        verify(outbox, times(3)).deletePublishedBefore(any(Instant.class), eq(1000));
        // 총 2250 row 삭제 메트릭.
        assertThat(meterRegistry.counter("outbox.retention.deleted").count()).isEqualTo(2250.0);
    }

    @Test
    void batch_size_미만이면_즉시_종료한다() {
        when(outbox.deletePublishedBefore(any(Instant.class), eq(1000))).thenReturn(42);

        job.purgeOldRows();

        verify(outbox, times(1)).deletePublishedBefore(any(Instant.class), eq(1000));
        assertThat(meterRegistry.counter("outbox.retention.deleted").count()).isEqualTo(42.0);
    }
}
