package com.example.search.bootstrap.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.example.search.adapter.out.synonym.ElasticsearchSynonymIndexUpdater;
import com.example.search.adapter.out.synonym.NoopSynonymIndexUpdater;
import com.example.search.application.port.out.SearchIndexProperties;
import com.example.search.application.synonym.port.out.SynonymIndexUpdaterPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 동의어 사전 outbound adapter 빈 등록 (ADR-0017).
 *
 * <p>ES 모드 — 실제 인덱스 settings reload. memory 모드 — RDB 등록만 하고 reload 는 no-op
 * (단위 테스트 / 로컬 dev).</p>
 */
@Configuration
public class SynonymConfig {

    @Bean
    @ConditionalOnProperty(name = "search.engine", havingValue = "elasticsearch", matchIfMissing = true)
    public SynonymIndexUpdaterPort esSynonymIndexUpdater(ElasticsearchClient client,
                                                          SearchIndexProperties properties) {
        return new ElasticsearchSynonymIndexUpdater(client, properties);
    }

    @Bean
    @ConditionalOnProperty(name = "search.engine", havingValue = "memory")
    public SynonymIndexUpdaterPort memorySynonymIndexUpdater() {
        return new NoopSynonymIndexUpdater();
    }
}
