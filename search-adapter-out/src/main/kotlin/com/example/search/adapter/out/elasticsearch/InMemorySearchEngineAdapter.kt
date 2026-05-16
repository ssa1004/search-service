package com.example.search.adapter.out.elasticsearch

import com.example.search.application.command.SearchProductCommand
import com.example.search.application.port.out.IndexWriterPort
import com.example.search.application.port.out.SearchEnginePort
import com.example.search.domain.facet.FacetResult
import com.example.search.domain.facet.FacetSpec
import com.example.search.domain.index.IndexDocument
import com.example.search.domain.product.ProductId
import com.example.search.domain.query.FilterCriterion
import com.example.search.domain.query.SearchQuery
import com.example.search.domain.query.SearchResult
import com.example.search.domain.suggest.AutocompleteSuggestion
import com.example.search.domain.suggest.RelatedSuggestion
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * 메모리 기반 SearchEnginePort + IndexWriterPort 구현 — 로컬 dev / 단위 테스트용.
 *
 * Elasticsearch 가 없는 환경에서도 REST + use case 가 동작하도록 단순한 contains / startsWith /
 * Levenshtein 매칭만 한다. 운영 분석기 (edge_ngram, fuzziness) 는 흉내만 — 실제 정확도는 ES 어댑터에
 * 의존.
 *
 * 이 클래스는 thread-safe — 동시 indexing / search 가 안전하게 동작.
 */
class InMemorySearchEngineAdapter : SearchEnginePort, IndexWriterPort {

    private val docs: MutableMap<String, IndexDocument> = ConcurrentHashMap()
    private val currentPhysical = AtomicReference<String?>()

    override fun search(command: SearchProductCommand): SearchResult {
        val sq: SearchQuery = command.query
        val start = System.nanoTime()

        val filtered: List<IndexDocument> = docs.values
            .filter { matchKeyword(it, sq.keyword) }
            .filter { matchFilters(it, sq.filters) }

        // boost rule 의 단순 가산 — popularityWeight * log(clickCount + 2). 신상품 decay 는 생략
        // (메모리 구현은 스모크 테스트용이라 정확한 점수가 중요하지 않음).
        val rule = command.boostRule
        // 점수 desc 정렬 + 동점 시 id asc tie-breaker — ES 어댑터와 같은 정책. tie-breaker 가
        // 없으면 ConcurrentHashMap 순회 순서에 따라 페이지 간 문서가 중복/누락될 수 있다.
        val all: List<SearchResult.Hit> = filtered
            .map { d ->
                SearchResult.Hit(
                    d.id, d.name, d.brand, d.category,
                    d.priceWon, d.stockQuantity, d.status,
                    rule.popularityWeight * ln((d.clickCount + 2).toDouble())
                )
            }
            .sortedWith(
                compareByDescending<SearchResult.Hit> { it.score }
                    .thenBy { it.id.value }
            )

        val from = sq.page.from()
        val to = min(from + sq.page.size, all.size)
        val paged: List<SearchResult.Hit> = if (from >= all.size) emptyList() else all.subList(from, to)

        val facets = computeFacets(filtered, command.facetSpecs)
        val took = (System.nanoTime() - start) / 1_000_000

        return SearchResult(all.size.toLong(), max(took, 1L), paged, facets)
    }

    override fun autocomplete(prefix: String, limit: Int): List<AutocompleteSuggestion> {
        val p = prefix.lowercase()
        return docs.values
            .filter { it.name.lowercase().startsWith(p) }
            .sortedByDescending { it.clickCount }
            .take(limit)
            .map { AutocompleteSuggestion(it.name, it.id, it.clickCount.toDouble()) }
    }

    override fun findRelatedKeywords(keyword: String, limit: Int, maxDistance: Int): List<RelatedSuggestion> {
        val k = keyword.lowercase()
        return docs.values
            .map { d ->
                RelatedSuggestion(
                    d.name, d.clickCount,
                    LevenshteinDistance.compute(k, d.name.lowercase())
                )
            }
            .filter { !it.suggestedKeyword.equals(keyword, ignoreCase = true) }
            .filter { it.distance <= maxDistance }
            .sortedByDescending { it.popularity }
            .take(limit)
    }

    // ── IndexWriterPort ─────────────────────────────────────────────

    override fun index(document: IndexDocument) {
        docs.merge(document.id.value, document) { existing, incoming ->
            // external version — 들어오는 게 더 크거나 같으면 적용, 아니면 무시 (멱등).
            if (incoming.version >= existing.version) incoming else existing
        }
    }

    override fun bulkIndex(documents: List<IndexDocument>) {
        documents.forEach { index(it) }
    }

    override fun delete(id: ProductId) {
        docs.remove(id.value)
    }

    override fun incrementClickCount(id: ProductId) {
        docs.computeIfPresent(id.value) { _, doc ->
            IndexDocument(
                doc.id, doc.name, doc.brand, doc.category, doc.sizes,
                doc.priceWon, doc.stockQuantity, doc.status,
                doc.clickCount + 1L, doc.version, doc.releasedAt, doc.updatedAt
            )
        }
    }

    override fun createIndex(physicalName: String) {
        currentPhysical.compareAndSet(null, physicalName)
    }

    override fun currentPhysicalName(): String? = currentPhysical.get()

    override fun countDocuments(physicalName: String): Long {
        // 메모리 구현은 단일 인덱스만 시뮬 — physicalName 무관.
        return docs.size.toLong()
    }

    override fun reindex(sourcePhysicalName: String, targetPhysicalName: String): Long = docs.size.toLong()

    override fun swapAlias(oldPhysicalName: String?, newPhysicalName: String) {
        currentPhysical.set(newPhysicalName)
    }

    override fun deleteIndex(physicalName: String) {
        if (physicalName == currentPhysical.get()) {
            currentPhysical.set(null)
            docs.clear()
        }
    }

    // ── helper ─────────────────────────────────────────────

    private fun matchKeyword(d: IndexDocument, keyword: String): Boolean {
        if (keyword.isBlank()) return true
        val k = keyword.lowercase()
        return d.name.lowercase().contains(k) || d.brand.lowercase().contains(k)
    }

    private fun matchFilters(d: IndexDocument, filters: List<FilterCriterion>): Boolean {
        for (f in filters) {
            if (!matchOne(d, f)) return false
        }
        return true
    }

    private fun matchOne(d: IndexDocument, f: FilterCriterion): Boolean = when (f) {
        is FilterCriterion.Term -> readField(d, f.field()) == f.value
        is FilterCriterion.Terms -> f.values.contains(readField(d, f.field()))
        is FilterCriterion.Range -> {
            val raw = readField(d, f.field())
            val v = raw.toLong()
            val fromOk = f.from?.let { from ->
                if (f.fromInclusive) v >= from else v > from
            } ?: true
            val toOk = f.to?.let { to ->
                if (f.toInclusive) v <= to else v < to
            } ?: true
            fromOk && toOk
        }
        is FilterCriterion.Exists -> readField(d, f.field()).isNotEmpty()
    }

    private fun readField(d: IndexDocument, field: String): String = when (field) {
        "brand" -> d.brand
        "category" -> d.category
        "status" -> d.status
        "priceWon" -> d.priceWon.toString()
        "stockQuantity" -> d.stockQuantity.toString()
        "clickCount" -> d.clickCount.toString()
        else -> ""
    }

    private fun computeFacets(filtered: List<IndexDocument>, specs: List<FacetSpec>): List<FacetResult> {
        val result = ArrayList<FacetResult>()
        for (spec in specs) {
            when (spec) {
                is FacetSpec.Terms -> {
                    val counts = HashMap<String, Long>()
                    for (d in filtered) {
                        counts.merge(readField(d, spec.field()), 1L, Long::plus)
                    }
                    val buckets = counts.entries
                        .sortedByDescending { it.value }
                        .take(spec.size)
                        .map { FacetResult.Bucket(it.key, it.value) }
                    result.add(FacetResult(spec.name(), buckets))
                }
                is FacetSpec.Range -> {
                    val buckets = spec.buckets.map { b ->
                        val count = filtered.count { d ->
                            val v = readField(d, spec.field()).toLong()
                            val fromOk = b.from?.let { v >= it } ?: true
                            val toOk = b.to?.let { v < it } ?: true
                            fromOk && toOk
                        }.toLong()
                        FacetResult.Bucket(b.key, count)
                    }
                    result.add(FacetResult(spec.name(), buckets))
                }
            }
        }
        return result
    }
}
