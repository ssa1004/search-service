package com.example.search.adapter.out.elasticsearch

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch._types.FieldValue
import co.elastic.clients.elasticsearch._types.SortOrder
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation
import co.elastic.clients.elasticsearch._types.aggregations.AggregationRange
import co.elastic.clients.elasticsearch._types.query_dsl.FieldValueFactorModifier
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionBoostMode
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionScore
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionScoreMode
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery
import co.elastic.clients.elasticsearch._types.query_dsl.Query
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType
import co.elastic.clients.elasticsearch.core.SearchRequest
import co.elastic.clients.elasticsearch.core.SearchResponse
import co.elastic.clients.elasticsearch.core.search.Hit
import co.elastic.clients.json.JsonData
import com.example.search.application.command.SearchProductCommand
import com.example.search.application.port.out.SearchEnginePort
import com.example.search.application.port.out.SearchIndexProperties
import com.example.search.domain.facet.FacetResult
import com.example.search.domain.facet.FacetSpec
import com.example.search.domain.index.BoostRule
import com.example.search.domain.product.ProductId
import com.example.search.domain.query.FilterCriterion
import com.example.search.domain.query.SearchQuery
import com.example.search.domain.query.SearchResult
import com.example.search.domain.query.SortSpec
import com.example.search.domain.suggest.AutocompleteSuggestion
import com.example.search.domain.suggest.RelatedSuggestion
import java.io.IOException

/**
 * [SearchEnginePort] 의 ES 구현. low-level Java Client (8.x) 직접 사용.
 *
 * 여기서 도메인 → ES query DSL 의 매핑이 일어난다:
 * - 키워드 → multi_match (name + name.autocomplete + brand)
 * - 필터 → filter context (점수에 영향 없음)
 * - boost → function_score (인기도 log 함수 + 신상품 gauss decay)
 * - facet → terms / range aggregation
 *
 * Resilience4j CB + Retry 는 [ResilientSearchClient] 가 외부에서 wrap 한다 (ADR-0012)
 * — 본 어댑터는 raw ES 호출만 책임.
 */
class ElasticsearchSearchEngineAdapter(
    private val client: ElasticsearchClient,
    private val properties: SearchIndexProperties
) : SearchEnginePort {

    override fun search(command: SearchProductCommand): SearchResult {
        val sq: SearchQuery = command.query
        val baseQuery = buildBaseQuery(sq)
        val withBoost = wrapWithFunctionScore(baseQuery, command.boostRule)

        val rb = SearchRequest.Builder()
            .index(properties.alias())
            .from(sq.page.from())
            .size(sq.page.size)
            .trackTotalHits { it.enabled(true) }
            .query(withBoost)

        applySort(rb, sq.sort)
        applyAggregations(rb, command.facetSpecs)

        try {
            val response: SearchResponse<IndexedProductSource> =
                client.search(rb.build(), IndexedProductSource::class.java)
            return toDomainResult(response, command.facetSpecs)
        } catch (e: IOException) {
            throw SearchEngineIOException("ES search 실패: ${e.message}", e)
        }
    }

    override fun autocomplete(prefix: String, limit: Int): List<AutocompleteSuggestion> {
        // edge_ngram analyzer 가 indexing 시점에 prefix 토큰을 만들어 둠 — query 시점은 standard
        // analyzer 로 매칭. 자동완성에 boost (인기도) 도 함께 적용.
        val keywordMatch = Query.of { q ->
            q.match { m -> m.field("name.autocomplete").query(prefix) }
        }

        val withBoost = wrapWithFunctionScore(keywordMatch, BoostRule.defaults())

        val req = SearchRequest.Builder()
            .index(properties.alias())
            .size(limit)
            .query(withBoost)
            .source { s -> s.filter { f -> f.includes("name", "id") } }
            .build()

        try {
            val response: SearchResponse<IndexedProductSource> =
                client.search(req, IndexedProductSource::class.java)
            return response.hits().hits().map { toAutocomplete(it) }
        } catch (e: IOException) {
            throw SearchEngineIOException("ES autocomplete 실패: ${e.message}", e)
        }
    }

    override fun findRelatedKeywords(keyword: String, limit: Int, maxDistance: Int): List<RelatedSuggestion> {
        // fuzzy match — 편집 거리 maxDistance 이내. 인기도 (clickCount) 로 정렬해 가장 가까운 인기
        // 키워드를 반환.
        val fuzzy = Query.of { q ->
            q.match { m ->
                m.field("name").query(keyword).fuzziness(maxDistance.toString())
            }
        }

        val req = SearchRequest.Builder()
            .index(properties.alias())
            .size(limit * 3) // 후보를 넉넉히 받아 dedup 후 잘라낸다.
            .query(fuzzy)
            .source { s -> s.filter { f -> f.includes("name", "clickCount") } }
            .build()

        try {
            val response: SearchResponse<IndexedProductSource> =
                client.search(req, IndexedProductSource::class.java)
            return response.hits().hits()
                .map { toRelated(it) }
                .filter { !it.suggestedKeyword.equals(keyword, ignoreCase = true) }
                .map { withDistance(it, keyword) }
                .filter { it.distance <= maxDistance }
                // 인기도 desc 정렬 후 상위 limit 만.
                .sortedByDescending { it.popularity }
                .distinct()
                .take(limit)
        } catch (e: IOException) {
            throw SearchEngineIOException("ES related keyword 실패: ${e.message}", e)
        }
    }

    // ── 내부 빌딩 helper ─────────────────────────────────────────────

    private fun buildBaseQuery(sq: SearchQuery): Query {
        // bool query — must (keyword) + filter (정확 일치).
        return Query.of { q ->
            q.bool { b ->
                if (sq.hasKeyword()) {
                    b.must(keywordQuery(sq.keyword))
                } else {
                    b.must(Query.of { qq -> qq.matchAll { m -> m } })
                }
                for (f in sq.filters) {
                    b.filter(toFilterQuery(f))
                }
                b
            }
        }
    }

    private fun keywordQuery(keyword: String): Query = Query.of { q ->
        q.multiMatch(
            MultiMatchQuery.of { m ->
                m.query(keyword)
                    // name 에 가중치 3 (가장 중요), brand 에 가중치 2, name.autocomplete 보조.
                    .fields("name^3", "brand^2", "name.autocomplete")
                    .type(TextQueryType.BestFields)
                    .fuzziness("AUTO")
            }
        )
    }

    private fun toFilterQuery(f: FilterCriterion): Query = when (f) {
        is FilterCriterion.Term -> Query.of { q ->
            q.term { tt -> tt.field(f.field()).value { v -> v.stringValue(f.value) } }
        }
        is FilterCriterion.Terms -> Query.of { q ->
            q.terms { tt ->
                tt.field(f.field())
                    .terms { tv -> tv.value(f.values.map { FieldValue.of(it) }) }
            }
        }
        is FilterCriterion.Range -> Query.of { q -> q.range(buildRangeQuery(f)) }
        is FilterCriterion.Exists -> Query.of { q -> q.exists { ex -> ex.field(f.field()) } }
    }

    private fun buildRangeQuery(r: FilterCriterion.Range): RangeQuery {
        // ES 8.x: RangeQuery 는 number/term/date 등 sealed variants. number variant 로 long 범위 표현.
        return RangeQuery.of { rq ->
            rq.number { n ->
                n.field(r.field())
                r.from?.let { from ->
                    if (r.fromInclusive) n.gte(from.toDouble()) else n.gt(from.toDouble())
                }
                r.to?.let { to ->
                    if (r.toInclusive) n.lte(to.toDouble()) else n.lt(to.toDouble())
                }
                n
            }
        }
    }

    private fun wrapWithFunctionScore(base: Query, rule: BoostRule): Query {
        if (rule.isDisabled()) {
            return base
        }
        // function_score — base query 의 점수에 두 함수의 결과를 곱한다.
        // 1) 인기도 log: log1p(clickCount) * popularityWeight
        // 2) 신상품 gauss decay: 출시일 origin 기준 반감기 만큼 지나면 0.5 로 감쇠.
        val popularityFn = FunctionScore.of { f ->
            f.fieldValueFactor { fvf ->
                fvf.field("clickCount")
                    .modifier(FieldValueFactorModifier.Log1p)
                    .factor(rule.popularityWeight)
                    .missing(0.0)
            }
        }
        val freshnessFn = FunctionScore.of { f ->
            f.gauss { d ->
                d.untyped { u ->
                    u.field("releasedAt")
                        .placement { p ->
                            p.origin(JsonData.of("now"))
                                .scale(JsonData.of(rule.freshnessHalfLife.toDays().toString() + "d"))
                                .decay(0.5)
                        }
                }
            }
        }
        return Query.of { q ->
            q.functionScore { fs ->
                fs.query(base)
                    .functions(listOf(popularityFn, freshnessFn))
                    .scoreMode(FunctionScoreMode.Sum)
                    .boostMode(FunctionBoostMode.Multiply)
            }
        }
    }

    private fun applySort(rb: SearchRequest.Builder, sort: SortSpec?) {
        // 명시 sort 가 있으면 그 필드로, 없으면 _score (function_score 결과) 로 정렬.
        // 어느 쪽이든 동점일 때 ES 는 순서를 보장하지 않아 페이지 간 문서가 중복/누락될 수 있다
        // (priceWon 같은 비유일 필드, 또는 _score 동점). _id 를 마지막 tie-breaker 로 항상 덧붙여
        // 동점 시에도 결정적 순서를 만든다.
        if (sort != null) {
            rb.sort { s ->
                s.field { f ->
                    f.field(sort.field)
                        .order(if (sort.direction == SortSpec.Direction.ASC) SortOrder.Asc else SortOrder.Desc)
                }
            }
        } else {
            rb.sort { s -> s.score { sc -> sc.order(SortOrder.Desc) } }
        }
        rb.sort { s -> s.field { f -> f.field("_id").order(SortOrder.Asc) } }
    }

    private fun applyAggregations(rb: SearchRequest.Builder, facets: List<FacetSpec>) {
        for (spec in facets) {
            when (spec) {
                is FacetSpec.Terms -> rb.aggregations(spec.name(), Aggregation.of { a ->
                    a.terms { tt -> tt.field(spec.field()).size(spec.size) }
                })
                is FacetSpec.Range -> rb.aggregations(spec.name(), Aggregation.of { a ->
                    a.range { rr ->
                        rr.field(spec.field())
                        for (b in spec.buckets) {
                            rr.ranges(toRange(b))
                        }
                        rr
                    }
                })
            }
        }
    }

    private fun toRange(b: FacetSpec.Range.Bucket): AggregationRange = AggregationRange.of { rr ->
        rr.key(b.key)
        // 8.x 의 AggregationRange.from / to 는 Double — long 을 toDouble() 로.
        b.from?.let { rr.from(it.toDouble()) }
        b.to?.let { rr.to(it.toDouble()) }
        rr
    }

    // ── 응답 매핑 ─────────────────────────────────────────────

    private fun toDomainResult(
        response: SearchResponse<IndexedProductSource>,
        facetSpecs: List<FacetSpec>
    ): SearchResult {
        val total = response.hits().total()?.value() ?: 0L
        val took = response.took()
        val hits = response.hits().hits().map { toHit(it) }
        val facets = toFacetResults(response.aggregations(), facetSpecs)
        return SearchResult(total, took, hits, facets)
    }

    private fun toHit(hit: Hit<IndexedProductSource>): SearchResult.Hit {
        val src = hit.source()!!
        return SearchResult.Hit(
            ProductId.of(hit.id()!!),
            src.name,
            src.brand,
            src.category,
            src.priceWon,
            src.stockQuantity,
            src.status,
            hit.score() ?: 0.0
        )
    }

    private fun toFacetResults(
        aggs: Map<String, Aggregate>?,
        facetSpecs: List<FacetSpec>
    ): List<FacetResult> {
        if (aggs.isNullOrEmpty()) return emptyList()
        val result = ArrayList<FacetResult>()
        for (spec in facetSpecs) {
            val agg = aggs[spec.name()] ?: continue
            when (spec) {
                is FacetSpec.Terms -> {
                    val buckets = agg.sterms().buckets().array()
                        .map { b -> FacetResult.Bucket(b.key().stringValue(), b.docCount()) }
                    result.add(FacetResult(spec.name(), buckets))
                }
                is FacetSpec.Range -> {
                    val buckets = agg.range().buckets().array()
                        .map { b -> FacetResult.Bucket(b.key()!!, b.docCount()) }
                    result.add(FacetResult(spec.name(), buckets))
                }
            }
        }
        return result
    }

    private fun toAutocomplete(hit: Hit<IndexedProductSource>): AutocompleteSuggestion {
        val src = hit.source()!!
        return AutocompleteSuggestion(src.name, ProductId.of(hit.id()!!), hit.score() ?: 0.0)
    }

    private fun toRelated(hit: Hit<IndexedProductSource>): RelatedSuggestion {
        val src = hit.source()!!
        return RelatedSuggestion(src.name, src.clickCount, 0)
    }

    private fun withDistance(s: RelatedSuggestion, original: String): RelatedSuggestion {
        val d = LevenshteinDistance.compute(original.lowercase(), s.suggestedKeyword.lowercase())
        return RelatedSuggestion(s.suggestedKeyword, s.popularity, d)
    }

    /**
     * ES 응답 매핑용 평탄 record. 도메인 IndexDocument 와 다른 Jackson DTO — 필드는 ES JSON 키
     * 그대로.
     */
    @JvmRecord
    data class IndexedProductSource(
        val id: String,
        val name: String,
        val brand: String,
        val category: String,
        val priceWon: Long,
        val stockQuantity: Int,
        val status: String,
        val clickCount: Long
    )
}
