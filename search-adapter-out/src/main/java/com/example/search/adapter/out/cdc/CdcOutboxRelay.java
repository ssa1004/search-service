package com.example.search.adapter.out.cdc;

import com.example.search.adapter.out.persistence.outbox.ProductChangeOutboxEntity;
import com.example.search.adapter.out.persistence.outbox.ProductChangeOutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * outbox → Kafka relay — Debezium 의 단순 시뮬레이션.
 *
 * <p>운영에서는 Debezium connector 가 WAL 을 직접 읽어 Kafka 로 보내지만, 여기서는 outbox 테이블을
 * polling 한다. 두 방식 모두 결과적으로 source DB 변경 → Kafka topic 의 흐름이지만 운영 부담이 매우
 * 다르다 (ADR-0004 참고).</p>
 *
 * <p>at-least-once 보장 — Kafka publish 후 outbox.published_at 갱신. publish 성공 + DB update 실패의
 * 경우 같은 메시지가 다음 polling 에 다시 발행되지만, 컨슈머 측은 ES external version 비교로
 * 멱등이라 결과 정합성 유지.</p>
 */
@Component
@ConditionalOnProperty(name = "search.kafka.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class CdcOutboxRelay {

    private static final int BATCH_SIZE = 100;
    private static final long PUBLISH_TIMEOUT_MS = 5000L;

    private final ProductChangeOutboxRepository outbox;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Value("${search.cdc.topic:product.changes}")
    private String topic;

    /**
     * 5초마다 미발행 outbox 를 한 batch 씩 처리. 부하가 크면 fixedDelay 줄이고 BATCH_SIZE 키운다.
     */
    @Scheduled(fixedDelayString = "${search.cdc.relay-interval-ms:5000}")
    @Transactional
    public void relay() {
        List<ProductChangeOutboxEntity> batch = outbox.findUnpublished(PageRequest.of(0, BATCH_SIZE));
        if (batch.isEmpty()) return;

        Instant now = clock.instant();
        int success = 0;
        for (ProductChangeOutboxEntity row : batch) {
            try {
                String json = serialize(row);
                kafkaTemplate.send(topic, row.getProductId(), json)
                        .get(PUBLISH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                row.markPublished(now);
                success++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("CDC relay interrupted — 다음 사이클에 재시도");
                break;
            } catch (ExecutionException | TimeoutException e) {
                log.warn("CDC publish 실패 id={} — 다음 사이클에 재시도: {}", row.getId(), e.getMessage());
                break;
            }
        }
        if (success > 0) {
            log.info("CDC relay published={} pending={}", success, batch.size() - success);
        }
    }

    private String serialize(ProductChangeOutboxEntity row) {
        try {
            CdcEventPayload event = new CdcEventPayload(
                    row.getOp().name(),
                    row.getProductId(),
                    row.getVersion(),
                    row.getPayload(),
                    row.getOccurredAt().toString()
            );
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new IllegalStateException("CDC payload 직렬화 실패 outboxId=" + row.getId(), e);
        }
    }
}
