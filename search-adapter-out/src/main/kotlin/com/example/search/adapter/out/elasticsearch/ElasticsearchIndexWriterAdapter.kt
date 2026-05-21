package com.example.search.adapter.out.elasticsearch

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch._types.ElasticsearchException
import co.elastic.clients.elasticsearch._types.VersionType
import co.elastic.clients.elasticsearch.core.BulkRequest
import co.elastic.clients.elasticsearch.core.IndexRequest
import co.elastic.clients.elasticsearch.indices.UpdateAliasesRequest
import com.example.search.application.port.out.IndexWriterPort
import com.example.search.application.port.out.SearchIndexProperties
import com.example.search.domain.index.IndexDocument
import com.example.search.domain.product.ProductId
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.StringReader

/**
 * [IndexWriterPort] 의 ES 구현. ES 8.x Java Client low-level API.
 *
 * 핵심 동작:
 * - [index] — external version 으로 lost update 방지. 구 version 이 들어오면 ES 가 거부
 *   (version_conflict) — 적용 의도이므로 swallow.
 * - [incrementClickCount] — painless 스크립트 partial update.
 * - [createIndex] — bootstrap 의 mapping JSON 을 그대로 PUT.
 * - [swapAlias] — atomic alias actions (remove + add 한 번에).
 */
// open: CGLIB AOP proxy 대상 (@CircuitBreaker/@Retry) — @Bean 등록이라 클래스 레벨 스테레오타입이
// 없어 plugin.spring allOpen 이 open 처리하지 않는다. Kotlin 클래스는 기본 final.
open class ElasticsearchIndexWriterAdapter(
    private val client: ElasticsearchClient,
    private val properties: SearchIndexProperties,
    private val mappingResource: IndexMappingResource
) : IndexWriterPort {

    @CircuitBreaker(name = CB_NAME)
    @Retry(name = CB_NAME)
    override fun index(document: IndexDocument) {
        try {
            client.index(
                IndexRequest.of { r ->
                    r.index(properties.alias())
                        .id(document.id.value)
                        .versionType(VersionType.External)
                        .version(document.version)
                        .document(toSource(document))
                }
            )
        } catch (e: ElasticsearchException) {
            if (e.status() == 409) {
                // version_conflict — 같은 / 더 큰 version 이 이미 들어가 있음. 의도상 멱등이므로 무시.
                log.debug(
                    "version_conflict id={} version={} (멱등 — 무시)",
                    document.id, document.version
                )
                return
            }
            throw e
        } catch (e: IOException) {
            throw SearchEngineIOException("ES index 실패: ${e.message}", e)
        }
    }

    @CircuitBreaker(name = CB_NAME)
    @Retry(name = CB_NAME)
    override fun bulkIndex(documents: List<IndexDocument>) {
        if (documents.isEmpty()) return
        val bb = BulkRequest.Builder()
        // bulk 는 reindex 등 운영 명령에서만 사용 — 운영 alias 가 아니라 호출자가 미리 만든 새 물리
        // 인덱스로 보내야 한다. 여기서는 alias 로 보내지만 reindex 흐름은 createIndex 후 직접 _reindex
        // 를 쓰므로 실제로는 click count update 정도 외에 사용되지 않는다.
        for (doc in documents) {
            bb.operations { op ->
                op.index { i ->
                    i.index(properties.alias())
                        .id(doc.id.value)
                        .document(toSource(doc))
                }
            }
        }
        try {
            val response = client.bulk(bb.build())
            if (response.errors()) {
                val failed = response.items().count { it.error() != null }
                log.warn("bulk index 일부 실패 total={} failed={}", documents.size, failed)
            }
        } catch (e: IOException) {
            throw SearchEngineIOException("ES bulk 실패: ${e.message}", e)
        }
    }

    @CircuitBreaker(name = CB_NAME)
    @Retry(name = CB_NAME)
    override fun delete(id: ProductId) {
        try {
            client.delete { d -> d.index(properties.alias()).id(id.value) }
        } catch (e: ElasticsearchException) {
            if (e.status() == 404) {
                log.debug("delete: 이미 없음 id={} (멱등 — 무시)", id)
                return
            }
            throw e
        } catch (e: IOException) {
            throw SearchEngineIOException("ES delete 실패: ${e.message}", e)
        }
    }

    @CircuitBreaker(name = CB_NAME)
    @Retry(name = CB_NAME)
    override fun incrementClickCount(id: ProductId) {
        try {
            client.update<Any, Any>(
                { u ->
                    u.index(properties.alias())
                        .id(id.value)
                        .script { s ->
                            s.source("ctx._source.clickCount = (ctx._source.clickCount ?: 0) + 1")
                                .lang("painless")
                        }
                        .retryOnConflict(3)
                },
                Any::class.java
            )
        } catch (e: ElasticsearchException) {
            if (e.status() == 404) {
                log.warn("clickCount 증가 대상 문서 없음 id={} (CDC 미반영 가능성)", id)
                return
            }
            throw e
        } catch (e: IOException) {
            throw SearchEngineIOException("ES update 실패: ${e.message}", e)
        }
    }

    @CircuitBreaker(name = CB_NAME)
    @Retry(name = CB_NAME)
    override fun createIndex(physicalName: String) {
        try {
            val mappingJson = mappingResource.read()
            client.indices().create { c -> c.index(physicalName).withJson(StringReader(mappingJson)) }
            log.info("ES 인덱스 생성 완료: {}", physicalName)
        } catch (e: IOException) {
            throw SearchEngineIOException("ES createIndex 실패: ${e.message}", e)
        }
    }

    @CircuitBreaker(name = CB_NAME)
    override fun currentPhysicalName(): String? {
        try {
            val response = client.indices().getAlias { g -> g.name(properties.alias()) }
            val it = response.result().keys.iterator()
            return if (it.hasNext()) it.next() else null
        } catch (e: ElasticsearchException) {
            if (e.status() == 404) return null
            throw e
        } catch (e: IOException) {
            throw SearchEngineIOException("ES getAlias 실패: ${e.message}", e)
        }
    }

    @CircuitBreaker(name = CB_NAME)
    override fun countDocuments(physicalName: String): Long {
        try {
            // refresh 후 count — bulk 직후 doc count 가 갱신되도록.
            client.indices().refresh { r -> r.index(physicalName) }
            val response = client.count { c -> c.index(physicalName) }
            return response.count()
        } catch (e: IOException) {
            throw SearchEngineIOException("ES count 실패: ${e.message}", e)
        }
    }

    @CircuitBreaker(name = CB_NAME)
    @Retry(name = CB_NAME)
    override fun reindex(sourcePhysicalName: String, targetPhysicalName: String): Long {
        try {
            val response = client.reindex { r ->
                r.source { s -> s.index(sourcePhysicalName) }
                    .dest { d -> d.index(targetPhysicalName) }
                    .waitForCompletion(true)
            }
            return response.total() ?: 0L
        } catch (e: IOException) {
            throw SearchEngineIOException("ES reindex 실패: ${e.message}", e)
        }
    }

    @CircuitBreaker(name = CB_NAME)
    override fun swapAlias(oldPhysicalName: String?, newPhysicalName: String) {
        val alias = properties.alias()
        try {
            val b = UpdateAliasesRequest.Builder()
            if (oldPhysicalName != null) {
                b.actions { a -> a.remove { rm -> rm.index(oldPhysicalName).alias(alias) } }
            }
            b.actions { a -> a.add { add -> add.index(newPhysicalName).alias(alias) } }
            client.indices().updateAliases(b.build())
            log.info("alias swap done alias={} old={} new={}", alias, oldPhysicalName, newPhysicalName)
        } catch (e: IOException) {
            throw SearchEngineIOException("ES alias swap 실패: ${e.message}", e)
        }
    }

    @CircuitBreaker(name = CB_NAME)
    override fun deleteIndex(physicalName: String) {
        try {
            client.indices().delete { d -> d.index(physicalName) }
            log.info("ES 인덱스 삭제 완료: {}", physicalName)
        } catch (e: ElasticsearchException) {
            if (e.status() == 404) {
                log.debug("이미 없는 인덱스 — 무시: {}", physicalName)
                return
            }
            throw e
        } catch (e: IOException) {
            throw SearchEngineIOException("ES deleteIndex 실패: ${e.message}", e)
        }
    }

    private fun toSource(doc: IndexDocument): ElasticsearchSearchEngineAdapter.IndexedProductSource =
        ElasticsearchSearchEngineAdapter.IndexedProductSource(
            doc.id.value,
            doc.name,
            doc.brand,
            doc.category,
            doc.priceWon,
            doc.stockQuantity,
            doc.status,
            doc.clickCount
        )

    companion object {
        // ResilientSearchClient 와 같은 instance config 공유. write 의 retry 정책은 별도화 검토 가치
        // (idempotency 보장 후에만 retry) — ADR-0012 의 "다시 검토할 시점" 참고.
        private const val CB_NAME: String = "elasticsearch"

        private val log = LoggerFactory.getLogger(ElasticsearchIndexWriterAdapter::class.java)
    }
}
