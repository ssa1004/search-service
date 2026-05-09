package com.example.search.adapter.out.elasticsearch;

import com.example.search.application.port.out.SearchIndexProperties;

/**
 * 단위 테스트용 / dev 메모리 실행 시 사용되는 정적 properties — bootstrap 의 @ConfigurationProperties
 * 가 빈으로 등록되기 전에 in-memory adapter 가 필요한 경우 fallback.
 *
 * <p>운영에서는 bootstrap 의 SearchProperties (Configuration Properties) 가 등록되어 이 클래스가
 * 비활성.</p>
 */
public class StaticSearchIndexProperties implements SearchIndexProperties {

    private final String alias;
    private final int reindexBatchSize;

    public StaticSearchIndexProperties(String alias, int reindexBatchSize) {
        this.alias = alias;
        this.reindexBatchSize = reindexBatchSize;
    }

    public static StaticSearchIndexProperties defaults() {
        return new StaticSearchIndexProperties("products", 500);
    }

    @Override
    public String alias() {
        return alias;
    }

    @Override
    public int reindexBatchSize() {
        return reindexBatchSize;
    }
}
