package com.example.search.bootstrap.config;

import com.example.search.application.port.out.SearchIndexProperties;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * search.* 환경 변수 / application.yml 매핑. {@link SearchIndexProperties} 빈을 노출.
 */
@ConfigurationProperties(prefix = "search")
@Validated
@Component
@Primary
public class SearchProperties implements SearchIndexProperties {

    @NotBlank
    private String alias = "products";

    @Min(50)
    private int reindexBatchSize = 500;

    /**
     * Elasticsearch hostname:port. http:// 접두사 없이.
     */
    @NotBlank
    private String elasticsearchHost = "localhost:9200";

    /**
     * CDC topic 이름. {@code spring.kafka.consumer.group-id} 와 함께 컨슈머 그룹 형성.
     */
    @NotBlank
    private String cdcTopic = "product.changes";

    @Override
    public String alias() {
        return alias;
    }

    @Override
    public int reindexBatchSize() {
        return reindexBatchSize;
    }

    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }
    public int getReindexBatchSize() { return reindexBatchSize; }
    public void setReindexBatchSize(int reindexBatchSize) { this.reindexBatchSize = reindexBatchSize; }
    public String getElasticsearchHost() { return elasticsearchHost; }
    public void setElasticsearchHost(String h) { this.elasticsearchHost = h; }
    public String getCdcTopic() { return cdcTopic; }
    public void setCdcTopic(String t) { this.cdcTopic = t; }
}
