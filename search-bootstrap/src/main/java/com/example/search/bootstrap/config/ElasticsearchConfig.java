package com.example.search.bootstrap.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.example.search.adapter.out.elasticsearch.ElasticsearchIndexWriterAdapter;
import com.example.search.adapter.out.elasticsearch.ElasticsearchSearchEngineAdapter;
import com.example.search.adapter.out.elasticsearch.IndexMappingResource;
import com.example.search.adapter.out.elasticsearch.InMemorySearchEngineAdapter;
import com.example.search.adapter.out.elasticsearch.ResilientSearchClient;
import com.example.search.application.port.out.IndexWriterPort;
import com.example.search.application.port.out.SearchEnginePort;
import com.example.search.application.port.out.SearchIndexProperties;
import com.example.search.bootstrap.health.SearchEngineHealthIndicator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ES 클라이언트 + adapter 빈 등록.
 *
 * <p>두 모드 — {@code search.engine=elasticsearch} (default) 면 운영 ES 어댑터, {@code memory} 면
 * InMemorySearchEngineAdapter 가 SearchEnginePort + IndexWriterPort 둘 다 구현. e2e 테스트 / 로컬
 * dev 에서 ES 없이 부팅 가능.</p>
 */
@Configuration
public class ElasticsearchConfig {

    // ── 운영 ES 모드 ─────────────────────────────────────────────────────────

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "search.engine", havingValue = "elasticsearch", matchIfMissing = true)
    public RestClient esRestClient(SearchProperties props) {
        String[] hp = props.getElasticsearchHost().split(":");
        return RestClient.builder(new HttpHost(hp[0], Integer.parseInt(hp[1]), "http")).build();
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "search.engine", havingValue = "elasticsearch", matchIfMissing = true)
    public ElasticsearchTransport esTransport(RestClient restClient, ObjectMapper objectMapper) {
        return new RestClientTransport(restClient, new JacksonJsonpMapper(objectMapper));
    }

    @Bean
    @ConditionalOnProperty(name = "search.engine", havingValue = "elasticsearch", matchIfMissing = true)
    public ElasticsearchClient esClient(ElasticsearchTransport transport) {
        return new ElasticsearchClient(transport);
    }

    @Bean
    @ConditionalOnProperty(name = "search.engine", havingValue = "elasticsearch", matchIfMissing = true)
    public SearchEnginePort esSearchEngine(ElasticsearchClient client, SearchIndexProperties props,
                                           RetryRegistry retryRegistry,
                                           CircuitBreakerRegistry cbRegistry) {
        // raw 어댑터 + Resilience4j Retry → CB chain (ADR-0012). instance 이름은 "elasticsearch".
        ElasticsearchSearchEngineAdapter raw = new ElasticsearchSearchEngineAdapter(client, props);
        return new ResilientSearchClient(
                raw,
                retryRegistry.retry("elasticsearch"),
                cbRegistry.circuitBreaker("elasticsearch"));
    }

    @Bean
    @ConditionalOnProperty(name = "search.engine", havingValue = "elasticsearch", matchIfMissing = true)
    public IndexWriterPort esIndexWriter(ElasticsearchClient client, SearchIndexProperties props,
                                         IndexMappingResource mapping) {
        return new ElasticsearchIndexWriterAdapter(client, props, mapping);
    }

    /**
     * readiness group 의 {@code searchEngine} 헬스 — ES ping 결과 보고. ApplicationReadinessCoordinator
     * 가 같은 빈을 polling 한다.
     */
    @Bean("searchEngine")
    @ConditionalOnProperty(name = "search.engine", havingValue = "elasticsearch", matchIfMissing = true)
    public SearchEngineHealthIndicator esEngineHealth(ElasticsearchClient client) {
        return new SearchEngineHealthIndicator(client);
    }

    // ── 메모리 모드 — ES 없이 동작하는 dev / 단순 e2e ───────────────────────

    /**
     * 단일 빈이 SearchEnginePort + IndexWriterPort 둘 다 구현 — 같은 인스턴스가 두 타입으로 주입된다.
     */
    @Bean
    @ConditionalOnProperty(name = "search.engine", havingValue = "memory")
    public InMemorySearchEngineAdapter inMemoryAdapter() {
        return new InMemorySearchEngineAdapter();
    }

    /**
     * memory 모드 — 항상 UP. readiness group 정의가 두 모드 모두에서 같은 indicator 이름을 참조하기
     * 위함.
     */
    @Bean("searchEngine")
    @ConditionalOnProperty(name = "search.engine", havingValue = "memory")
    public SearchEngineHealthIndicator memoryEngineHealth() {
        return SearchEngineHealthIndicator.inMemory();
    }
}
