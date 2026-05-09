package com.example.search.application.port.out;

/**
 * 인덱스/alias 이름과 reindex 동작 파라미터를 노출. 구현체는 bootstrap 의 @ConfigurationProperties.
 *
 * <p>application 모듈이 환경 변수에 직접 의존하지 않도록 port 로 분리.</p>
 */
public interface SearchIndexProperties {

    /**
     * 검색이 항상 바라보는 alias 이름. 예: "products".
     */
    String alias();

    /**
     * reindex bulk 한 번당 batch 크기. 너무 크면 ES 측 메모리 압박, 너무 작으면 처리량 저하.
     */
    int reindexBatchSize();
}
