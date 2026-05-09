package com.example.search.adapter.in.kafka;

import com.example.search.adapter.out.cdc.CdcEventPayload;
import com.example.search.adapter.out.cdc.ProductDtoMapper;
import com.example.search.application.port.in.HandleProductChangeUseCase;
import com.example.search.domain.event.ProductChangeEvent;
import com.example.search.domain.product.ProductId;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 *   <li>예외 없이 끝났을 때만 ack — 처리 실패 시 재처리 (at-least-once).</li>
 * </ol>
 *
 * <p>{@code AckMode.MANUAL_IMMEDIATE} 가정 — bootstrap 의 KafkaConfig 가 설정. use case 가 idempotent
 * 이므로 중복 수신은 결과 같음 (ES external version 비교).</p>
 */
@Component
@ConditionalOnProperty(name = "search.kafka.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class CdcConsumer {

    private final HandleProductChangeUseCase changeUseCase;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "${search.cdc.topic:product.changes}",
            groupId = "${spring.kafka.consumer.group-id:search-cdc}",
            containerFactory = "kafkaListenerContainerFactory")
    public void onMessage(String payload, Acknowledgment ack) {
        try {
            CdcEventPayload event = objectMapper.readValue(payload, CdcEventPayload.class);
            ProductChangeEvent domainEvent = toDomain(event);
            changeUseCase.handle(domainEvent);
            ack.acknowledge();
        } catch (Exception e) {
            // ack 안 함 → consumer 가 같은 offset 다시 읽음. retry 횟수 제한 / DLQ 는 ListenerErrorHandler
            // 로 별도 정책 가능 (현재는 무한 재시도가 default — 운영에서는 DLQ 필수).
            log.error("CDC 메시지 처리 실패 — 재시도. payload={}", payload, e);
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
