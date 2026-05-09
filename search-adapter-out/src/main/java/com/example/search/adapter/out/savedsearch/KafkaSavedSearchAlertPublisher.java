package com.example.search.adapter.out.savedsearch;

import com.example.search.application.savedsearch.port.out.SavedSearchAlertPublisher;
import com.example.search.domain.product.ProductId;
import com.example.search.domain.savedsearch.NotifyChannel;
import com.example.search.domain.savedsearch.SavedSearchAlert;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * SavedSearch 매치 결과를 Kafka 또는 webhook 으로 발행.
 *
 * <p>현 단계 구현 방침:</p>
 * <ul>
 *   <li>Kafka — KafkaTemplate 으로 사용자가 지정한 topic ({@code channel.target}) 에 publish.
 *       다운스트림 서비스가 사용자별 push / email 변환.</li>
 *   <li>WEBHOOK — 본 모듈은 기본 구현 없음 (HTTP 클라이언트 dep 추가 회피). 별도 어댑터로 확장.</li>
 * </ul>
 *
 * <p>Kafka publish 는 5초 timeout — 호출자 (스케줄러) 가 한 SavedSearch 평가에 너무 오래 막히지 않게.</p>
 */
@RequiredArgsConstructor
@Slf4j
public class KafkaSavedSearchAlertPublisher implements SavedSearchAlertPublisher {

    private static final long PUBLISH_TIMEOUT_MS = 5_000L;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(SavedSearchAlert alert, NotifyChannel channel) {
        if (channel.type() != NotifyChannel.Type.KAFKA) {
            // 다른 channel type 은 별도 publisher 가 책임. 본 구현은 Kafka 만.
            throw new UnsupportedOperationException(
                    "Kafka 가 아닌 channel type: " + channel.type());
        }
        String payload = serialize(alert);
        try {
            kafkaTemplate.send(channel.target(), alert.savedSearchId().value(), payload)
                    .get(PUBLISH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka publish interrupted", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException(
                    "Kafka publish 실패 topic=" + channel.target() + ": " + e.getMessage(), e);
        }
    }

    private String serialize(SavedSearchAlert alert) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("savedSearchId", alert.savedSearchId().value());
        root.put("ownerId", alert.ownerId());
        root.put("label", alert.label());
        ArrayNode arr = root.putArray("matchedProductIds");
        for (ProductId pid : alert.matchedProductIds()) {
            arr.add(pid.value());
        }
        root.put("totalNewMatches", alert.totalNewMatches());
        root.put("firedAt", alert.firedAt().toString());
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("SavedSearchAlert 직렬화 실패", e);
        }
    }
}
