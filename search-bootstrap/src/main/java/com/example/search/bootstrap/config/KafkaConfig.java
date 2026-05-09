package com.example.search.bootstrap.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka 컨슈머 컨테이너 설정 — manual ack (at-least-once) + concurrency 1 (CDC 순서 보존).
 *
 * <p>{@code search.kafka.enabled=true} 일 때만 활성. 비활성 모드에서는 outbox 는 쌓이지만 컨슈머 /
 * publisher 미동작 (로컬 dev / 단위 테스트).</p>
 */
@Configuration
@EnableKafka
@ConditionalOnProperty(name = "search.kafka.enabled", havingValue = "true")
public class KafkaConfig {

    @Bean
    public ConsumerFactory<String, String> consumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrap,
            @Value("${spring.kafka.consumer.group-id:search-cdc}") String groupId
    ) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        // 새 consumer 는 가장 오래된 offset 부터 — CDC 메시지 누락 방지.
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        // 자동 commit 비활성 — manual ack 로 처리 완료 후에만 offset 진행.
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            DefaultErrorHandler cdcErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        // CDC 는 partition 간 순서가 product_id 단위로 보장되어야 하므로 concurrency 는 1 (운영에서는
        // partition 수에 맞춰 늘릴 수 있음).
        factory.setConcurrency(1);
        // 에러 핸들러 부착 — 3회 즉시 retry 후 DLT 로 라우팅 (ADR-0013).
        factory.setCommonErrorHandler(cdcErrorHandler);
        return factory;
    }

    /**
     * DLQ 정책 (ADR-0013) — DeadLetterPublishingRecoverer + FixedBackOff(0, 2).
     *
     * <p>3회 즉시 retry (interval 0) — CDC 메시지는 영구 schema 오류 / payload corruption 이 다수.
     * 같은 메시지 반복 retry 보다 빨리 DLT 로 격리.</p>
     *
     * <p>DLT 토픽: {@code <원본>.DLT} (Spring Kafka default). 운영자는 DLT consumer 또는 admin
     * replay endpoint 로 처리.</p>
     */
    @Bean
    public DefaultErrorHandler cdcErrorHandler(KafkaOperations<String, String> kafkaTemplate,
                                               MeterRegistry meterRegistry) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> {
                    // default destination — <topic>.DLT (같은 partition).
                    meterRegistry.counter("cdc.consume",
                            "topic", record.topic(),
                            "outcome", "dlt").increment();
                    return new TopicPartition(record.topic() + ".DLT", record.partition());
                });
        // FixedBackOff(interval=0ms, maxAttempts=2) → 첫 호출 + 2회 retry = 총 3회 시도.
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(0L, 2L));
        handler.setRetryListeners((record, ex, deliveryAttempt) ->
                meterRegistry.counter("cdc.consume",
                        "topic", record.topic(),
                        "outcome", "retry").increment());
        return handler;
    }

    @Bean
    public ProducerFactory<String, String> producerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrap) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 5);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}
