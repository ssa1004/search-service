package com.example.search.adapter.out.savedsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.JsonData;
import com.example.search.adapter.out.elasticsearch.SearchEngineIOException;
import com.example.search.application.port.out.SearchIndexProperties;
import com.example.search.application.savedsearch.port.out.SavedSearchMatchFinder;
import com.example.search.domain.product.ProductId;
import com.example.search.domain.query.FilterCriterion;
import com.example.search.domain.query.SearchQuery;
import com.example.search.domain.savedsearch.SavedSearch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

/**
 * SavedSearch 의 query 를 ES 에 다시 던져 since 이후 신규 매치만 반환.
 *
 * <p>{@code updatedAt >= since} filter 를 자동 합성 — 색인 시점 기준 신규 / 변경 문서만 후보. id 만
 * 가져오기 위해 {@code _source: false} + {@code stored_fields: _id}.</p>
 *
 * <p>본 어댑터는 {@link com.example.search.adapter.out.elasticsearch.ElasticsearchSearchEngineAdapter}
 * 와 다르게 facet / boost / sort 를 적용하지 않음 — 알림 후보 product 만 빠르게 식별하면 충분.</p>
 */
@RequiredArgsConstructor
@Slf4j
public class ElasticsearchSavedSearchMatchFinder implements SavedSearchMatchFinder {

    /** 평가 시 since filter 가 사용하는 ES date 필드 — products mapping 의 updatedAt. */
    static final String UPDATED_AT_FIELD = "updatedAt";

    private final ElasticsearchClient client;
    private final SearchIndexProperties properties;

    @Override
    public List<ProductId> findNewMatches(SavedSearch savedSearch, Instant since, int maxResults) {
        SearchQuery sq = savedSearch.query();

        Query base = Query.of(q -> q.bool(b -> {
            if (sq.hasKeyword()) {
                b.must(keywordQuery(sq.keyword()));
            } else {
                b.must(Query.of(qq -> qq.matchAll(m -> m)));
            }
            for (FilterCriterion f : sq.filters()) {
                b.filter(toFilterQuery(f));
            }
            // since filter — updatedAt >= since.
            b.filter(Query.of(q2 -> q2.range(RangeQuery.of(rq -> rq.date(d -> d
                    .field(UPDATED_AT_FIELD)
                    .gte(since.toString()))))));
            return b;
        }));

        SearchRequest req = new SearchRequest.Builder()
                .index(properties.alias())
                .size(maxResults)
                .source(s -> s.fetch(false))   // _source 가져오지 않음 — id 만 필요.
                .query(base)
                .build();

        try {
            SearchResponse<Void> response = client.search(req, Void.class);
            return response.hits().hits().stream()
                    .map(h -> ProductId.of(h.id()))
                    .toList();
        } catch (IOException e) {
            throw new SearchEngineIOException(
                    "SavedSearch match 평가 실패 id=" + savedSearch.id().value() + ": " + e.getMessage(), e);
        }
    }

    private Query keywordQuery(String keyword) {
        return Query.of(q -> q.multiMatch(MultiMatchQuery.of(m -> m
                .query(keyword)
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

    /** since 가 epoch 0 인 경우 대비 — defensive only. */
    @SuppressWarnings("unused")
    private static JsonData asJson(Instant i) {
        return JsonData.of(i.toString());
    }
}
