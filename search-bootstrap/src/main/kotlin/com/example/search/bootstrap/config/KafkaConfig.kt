package com.example.search.bootstrap.config

import io.micrometer.core.instrument.MeterRegistry
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaOperations
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.util.backoff.FixedBackOff

/**
 * Kafka 컨슈머 컨테이너 설정 — manual ack (at-least-once) + concurrency 1 (CDC 순서 보존).
 *
 * `search.kafka.enabled=true` 일 때만 활성. 비활성 모드에서는 outbox 는 쌓이지만 컨슈머 / publisher
 * 미동작 (로컬 dev / 단위 테스트).
 */
@Configuration
@EnableKafka
@ConditionalOnProperty(name = ["search.kafka.enabled"], havingValue = "true")
class KafkaConfig {

    @Bean
    open fun consumerFactory(
        @Value("\${spring.kafka.bootstrap-servers}") bootstrap: String,
        @Value("\${spring.kafka.consumer.group-id:search-cdc}") groupId: String,
    ): ConsumerFactory<String, String> {
        val props = HashMap<String, Any>()
        props[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrap
        props[ConsumerConfig.GROUP_ID_CONFIG] = groupId
        // 새 consumer 는 가장 오래된 offset 부터 — CDC 메시지 누락 방지.
        props[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        // 자동 commit 비활성 — manual ack 로 처리 완료 후에만 offset 진행.
        props[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = false
        props[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        props[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        return DefaultKafkaConsumerFactory(props)
    }

    @Bean
    open fun kafkaListenerContainerFactory(
        consumerFactory: ConsumerFactory<String, String>,
        cdcErrorHandler: DefaultErrorHandler,
    ): ConcurrentKafkaListenerContainerFactory<String, String> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
        factory.consumerFactory = consumerFactory
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL_IMMEDIATE
        // CDC 는 partition 간 순서가 product_id 단위로 보장되어야 하므로 concurrency 는 1 (운영에서는
        // partition 수에 맞춰 늘릴 수 있음).
        factory.setConcurrency(1)
        // 에러 핸들러 부착 — 3회 즉시 retry 후 DLT 로 라우팅 (ADR-0013).
        factory.setCommonErrorHandler(cdcErrorHandler)
        return factory
    }

    /**
     * DLQ 정책 (ADR-0013) — DeadLetterPublishingRecoverer + FixedBackOff(0, 2).
     *
     * 3회 즉시 retry (interval 0) — CDC 메시지는 영구 schema 오류 / payload corruption 이 다수. 같은
     * 메시지 반복 retry 보다 빨리 DLT 로 격리.
     *
     * DLT 토픽: `<원본>.DLT` (Spring Kafka default). 운영자는 DLT consumer 또는 admin replay endpoint
     * 로 처리.
     */
    @Bean
    open fun cdcErrorHandler(
        kafkaTemplate: KafkaOperations<String, String>,
        meterRegistry: MeterRegistry,
    ): DefaultErrorHandler {
        val recoverer = DeadLetterPublishingRecoverer(kafkaTemplate) { record, _ ->
            // default destination — <topic>.DLT (같은 partition).
            meterRegistry.counter(
                "cdc.consume",
                "topic", record.topic(),
                "outcome", "dlt",
            ).increment()
            TopicPartition(record.topic() + ".DLT", record.partition())
        }
        // FixedBackOff(interval=0ms, maxAttempts=2) → 첫 호출 + 2회 retry = 총 3회 시도.
        val handler = DefaultErrorHandler(recoverer, FixedBackOff(0L, 2L))
        handler.setRetryListeners({ record, _, _ ->
            meterRegistry.counter(
                "cdc.consume",
                "topic", record.topic(),
                "outcome", "retry",
            ).increment()
        })
        return handler
    }

    @Bean
    open fun producerFactory(
        @Value("\${spring.kafka.bootstrap-servers}") bootstrap: String,
    ): ProducerFactory<String, String> {
        val props = HashMap<String, Any>()
        props[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrap
        props[ProducerConfig.ACKS_CONFIG] = "all"
        props[ProducerConfig.RETRIES_CONFIG] = 5
        props[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = true
        props[ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION] = 5
        props[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        props[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        return DefaultKafkaProducerFactory(props)
    }

    @Bean
    open fun kafkaTemplate(producerFactory: ProducerFactory<String, String>): KafkaTemplate<String, String> =
        KafkaTemplate(producerFactory)
}
