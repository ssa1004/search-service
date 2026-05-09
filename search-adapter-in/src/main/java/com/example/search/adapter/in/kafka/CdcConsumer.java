package com.example.search.adapter.in.kafka;

import com.example.search.adapter.out.cdc.CdcEventPayload;
import com.example.search.adapter.out.cdc.ProductDtoMapper;
import com.example.search.application.port.in.HandleProductChangeUseCase;
import com.example.search.domain.event.ProductChangeEvent;
import com.example.search.domain.product.ProductId;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * CDC 토픽 ({@code product.changes}) 컨슈머.
 *
 * <p>동작:</p>
 * <ol>
 *   <li>메시지 1건 수신.</li>
 *   <li>JSON → {@link ProductChangeEvent} 도메인 변환.</li>
 *   <li>{@link HandleProductChangeUseCase} 호출 — INSERT/UPDATE 는 indexer 위임, DELETE 는 ES
 *       문서 삭제.</li>
 *   <li>예외 없이 끝났을 때만 ack — 처리 실패 시 DefaultErrorHandler 가 3회 retry 후 DLT 로
 *       격리 (ADR-0013). use case 가 idempotent 이므로 retry 중복 수신은 결과 같음.</li>
 * </ol>
 *
 * <p>{@code AckMode.MANUAL_IMMEDIATE} — bootstrap 의 KafkaConfig 가 설정. 본 컨슈머는 예외를
 * propagate 만 하고 retry / DLT 라우팅은 KafkaConfig 의 cdcErrorHandler 가 책임.</p>
 */
@Component
@ConditionalOnProperty(name = "search.kafka.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class CdcConsumer {

    private final HandleProductChangeUseCase changeUseCase;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    @KafkaListener(topics = "${search.cdc.topic:product.changes}",
            groupId = "${spring.kafka.consumer.group-id:search-cdc}",
            containerFactory = "kafkaListenerContainerFactory")
    public void onMessage(String payload, Acknowledgment ack) {
        String topic = "product.changes";
        try {
            CdcEventPayload event = objectMapper.readValue(payload, CdcEventPayload.class);
            ProductChangeEvent domainEvent = toDomain(event);
            changeUseCase.handle(domainEvent);
            ack.acknowledge();
            meterRegistry.counter("cdc.consume", "topic", topic, "outcome", "success").increment();
        } catch (Exception e) {
            // 예외 propagate → DefaultErrorHandler 가 3회 retry 후 DLT 발행. retry / dlt 메트릭은
            // KafkaConfig 의 retryListener / recoverer 가 기록.
            log.error("CDC 메시지 처리 실패 — 에러 핸들러로 위임. payload={}", payload, e);
            throw new IllegalStateException("CDC 처리 실패", e);
        }
    }

    private ProductChangeEvent toDomain(CdcEventPayload event) {
        Instant occurredAt = Instant.parse(event.occurredAt());
        return switch (event.op()) {
            case "INSERT" -> ProductChangeEvent.insert(
                    ProductDtoMapper.fromJson(event.payload(), objectMapper), occurredAt);
            case "UPDATE" -> ProductChangeEvent.update(
                    ProductDtoMapper.fromJson(event.payload(), objectMapper), occurredAt);
            case "DELETE" -> ProductChangeEvent.delete(
                    ProductId.of(event.productId()), event.version(), occurredAt);
            default -> throw new IllegalArgumentException("알 수 없는 op: " + event.op());
        };
    }
}
