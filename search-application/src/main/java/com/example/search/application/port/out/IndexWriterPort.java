package com.example.search.application.port.out;

import com.example.search.domain.index.IndexDocument;
import com.example.search.domain.product.ProductId;

import java.util.List;

/**
 * ES 쓰기 port — single doc / bulk / delete / partial update / alias swap.
 *
 * <p>모든 메서드는 alias 이름을 인자로 받지 않는다 — 운영 alias 는 {@code SearchIndexProperties}
 * 가 가지고 있고 구현체가 거기서 읽는다. reindex 흐름은 별도 메서드 ({@link #createIndex},
 * {@link #swapAlias}) 가 직접 물리 이름을 다룬다.</p>
 */
public interface IndexWriterPort {

    /**
     * 단건 upsert. {@code version} 은 ES external version 으로 사용 — 구 버전이 새 버전을 덮어쓰는
     * lost update 를 ES 가 거부한다.
     */
    void index(IndexDocument document);

    /**
     * 다건 bulk upsert. reindex 같은 대량 작업의 처리량을 위해 분리.
     */
    void bulkIndex(List<IndexDocument> documents);

    /**
     * 단건 삭제. 존재하지 않으면 무시 (ES `result=not_found` → swallow).
     */
    void delete(ProductId id);

    /**
     * clickCount 1 증가 — partial update (painless script).
     */
    void incrementClickCount(ProductId id);

    /**
     * 새 물리 인덱스 생성 — reindex 시 alias 와 다른 mapping 으로 미리 만든다.
     */
    void createIndex(String physicalName);

    /**
     * 운영 alias 가 가리키는 현재 물리 인덱스 이름 (없으면 null). reindex 전후 비교용.
     */
    String currentPhysicalName();

    /**
     * 물리 인덱스의 doc count — reindex 검증.
     */
    long countDocuments(String physicalName);

    /**
     * source/target 인덱스 사이의 ES native reindex API 호출. {@code _reindex} 와 동일.
     */
    long reindex(String sourcePhysicalName, String targetPhysicalName);

    /**
     * alias atomic swap — 한 ES 호출로 (remove old + add new) 실행되어 검색 무중단.
     */
    void swapAlias(String oldPhysicalName, String newPhysicalName);

    /**
     * 물리 인덱스 삭제 — alias swap 후 구 인덱스 정리용.
     */
    void deleteIndex(String physicalName);
}
