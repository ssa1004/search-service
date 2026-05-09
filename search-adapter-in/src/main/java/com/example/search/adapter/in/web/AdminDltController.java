package com.example.search.adapter.in.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DLT 메시지 manual replay endpoint (ADR-0013).
 *
 * <p>운영자가 DLT 의 root cause (예: payload schema 오류) 를 고친 뒤 호출하면 DLT 의 보존된
 * 메시지를 원본 토픽으로 다시 publish — 정상 consumer 가 재처리한다.</p>
 *
 * <p>주의:</p>
 * <ul>
 *   <li>본 endpoint 는 운영 보안 게이트 뒤에 둔다 (네트워크 분리 또는 인증).</li>
 *   <li>poll 만 하고 commit 은 안 함 — 같은 message 를 여러 번 replay 가능 (idempotent
 *       consumer 가정).</li>
 *   <li>DLT 메시지가 많으면 maxRecords 로 batch 분할 호출.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/admin/cdc/dlt")
@ConditionalOnProperty(name = "search.kafka.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class AdminDltController {

    private final ConsumerFactory<String, String> consumerFactory;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${search.cdc.topic:product.changes}")
    private String originalTopic;

    @PostMapping("/replay")
    public Map<String, Object> replay(@RequestParam(defaultValue = "100") int maxRecords) {
        String dltTopic = originalTopic + ".DLT";
        // 임시 group — beginning 부터 모두 읽기 위해 random group 사용.
        String tempGroup = "dlt-replay-" + UUID.randomUUID();
        Map<String, Object> overrides = new HashMap<>();
        overrides.put("group.id", tempGroup);
        overrides.put("auto.offset.reset", "earliest");
        overrides.put("enable.auto.commit", "false");

        int replayed = 0;
        try (Consumer<String, String> consumer = consumerFactory.createConsumer(tempGroup, "replay")) {
            consumer.subscribe(List.of(dltTopic));
            // 첫 poll 은 partition assignment 대기 — 한 번 더 호출.
            consumer.poll(Duration.ofMillis(500));
            // 시작점 명시 — earliest.
            consumer.assignment().forEach(tp -> consumer.seekToBeginning(List.of(tp)));

            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(5));
            for (ConsumerRecord<String, String> record : records) {
                if (replayed >= maxRecords) break;
                kafkaTemplate.send(originalTopic, record.key(), record.value());
                replayed++;
            }
        }
        log.info("DLT replay 완료 source={} target={} count={}", dltTopic, originalTopic, replayed);
        return Map.of(
                "source", dltTopic,
                "target", originalTopic,
                "replayed", replayed
        );
    }
}
