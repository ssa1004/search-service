package com.example.search.adapter.out.cdc

import com.example.search.adapter.out.persistence.outbox.ProductChangeOutboxEntity
import com.example.search.adapter.out.persistence.outbox.ProductChangeOutboxRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.domain.PageRequest
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * outbox → Kafka relay — Debezium 의 단순 시뮬레이션.
 *
 * 운영에서는 Debezium connector 가 WAL 을 직접 읽어 Kafka 로 보내지만, 여기서는 outbox 테이블을
 * polling 한다. 두 방식 모두 결과적으로 source DB 변경 → Kafka topic 의 흐름이지만 운영 부담이 매우
 * 다르다 (ADR-0004 참고).
 *
 * at-least-once 보장 — Kafka publish 후 outbox.published_at 갱신. publish 성공 + DB update 실패의
 * 경우 같은 메시지가 다음 polling 에 다시 발행되지만, 컨슈머 측은 ES external version 비교로
 * 멱등이라 결과 정합성 유지.
 */
@Component
@ConditionalOnProperty(name = ["search.kafka.enabled"], havingValue = "true")
class CdcOutboxRelay(
    private val outbox: ProductChangeOutboxRepository,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper,
    private val clock: Clock
) {

    @Value("\${search.cdc.topic:product.changes}")
    private lateinit var topic: String

    /**
     * 5초마다 미발행 outbox 를 한 batch 씩 처리. 부하가 크면 fixedDelay 줄이고 BATCH_SIZE 키운다.
     */
    @Scheduled(fixedDelayString = "\${search.cdc.relay-interval-ms:5000}")
    @Transactional
    fun relay() {
        val batch = outbox.findUnpublished(PageRequest.of(0, BATCH_SIZE))
        if (batch.isEmpty()) return

        val now = clock.instant()
        var success = 0
        for (row in batch) {
            try {
                val json = serialize(row)
                kafkaTemplate.send(topic, row.productId, json)
                    .get(PUBLISH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                row.markPublished(now)
                success++
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                log.warn("CDC relay interrupted — 다음 사이클에 재시도")
                break
            } catch (e: ExecutionException) {
                log.warn("CDC publish 실패 id={} — 다음 사이클에 재시도: {}", row.id, e.message)
                break
            } catch (e: TimeoutException) {
                log.warn("CDC publish 실패 id={} — 다음 사이클에 재시도: {}", row.id, e.message)
                break
            }
        }
        if (success > 0) {
            log.info("CDC relay published={} pending={}", success, batch.size - success)
        }
    }

    private fun serialize(row: ProductChangeOutboxEntity): String {
        try {
            val event = CdcEventPayload(
                row.op.name,
                row.productId,
                row.version,
                row.payload,
                row.occurredAt.toString()
            )
            return objectMapper.writeValueAsString(event)
        } catch (e: Exception) {
            throw IllegalStateException("CDC payload 직렬화 실패 outboxId=${row.id}", e)
        }
    }

    companion object {
        private const val BATCH_SIZE: Int = 100
        private const val PUBLISH_TIMEOUT_MS: Long = 5000L

        private val log = LoggerFactory.getLogger(CdcOutboxRelay::class.java)
    }
}
