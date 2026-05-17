package com.example.search.bootstrap.health

import co.elastic.clients.elasticsearch.ElasticsearchClient
import org.slf4j.LoggerFactory
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import java.io.IOException

/**
 * 검색 엔진 헬스 — ES ping 실패 시 readiness=DOWN 보고. K8s 가 traffic 을 떼어 cascade fail 차단
 * (ADR-0010).
 *
 * `/actuator/health/readiness` 의 readiness group 에 항상 포함된다 (ES / memory 모드 모두). ES
 * 모드에서는 client ping 결과를, memory 모드에서는 항상 UP 보고.
 *
 * liveness 에는 포함되지 않는다 — ES 일시 불가가 pod restart 사유는 아니다.
 */
class SearchEngineHealthIndicator private constructor(
    /** memory 모드용 — null 이면 항상 UP. */
    private val client: ElasticsearchClient?,
    private val engineName: String,
) : HealthIndicator {

    constructor(client: ElasticsearchClient) : this(client, "elasticsearch")

    override fun health(): Health {
        val es = client ?: return Health.up().withDetail("engine", engineName).build()
        return try {
            if (es.ping().value()) {
                Health.up().withDetail("engine", engineName).build()
            } else {
                Health.down()
                    .withDetail("engine", engineName)
                    .withDetail("reason", "ping returned false")
                    .build()
            }
        } catch (e: IOException) {
            log.warn("ES ping 실패 — readiness DOWN 보고: {}", e.message)
            Health.down(e).withDetail("engine", engineName).build()
        } catch (e: RuntimeException) {
            log.warn("ES ping 실패 — readiness DOWN 보고: {}", e.message)
            Health.down(e).withDetail("engine", engineName).build()
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(SearchEngineHealthIndicator::class.java)

        /** memory 모드 팩토리 — ping 대상 없음. */
        @JvmStatic
        fun inMemory(): SearchEngineHealthIndicator = SearchEngineHealthIndicator(null, "memory")
    }
}
