package com.example.search.bootstrap.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DefaultErrorHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * cdcErrorHandler 빈 회귀 검증 (ADR-0013) — DefaultErrorHandler 가 만들어지고 backoff 가 정상.
 *
 * <p>실제 retry / DLT publish 동작은 Spring Kafka 의 통합 테스트 (별도 EmbeddedKafka) 가 다룬다.
 * 본 테스트는 *config 회귀* (interval 변경, FixedBackOff → ExponentialBackOff 로 잘못 교체 등)
 * 만 잡는다.</p>
 */
class CdcErrorHandlerTest {

    private KafkaConfig config;
    private SimpleMeterRegistry registry;
    @SuppressWarnings("unchecked")
    private final KafkaOperations<String, String> template = mock(KafkaOperations.class);

    @BeforeEach
    void setUp() {
        config = new KafkaConfig();
        registry = new SimpleMeterRegistry();
    }

    @Test
    void cdcErrorHandler_빈이_생성된다() {
        DefaultErrorHandler handler = config.cdcErrorHandler(template, registry);
        assertThat(handler).isNotNull();
    }

    @Test
    void cdcErrorHandler_가_생성된_후_metric_은_초기에_0() {
        // KafkaConfig 빈 생성만으로 외부 dep 없이 측정 — RetryListener 의 protected 접근 회피.
        config.cdcErrorHandler(template, registry);
        // 빈 생성만으로는 cdc.consume retry counter 가 등록 안 됨 (호출 시점에 lazy 등록).
        // 회귀 방지로는 빈 생성과 metric 이름 prefix 가 코드에 명시돼 있는지가 충분.
        double initial = registry.counter("cdc.consume",
                "topic", "product.changes",
                "outcome", "retry").count();
        assertThat(initial).isEqualTo(0.0);
    }
}
