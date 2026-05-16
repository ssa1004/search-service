package com.example.search.adapter.`in`.kafka

import com.example.search.adapter.out.cdc.CdcEventPayload
import com.example.search.adapter.out.cdc.ProductDtoMapper
import com.example.search.application.port.`in`.HandleProductChangeUseCase
import com.example.search.domain.event.ProductChangeEvent
import com.example.search.domain.product.ProductId
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * CDC 토픽 (`product.changes`) 컨슈머.
 *
 * 동작:
 * 1. 메시지 1건 수신.
 * 2. JSON → [ProductChangeEvent] 도메인 변환.
 * 3. [HandleProductChangeUseCase] 호출 — INSERT/UPDATE 는 indexer 위임, DELETE 는 ES
 *    문서 삭제.
 * 4. 예외 없이 끝났을 때만 ack — 처리 실패 시 DefaultErrorHandler 가 3회 retry 후 DLT 로
 *    격리 (ADR-0013). use case 가 idempotent 이므로 retry 중복 수신은 결과 같음.
 *
 * `AckMode.MANUAL_IMMEDIATE` — bootstrap 의 KafkaConfig 가 설정. 본 컨슈머는 예외를
 * propagate 만 하고 retry / DLT 라우팅은 KafkaConfig 의 cdcErrorHandler 가 책임.
 */
@Component
@ConditionalOnProperty(name = ["search.kafka.enabled"], havingValue = "true")
class CdcConsumer(
    private val changeUseCase: HandleProductChangeUseCase,
    private val objectMapper: ObjectMapper,
    private val meterRegistry: MeterRegistry
) {

    @KafkaListener(
        topics = ["\${search.cdc.topic:product.changes}"],
        groupId = "\${spring.kafka.consumer.group-id:search-cdc}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun onMessage(payload: String, ack: Acknowledgment) {
        val topic = "product.changes"
        try {
            val event = objectMapper.readValue(payload, CdcEventPayload::class.java)
            val domainEvent = toDomain(event)
            changeUseCase.handle(domainEvent)
            ack.acknowledge()
            meterRegistry.counter("cdc.consume", "topic", topic, "outcome", "success").increment()
        } catch (e: Exception) {
            // 예외 propagate → DefaultErrorHandler 가 3회 retry 후 DLT 발행. retry / dlt 메트릭은
            // KafkaConfig 의 retryListener / recoverer 가 기록.
            log.error("CDC 메시지 처리 실패 — 에러 핸들러로 위임. payload={}", payload, e)
            throw IllegalStateException("CDC 처리 실패", e)
        }
    }

    private fun toDomain(event: CdcEventPayload): ProductChangeEvent {
        val occurredAt = Instant.parse(event.occurredAt)
        return when (event.op) {
            "INSERT" -> ProductChangeEvent.insert(
                ProductDtoMapper.fromJson(event.payload!!, objectMapper), occurredAt
            )
            "UPDATE" -> ProductChangeEvent.update(
                ProductDtoMapper.fromJson(event.payload!!, objectMapper), occurredAt
            )
            "DELETE" -> ProductChangeEvent.delete(
                ProductId.of(event.productId), event.version, occurredAt
            )
            else -> throw IllegalArgumentException("알 수 없는 op: ${event.op}")
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(CdcConsumer::class.java)
    }
}
