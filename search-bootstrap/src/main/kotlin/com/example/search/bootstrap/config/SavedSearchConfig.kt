package com.example.search.bootstrap.config

import co.elastic.clients.elasticsearch.ElasticsearchClient
import com.example.search.adapter.out.savedsearch.ElasticsearchSavedSearchMatchFinder
import com.example.search.adapter.out.savedsearch.KafkaSavedSearchAlertPublisher
import com.example.search.adapter.out.savedsearch.SavedSearchEvaluatorJob
import com.example.search.adapter.out.savedsearch.SearchQueryJsonCodec
import com.example.search.application.port.out.SearchIndexProperties
import com.example.search.application.savedsearch.port.`in`.EvaluateSavedSearchesUseCase
import com.example.search.application.savedsearch.port.out.SavedSearchAlertPublisher
import com.example.search.application.savedsearch.port.out.SavedSearchMatchFinder
import com.example.search.application.savedsearch.port.out.SavedSearchRepository
import com.example.search.application.savedsearch.service.EvaluateSavedSearchesService
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.KafkaTemplate
import java.time.Clock

/**
 * SavedSearch 기능의 outbound adapter 빈 등록 (ADR-0016).
 *
 * match finder — 운영 ES 모드일 때만 등록. 메모리 모드는 별도 시뮬레이션이 필요하면 추가.
 *
 * alert publisher (Kafka) — `search.kafka.enabled=true` 일 때만 등록. 그 외 환경 (local dev) 은
 * publisher 빈이 없어 SavedSearchEvaluatorJob 도 의존성 미해소로 비활성 — 명시적이라 안전.
 */
@Configuration
class SavedSearchConfig {

    @Bean
    open fun searchQueryJsonCodec(objectMapper: ObjectMapper): SearchQueryJsonCodec =
        SearchQueryJsonCodec(objectMapper)

    @Bean
    @ConditionalOnProperty(name = ["search.engine"], havingValue = "elasticsearch", matchIfMissing = true)
    open fun savedSearchMatchFinder(
        client: ElasticsearchClient,
        properties: SearchIndexProperties,
    ): SavedSearchMatchFinder = ElasticsearchSavedSearchMatchFinder(client, properties)

    @Bean
    @ConditionalOnProperty(name = ["search.kafka.enabled"], havingValue = "true")
    open fun savedSearchAlertPublisher(
        kafkaTemplate: KafkaTemplate<String, String>,
        objectMapper: ObjectMapper,
    ): SavedSearchAlertPublisher = KafkaSavedSearchAlertPublisher(kafkaTemplate, objectMapper)

    /**
     * 평가 use case — match finder + publisher 빈이 모두 있을 때만 활성. memory 모드 또는 kafka
     * 비활성 환경에서는 빈 자체가 없어 SavedSearch 등록은 가능하지만 평가는 동작하지 않음 — 명시적
     * 안전 모드.
     */
    @Bean
    @ConditionalOnBean(SavedSearchMatchFinder::class, SavedSearchAlertPublisher::class)
    open fun evaluateSavedSearches(
        repository: SavedSearchRepository,
        matchFinder: SavedSearchMatchFinder,
        publisher: SavedSearchAlertPublisher,
        clock: Clock,
    ): EvaluateSavedSearchesUseCase = EvaluateSavedSearchesService(repository, matchFinder, publisher, clock)

    @Bean
    @ConditionalOnBean(EvaluateSavedSearchesUseCase::class)
    open fun savedSearchEvaluatorJob(
        useCase: EvaluateSavedSearchesUseCase,
        meterRegistry: MeterRegistry,
    ): SavedSearchEvaluatorJob = SavedSearchEvaluatorJob(useCase, meterRegistry)
}
