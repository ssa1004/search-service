package com.example.search.adapter.`in`.web

import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.util.UUID

/**
 * DLT 메시지 manual replay endpoint (ADR-0013).
 *
 * 운영자가 DLT 의 root cause (예: payload schema 오류) 를 고친 뒤 호출하면 DLT 의 보존된
 * 메시지를 원본 토픽으로 다시 publish — 정상 consumer 가 재처리한다.
 *
 * 주의:
 * - 본 endpoint 는 운영 보안 게이트 뒤에 둔다 (네트워크 분리 또는 인증).
 * - poll 만 하고 commit 은 안 함 — 같은 message 를 여러 번 replay 가능 (idempotent
 *   consumer 가정).
 * - DLT 메시지가 많으면 maxRecords 로 batch 분할 호출.
 */
@RestController
@RequestMapping("/api/v1/admin/cdc/dlt")
@ConditionalOnProperty(name = ["search.kafka.enabled"], havingValue = "true")
class AdminDltController(
    private val consumerFactory: ConsumerFactory<String, String>,
    private val kafkaTemplate: KafkaTemplate<String, String>
) {

    @Value("\${search.cdc.topic:product.changes}")
    private lateinit var originalTopic: String

    @PostMapping("/replay")
    fun replay(@RequestParam(defaultValue = "100") maxRecords: Int): Map<String, Any> {
        val dltTopic = "$originalTopic.DLT"
        // 임시 group — beginning 부터 모두 읽기 위해 random group 사용.
        val tempGroup = "dlt-replay-${UUID.randomUUID()}"
        val overrides = HashMap<String, Any>()
        overrides["group.id"] = tempGroup
        overrides["auto.offset.reset"] = "earliest"
        overrides["enable.auto.commit"] = "false"

        var replayed = 0
        val consumer: Consumer<String, String> =
            consumerFactory.createConsumer(tempGroup, "replay")
        consumer.use { c ->
            c.subscribe(listOf(dltTopic))
            // 첫 poll 은 partition assignment 대기 — 한 번 더 호출.
            c.poll(Duration.ofMillis(500))
            // 시작점 명시 — earliest.
            c.assignment().forEach { tp -> c.seekToBeginning(listOf(tp)) }

            val records = c.poll(Duration.ofSeconds(5))
            for (record: ConsumerRecord<String, String> in records) {
                if (replayed >= maxRecords) break
                kafkaTemplate.send(originalTopic, record.key(), record.value())
                replayed++
            }
        }
        log.info("DLT replay 완료 source={} target={} count={}", dltTopic, originalTopic, replayed)
        return mapOf(
            "source" to dltTopic,
            "target" to originalTopic,
            "replayed" to replayed
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(AdminDltController::class.java)
    }
}
