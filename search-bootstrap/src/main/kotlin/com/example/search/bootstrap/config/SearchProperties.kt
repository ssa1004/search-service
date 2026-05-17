package com.example.search.bootstrap.config

import com.example.search.application.port.out.SearchIndexProperties
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import org.springframework.validation.annotation.Validated

/**
 * search.* 환경 변수 / application.yml 매핑. [SearchIndexProperties] 빈을 노출.
 *
 * setter 기반 binding 을 유지 — 기존 운영 yml 의 kebab-case → camelCase mapping 동작을 그대로
 * 보존. 추가로 [SearchIndexProperties] 의 메서드 형태 (alias() / reindexBatchSize()) 도 함께 제공.
 */
@ConfigurationProperties(prefix = "search")
@Validated
@Component
@Primary
class SearchProperties : SearchIndexProperties {

    @get:NotBlank
    var alias: String = "products"

    @get:Min(50)
    var reindexBatchSize: Int = 500

    /**
     * Elasticsearch hostname:port. http:// 접두사 없이.
     */
    @get:NotBlank
    var elasticsearchHost: String = "localhost:9200"

    /**
     * CDC topic 이름. `spring.kafka.consumer.group-id` 와 함께 컨슈머 그룹 형성.
     */
    @get:NotBlank
    var cdcTopic: String = "product.changes"

    override fun alias(): String = alias

    override fun reindexBatchSize(): Int = reindexBatchSize
}
