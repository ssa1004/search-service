package com.example.search.adapter.out.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.RangeBucket;
import co.elastic.clients.elasticsearch._types.aggregations.AggregationRange;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import com.example.search.application.command.SearchProductCommand;
import com.example.search.application.port.out.SearchEnginePort;
import com.example.search.application.port.out.SearchIndexProperties;
import com.example.search.domain.facet.FacetResult;
import com.example.search.domain.facet.FacetSpec;
import com.example.search.domain.index.BoostRule;
import com.example.search.domain.product.ProductId;
import com.example.search.domain.query.FilterCriterion;
import com.example.search.domain.query.SearchQuery;
import com.example.search.domain.query.SearchResult;
import com.example.search.domain.query.SortSpec;
import com.example.search.domain.suggest.AutocompleteSuggestion;
import com.example.search.domain.suggest.RelatedSuggestion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@link SearchEnginePort} 의 ES 구현. low-level Java Client (8.x) 직접 사용.
 *
 * <p>여기서 도메인 → ES query DSL 의 매핑이 일어난다:</p>
 * <ul>
 *   <li>키워드 → multi_match (name + name.autocomplete + brand)</li>
 *   <li>필터 → filter context (점수에 영향 없음)</li>
 *   <li>boost → function_score (인기도 log 함수 + 신상품 gauss decay)</li>
 *   <li>facet → terms / range aggregation</li>
 * </ul>
 *
 * <p>Resilience4j CB + Retry 는 {@link ResilientSearchClient} 가 외부에서 wrap 한다 (ADR-0012)
 * — 본 어댑터는 raw ES 호출만 책임.</p>
 */
@RequiredArgsConstructor
@Slf4j
public class ElasticsearchSearchEngineAdapter implements SearchEnginePort {

    private final ElasticsearchClient client;
    private final SearchIndexProperties properties;

    @Override
    public SearchResult search(SearchProductCommand command) {
        SearchQuery sq = command.query();
        Query baseQuery = buildBaseQuery(sq);
        Query withBoost = wrapWithFunctionScore(baseQuery, command.boostRule());

        SearchRequest.Builder rb = new SearchRequest.Builder()
                .index(properties.alias())
                .from(sq.page().from())
                .size(sq.page().size())
                .trackTotalHits(t -> t.enabled(true))
                .query(withBoost);

        applySort(rb, sq.sort());
        applyAggregations(rb, command.facetSpecs());

        try {
            SearchResponse<IndexedProductSource> response = client.search(rb.build(), IndexedProductSource.class);
            return toDomainResult(response, command.facetSpecs());
        } catch (IOException e) {
            throw new SearchEngineIOException("ES search 실패: " + e.getMessage(), e);
        }
    }

    @Override
    public List<AutocompleteSuggestion> autocomplete(String prefix, int limit) {
        // edge_ngram analyzer 가 indexing 시점에 prefix 토큰을 만들어 둠 — query 시점은 standard
        // analyzer 로 매칭. 자동완성에 boost (인기도) 도 함께 적용.
        Query keywordMatch = Query.of(q -> q.match(m -> m
                .field("name.autocomplete")
                .query(prefix)));

        Query withBoost = wrapWithFunctionScore(keywordMatch, BoostRule.defaults());

        SearchRequest req = new SearchRequest.Builder()
                .index(properties.alias())
                .size(limit)
                .query(withBoost)
                .source(s -> s.filter(f -> f.includes("name", "id")))
                .build();

        try {
            SearchResponse<IndexedProductSource> response = client.search(req, IndexedProductSource.class);
            return response.hits().hits().stream()
                    .map(this::toAutocomplete)
                    .toList();
        } catch (IOException e) {
            throw new SearchEngineIOException("ES autocomplete 실패: " + e.getMessage(), e);
        }
    }

    @Override
    public List<RelatedSuggestion> findRelatedKeywords(String keyword, int limit, int maxDistance) {
        // fuzzy match — 편집 거리 maxDistance 이내. 인기도 (clickCount) 로 정렬해 가장 가까운 인기
        // 키워드를 반환.
        Query fuzzy = Query.of(q -> q.match(m -> m
                .field("name")
                .query(keyword)
                .fuzziness(String.valueOf(maxDistance))));

        SearchRequest req = new SearchRequest.Builder()
                .index(properties.alias())
                .size(limit * 3)        // 후보를 넉넉히 받아 dedup 후 잘라낸다.
                .query(fuzzy)
                .source(s -> s.filter(f -> f.includes("name", "clickCount")))
                .build();

        try {
            SearchResponse<IndexedProductSource> response = client.search(req, IndexedProductSource.class);
            return response.hits().hits().stream()
                    .map(this::toRelated)
                    .filter(r -> !r.suggestedKeyword().equalsIgnoreCase(keyword))
                    .map(r -> withDistance(r, keyword))
                    .filter(r -> r.distance() <= maxDistance)
                    // 인기도 desc 정렬 후 상위 limit 만.
                    .sorted(Comparator.comparingLong(RelatedSuggestion::popularity).reversed())
                    .distinct()
                    .limit(limit)
                    .toList();
        } catch (IOException e) {
            throw new SearchEngineIOException("ES related keyword 실패: " + e.getMessage(), e);
        }
    }

    // ── 내부 빌딩 helper ─────────────────────────────────────────────

    private Query buildBaseQuery(SearchQuery sq) {
        // bool query — must (keyword) + filter (정확 일치).
        return Query.of(q -> q.bool(b -> {
            if (sq.hasKeyword()) {
                b.must(keywordQuery(sq.keyword()));
            } else {
                b.must(Query.of(qq -> qq.matchAll(m -> m)));
            }
            for (FilterCriterion f : sq.filters()) {
                b.filter(toFilterQuery(f));
            }
            return b;
        }));
    }

    private Query keywordQuery(String keyword) {
        return Query.of(q -> q.multiMatch(MultiMatchQuery.of(m -> m
                .query(keyword)
                // name 에 가중치 3 (가장 중요), brand 에 가중치 2, name.autocomplete 보조.
                .fields("name^3", "brand^2", "name.autocomplete")
                .type(TextQueryType.BestFields)
                .fuzziness("AUTO")
        )));
    }

    private Query toFilterQuery(FilterCriterion f) {
        return switch (f) {
            case FilterCriterion.Term t -> Query.of(q -> q.term(tt -> tt
                    .field(t.field()).value(v -> v.stringValue(t.value()))));
            case FilterCriterion.Terms t -> Query.of(q -> q.terms(tt -> tt
                    .field(t.field())
                    .terms(tv -> tv.value(t.values().stream()
                            .map(s -> co.elastic.clients.elasticsearch._types.FieldValue.of(s))
                            .toList()))));
            case FilterCriterion.Range r -> Query.of(q -> q.range(buildRangeQuery(r)));
            case FilterCriterion.Exists e -> Query.of(q -> q.exists(ex -> ex.field(e.field())));
        };
    }

    private RangeQuery buildRangeQuery(FilterCriterion.Range r) {
        // ES 8.x: RangeQuery 는 number/term/date 등 sealed variants. number variant 로 long 범위 표현.
        return RangeQuery.of(rq -> rq.number(n -> {
            n.field(r.field());
            if (r.from() != null) {
                if (r.fromInclusive()) n.gte(r.from().doubleValue());
                else n.gt(r.from().doubleValue());
            }
            if (r.to() != null) {
                if (r.toInclusive()) n.lte(r.to().doubleValue());
                else n.lt(r.to().doubleValue());
            }
            return n;
        }));
    }

    private Query wrapWithFunctionScore(Query base, BoostRule rule) {
        if (rule.isDisabled()) {
            return base;
        }
        // function_score — base query 의 점수에 두 함수의 결과를 곱한다.
        // 1) 인기도 log: log1p(clickCount) * popularityWeight
        // 2) 신상품 gauss decay: 출시일 origin 기준 반감기 만큼 지나면 0.5 로 감쇠.
        var popularityFn = co.elastic.clients.elasticsearch._types.query_dsl.FunctionScore.of(f ->
                f.fieldValueFactor(fvf -> fvf
                        .field("clickCount")
                        .modifier(co.elastic.clients.elasticsearch._types.query_dsl.FieldValueFactorModifier.Log1p)
                        .factor(rule.popularityWeight())
                        .missing(0.0)));
        var freshnessFn = co.elastic.clients.elasticsearch._types.query_dsl.FunctionScore.of(f ->
                f.gauss(d -> d.untyped(u -> u
                        .field("releasedAt")
                        .placement(p -> p
                                .origin(JsonData.of("now"))
                                .scale(JsonData.of(rule.freshnessHalfLife().toDays() + "d"))
                                .decay(0.5)))));
        return Query.of(q -> q.functionScore(fs -> fs
                .query(base)
                .functions(List.of(popularityFn, freshnessFn))
                .scoreMode(co.elastic.clients.elasticsearch._types.query_dsl.FunctionScoreMode.Sum)
                .boostMode(co.elastic.clients.elasticsearch._types.query_dsl.FunctionBoostMode.Multiply)
        ));
    }

    private void applySort(SearchRequest.Builder rb, SortSpec sort) {
        if (sort == null) return;
        rb.sort(s -> s.field(f -> f
                .field(sort.field())
                .order(sort.direction() == SortSpec.Direction.ASC ? SortOrder.Asc : SortOrder.Desc)));
    }

    private void applyAggregations(SearchRequest.Builder rb, List<FacetSpec> facets) {
        for (FacetSpec spec : facets) {
            switch (spec) {
                case FacetSpec.Terms t -> rb.aggregations(t.name(), Aggregation.of(a -> a
                        .terms(tt -> tt.field(t.field()).size(t.size()))));
                case FacetSpec.Range r -> rb.aggregations(r.name(), Aggregation.of(a -> a
                        .range(rr -> {
                            rr.field(r.field());
                            for (FacetSpec.Range.Bucket b : r.buckets()) {
                                rr.ranges(toRange(b));
                            }
                            return rr;
                        })));
            }
        }
    }

    private AggregationRange toRange(FacetSpec.Range.Bucket b) {
        return AggregationRange.of(rr -> {
            rr.key(b.key());
            // 8.x 의 AggregationRange.from / to 는 Double — long 을 doubleValue() 로.
            if (b.from() != null) rr.from(b.from().doubleValue());
            if (b.to() != null) rr.to(b.to().doubleValue());
            return rr;
        });
    }

    // ── 응답 매핑 ─────────────────────────────────────────────

    private SearchResult toDomainResult(SearchResponse<IndexedProductSource> response,
                                        List<FacetSpec> facetSpecs) {
        long total = response.hits().total() != null ? response.hits().total().value() : 0L;
        long took = response.took();
        List<SearchResult.Hit> hits = response.hits().hits().stream()
                .map(this::toHit)
                .toList();
        List<FacetResult> facets = toFacetResults(response.aggregations(), facetSpecs);
        return new SearchResult(total, took, hits, facets);
    }

    private SearchResult.Hit toHit(Hit<IndexedProductSource> hit) {
        IndexedProductSource src = hit.source();
        return new SearchResult.Hit(
                ProductId.of(hit.id()),
                src.name(),
                src.brand(),
                src.category(),
                src.priceWon(),
                src.stockQuantity(),
                src.status(),
                hit.score() != null ? hit.score() : 0.0
        );
    }

    private List<FacetResult> toFacetResults(Map<String, co.elastic.clients.elasticsearch._types.aggregations.Aggregate> aggs,
                                             List<FacetSpec> facetSpecs) {
        if (aggs == null || aggs.isEmpty()) return List.of();
        List<FacetResult> result = new ArrayList<>();
        for (FacetSpec spec : facetSpecs) {
            var agg = aggs.get(spec.name());
            if (agg == null) continue;
            switch (spec) {
                case FacetSpec.Terms t -> {
                    List<FacetResult.Bucket> buckets = new ArrayList<>();
                    for (StringTermsBucket b : agg.sterms().buckets().array()) {
                        buckets.add(new FacetResult.Bucket(b.key().stringValue(), b.docCount()));
                    }
                    result.add(new FacetResult(t.name(), buckets));
                }
                case FacetSpec.Range r -> {
                    List<FacetResult.Bucket> buckets = new ArrayList<>();
                    for (RangeBucket b : agg.range().buckets().array()) {
                        buckets.add(new FacetResult.Bucket(b.key(), b.docCount()));
                    }
                    result.add(new FacetResult(r.name(), buckets));
                }
            }
        }
        return result;
    }

    private AutocompleteSuggestion toAutocomplete(Hit<IndexedProductSource> hit) {
        IndexedProductSource src = hit.source();
        return new AutocompleteSuggestion(
                src.name(), ProductId.of(hit.id()), hit.score() != null ? hit.score() : 0.0);
    }

    private RelatedSuggestion toRelated(Hit<IndexedProductSource> hit) {
        IndexedProductSource src = hit.source();
        return new RelatedSuggestion(src.name(), src.clickCount(), 0);
    }

    private RelatedSuggestion withDistance(RelatedSuggestion s, String original) {
        int d = LevenshteinDistance.compute(original.toLowerCase(), s.suggestedKeyword().toLowerCase());
        return new RelatedSuggestion(s.suggestedKeyword(), s.popularity(), d);
    }

    /**
     * ES 응답 매핑용 평탄 record. 도메인 IndexDocument 와 다른 Jackson DTO — 필드는 ES JSON 키
     * 그대로.
     */
    public record IndexedProductSource(
            String id,
            String name,
            String brand,
            String category,
            long priceWon,
            int stockQuantity,
            String status,
            long clickCount
    ) {
    }

    /** 응답 매핑 시 client 가 source 를 비워 보낸 경우 대비 — 보통은 발생하지 않지만 NPE 차단. */
    @SuppressWarnings("unused")
    private static String safeId() {
        return UUID.randomUUID().toString();
    }
}
