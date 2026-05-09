package com.example.search.bootstrap.health;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import java.io.IOException;

/**
 * 검색 엔진 헬스 — ES ping 실패 시 readiness=DOWN 보고. K8s 가 traffic 을 떼어 cascade fail
 * 차단 (ADR-0010).
 *
 * <p>{@code /actuator/health/readiness} 의 readiness group 에 항상 포함된다 (ES / memory 모드
 * 모두). ES 모드에서는 client ping 결과를, memory 모드에서는 항상 UP 보고.</p>
 *
 * <p>liveness 에는 포함되지 않는다 — ES 일시 불가가 pod restart 사유는 아니다.</p>
 */
@Slf4j
public class SearchEngineHealthIndicator implements HealthIndicator {

    /** memory 모드용 — null 이면 항상 UP. */
    private final ElasticsearchClient client;
    private final String engineName;

    public SearchEngineHealthIndicator(ElasticsearchClient client) {
        this.client = client;
        this.engineName = "elasticsearch";
    }

    /** memory 모드 팩토리 — ping 대상 없음. */
    public static SearchEngineHealthIndicator inMemory() {
        return new SearchEngineHealthIndicator();
    }

    private SearchEngineHealthIndicator() {
        this.client = null;
        this.engineName = "memory";
    }

    @Override
    public Health health() {
        if (client == null) {
            return Health.up().withDetail("engine", engineName).build();
        }
        try {
            boolean ok = client.ping().value();
            if (ok) {
                return Health.up().withDetail("engine", engineName).build();
            }
            return Health.down().withDetail("engine", engineName)
                    .withDetail("reason", "ping returned false").build();
        } catch (IOException | RuntimeException e) {
            log.warn("ES ping 실패 — readiness DOWN 보고: {}", e.getMessage());
            return Health.down(e).withDetail("engine", engineName).build();
        }
    }
}
