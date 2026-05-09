package com.example.search.adapter.in.kafka;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * DLT 토픽 ({@code product.changes.DLT}) 의 격리 컨슈머 (ADR-0013).
 *
 * <p>본 컨슈머는 메시지를 *처리하지 않는다* — 단순히 in-memory 카운터를 갱신하고 운영자가 admin
 * endpoint 로 manual replay 할 때까지 대기. ack 만 한다.</p>
 *
 * <p>본격 운영에서는 DLT 메시지를 별도 storage (DB / S3) 에 보존하고 UI 에서 검토 / replay 하지만
 * 본 service 는 in-memory snapshot 으로만 노출한다 (관찰 가능성 우선).</p>
 */
@Component
@RestController
@ConditionalOnProperty(name = "search.kafka.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class CdcDltConsumer {

    private final MeterRegistry meterRegistry;

    /** topic-partition 별 마지막으로 본 DLT offset — 운영 가시성 용. */
    private final ConcurrentMap<String, AtomicLong> lastDltOffsetByTopic = new ConcurrentHashMap<>();

    @KafkaListener(topics = "${search.cdc.topic:product.changes}.DLT",
            groupId = "${spring.kafka.consumer.group-id:search-cdc}-dlt",
            containerFactory = "kafkaListenerContainerFactory")
    public void onDlt(@Payload String payload,
                      @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                      @Header(KafkaHeaders.OFFSET) long offset,
                      Acknowledgment ack) {
        log.warn("CDC DLT 수신 topic={} offset={} payload={}", topic, offset, payload);
        lastDltOffsetByTopic.computeIfAbsent(topic, t -> new AtomicLong()).set(offset);
        meterRegistry.counter("cdc.dlt.observed", "topic", topic).increment();
        // ack 만 — 처리는 admin replay endpoint 로 위임.
        ack.acknowledge();
    }

    /** 단순 모니터링용 — admin endpoint 가 호출. */
    public long lastObservedOffset(String topic) {
        AtomicLong v = lastDltOffsetByTopic.get(topic);
        return v == null ? -1L : v.get();
    }
}
