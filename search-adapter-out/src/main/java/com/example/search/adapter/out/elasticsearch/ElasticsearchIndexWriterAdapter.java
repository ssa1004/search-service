package com.example.search.adapter.out.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.VersionType;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.indices.GetAliasResponse;
import co.elastic.clients.elasticsearch.indices.UpdateAliasesRequest;
import com.example.search.application.port.out.IndexWriterPort;
import com.example.search.application.port.out.SearchIndexProperties;
import com.example.search.domain.index.IndexDocument;
import com.example.search.domain.product.ProductId;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

/**
 * {@link IndexWriterPort} 의 ES 구현. ES 8.x Java Client low-level API.
 *
 * <p>핵심 동작:</p>
 * <ul>
 *   <li>{@link #index} — external version 으로 lost update 방지. 구 version 이 들어오면 ES 가 거부
 *       (version_conflict) — 적용 의도이므로 swallow.</li>
 *   <li>{@link #incrementClickCount} — painless 스크립트 partial update.</li>
 *   <li>{@link #createIndex} — bootstrap 의 mapping JSON 을 그대로 PUT.</li>
 *   <li>{@link #swapAlias} — atomic alias actions (remove + add 한 번에).</li>
 * </ul>
 */
@RequiredArgsConstructor
@Slf4j
public class ElasticsearchIndexWriterAdapter implements IndexWriterPort {

    private static final String CB_NAME = "elasticsearch-index";

    private final ElasticsearchClient client;
    private final SearchIndexProperties properties;
    private final IndexMappingResource mappingResource;

    @Override
    @CircuitBreaker(name = CB_NAME)
    @Retry(name = CB_NAME)
    public void index(IndexDocument document) {
        try {
            client.index(IndexRequest.of(r -> r
                    .index(properties.alias())
                    .id(document.id().value())
                    .versionType(VersionType.External)
                    .version(document.version())
                    .document(toSource(document))));
        } catch (ElasticsearchException e) {
            if (e.status() == 409) {
                // version_conflict — 같은 / 더 큰 version 이 이미 들어가 있음. 의도상 멱등이므로 무시.
                log.debug("version_conflict id={} version={} (멱등 — 무시)",
                        document.id(), document.version());
                return;
            }
            throw e;
        } catch (IOException e) {
            throw new SearchEngineIOException("ES index 실패: " + e.getMessage(), e);
        }
    }

    @Override
    @CircuitBreaker(name = CB_NAME)
    @Retry(name = CB_NAME)
    public void bulkIndex(List<IndexDocument> documents) {
        if (documents.isEmpty()) return;
        BulkRequest.Builder bb = new BulkRequest.Builder();
        // bulk 는 reindex 등 운영 명령에서만 사용 — 운영 alias 가 아니라 호출자가 미리 만든 새 물리
        // 인덱스로 보내야 한다. 여기서는 alias 로 보내지만 reindex 흐름은 createIndex 후 직접 _reindex
        // 를 쓰므로 실제로는 click count update 정도 외에 사용되지 않는다.
        for (IndexDocument doc : documents) {
            bb.operations(op -> op.index(i -> i
                    .index(properties.alias())
                    .id(doc.id().value())
                    .document(toSource(doc))));
        }
        try {
            var response = client.bulk(bb.build());
            if (response.errors()) {
                long failed = response.items().stream().filter(it -> it.error() != null).count();
                log.warn("bulk index 일부 실패 total={} failed={}", documents.size(), failed);
            }
        } catch (IOException e) {
            throw new SearchEngineIOException("ES bulk 실패: " + e.getMessage(), e);
        }
    }

    @Override
    @CircuitBreaker(name = CB_NAME)
    @Retry(name = CB_NAME)
    public void delete(ProductId id) {
        try {
            client.delete(d -> d.index(properties.alias()).id(id.value()));
        } catch (ElasticsearchException e) {
            if (e.status() == 404) {
                log.debug("delete: 이미 없음 id={} (멱등 — 무시)", id);
                return;
            }
            throw e;
        } catch (IOException e) {
            throw new SearchEngineIOException("ES delete 실패: " + e.getMessage(), e);
        }
    }

    @Override
    @CircuitBreaker(name = CB_NAME)
    @Retry(name = CB_NAME)
    public void incrementClickCount(ProductId id) {
        try {
            client.update(u -> u
                    .index(properties.alias())
                    .id(id.value())
                    .script(s -> s.source("ctx._source.clickCount = (ctx._source.clickCount ?: 0) + 1")
                            .lang("painless"))
                    .retryOnConflict(3),
                    Object.class);
        } catch (ElasticsearchException e) {
            if (e.status() == 404) {
                log.warn("clickCount 증가 대상 문서 없음 id={} (CDC 미반영 가능성)", id);
                return;
            }
            throw e;
        } catch (IOException e) {
            throw new SearchEngineIOException("ES update 실패: " + e.getMessage(), e);
        }
    }

    @Override
    @CircuitBreaker(name = CB_NAME)
    @Retry(name = CB_NAME)
    public void createIndex(String physicalName) {
        try {
            String mappingJson = mappingResource.read();
            client.indices().create(c -> c
                    .index(physicalName)
                    .withJson(new java.io.StringReader(mappingJson)));
            log.info("ES 인덱스 생성 완료: {}", physicalName);
        } catch (IOException e) {
            throw new SearchEngineIOException("ES createIndex 실패: " + e.getMessage(), e);
        }
    }

    @Override
    @CircuitBreaker(name = CB_NAME)
    public String currentPhysicalName() {
        try {
            GetAliasResponse response = client.indices().getAlias(g -> g.name(properties.alias()));
            Iterator<String> it = response.result().keySet().iterator();
            return it.hasNext() ? it.next() : null;
        } catch (ElasticsearchException e) {
            if (e.status() == 404) return null;
            throw e;
        } catch (IOException e) {
            throw new SearchEngineIOException("ES getAlias 실패: " + e.getMessage(), e);
        }
    }

    @Override
    @CircuitBreaker(name = CB_NAME)
    public long countDocuments(String physicalName) {
        try {
            // refresh 후 count — bulk 직후 doc count 가 갱신되도록.
            client.indices().refresh(r -> r.index(physicalName));
            var response = client.count(c -> c.index(physicalName));
            return response.count();
        } catch (IOException e) {
            throw new SearchEngineIOException("ES count 실패: " + e.getMessage(), e);
        }
    }

    @Override
    @CircuitBreaker(name = CB_NAME)
    @Retry(name = CB_NAME)
    public long reindex(String sourcePhysicalName, String targetPhysicalName) {
        try {
            var response = client.reindex(r -> r
                    .source(s -> s.index(sourcePhysicalName))
                    .dest(d -> d.index(targetPhysicalName))
                    .waitForCompletion(true));
            Long total = response.total();
            return total != null ? total : 0L;
        } catch (IOException e) {
            throw new SearchEngineIOException("ES reindex 실패: " + e.getMessage(), e);
        }
    }

    @Override
    @CircuitBreaker(name = CB_NAME)
    public void swapAlias(String oldPhysicalName, String newPhysicalName) {
        String alias = properties.alias();
        try {
            UpdateAliasesRequest.Builder b = new UpdateAliasesRequest.Builder();
            if (oldPhysicalName != null) {
                b.actions(a -> a.remove(rm -> rm.index(oldPhysicalName).alias(alias)));
            }
            b.actions(a -> a.add(add -> add.index(newPhysicalName).alias(alias)));
            client.indices().updateAliases(b.build());
            log.info("alias swap done alias={} old={} new={}", alias, oldPhysicalName, newPhysicalName);
        } catch (IOException e) {
            throw new SearchEngineIOException("ES alias swap 실패: " + e.getMessage(), e);
        }
    }

    @Override
    @CircuitBreaker(name = CB_NAME)
    public void deleteIndex(String physicalName) {
        try {
            client.indices().delete(d -> d.index(physicalName));
            log.info("ES 인덱스 삭제 완료: {}", physicalName);
        } catch (ElasticsearchException e) {
            if (e.status() == 404) {
                log.debug("이미 없는 인덱스 — 무시: {}", physicalName);
                return;
            }
            throw e;
        } catch (IOException e) {
            throw new SearchEngineIOException("ES deleteIndex 실패: " + e.getMessage(), e);
        }
    }

    private ElasticsearchSearchEngineAdapter.IndexedProductSource toSource(IndexDocument doc) {
        return new ElasticsearchSearchEngineAdapter.IndexedProductSource(
                doc.id().value(),
                doc.name(),
                doc.brand(),
                doc.category(),
                doc.priceWon(),
                doc.stockQuantity(),
                doc.status(),
                doc.clickCount()
        );
    }
}
