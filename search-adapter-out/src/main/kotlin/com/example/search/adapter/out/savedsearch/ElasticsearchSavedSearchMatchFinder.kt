package com.example.search.adapter.out.savedsearch

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch._types.FieldValue
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery
import co.elastic.clients.elasticsearch._types.query_dsl.Query
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType
import co.elastic.clients.elasticsearch.core.SearchRequest
import com.example.search.adapter.out.elasticsearch.SearchEngineIOException
import com.example.search.application.port.out.SearchIndexProperties
import com.example.search.application.savedsearch.port.out.SavedSearchMatchFinder
import com.example.search.domain.product.ProductId
import com.example.search.domain.query.FilterCriterion
import com.example.search.domain.savedsearch.SavedSearch
import java.io.IOException
import java.time.Instant

/**
 * SavedSearch 의 query 를 ES 에 다시 던져 since 이후 신규 매치만 반환.
 *
 * `updatedAt > since` (strict) filter 를 자동 합성 — since 는 직전 사이클이 이미 스캔
 * 완료한 시점이므로 같은 ms 의 문서를 다음 사이클에서 다시 잡지 않게 한다 (gte 였다면 경계의
 * 문서가 두 사이클 모두 매치되어 중복 알림 발행). id 만 가져오기 위해 `_source: false` +
 * `stored_fields: _id`.
 *
 * 본 어댑터는 [com.example.search.adapter.out.elasticsearch.ElasticsearchSearchEngineAdapter]
 * 와 다르게 facet / boost / sort 를 적용하지 않음 — 알림 후보 product 만 빠르게 식별하면 충분.
 */
class ElasticsearchSavedSearchMatchFinder(
    private val client: ElasticsearchClient,
    private val properties: SearchIndexProperties
) : SavedSearchMatchFinder {

    override fun findNewMatches(savedSearch: SavedSearch, since: Instant, maxResults: Int): List<ProductId> {
        val sq = savedSearch.query

        val base = Query.of { q ->
            q.bool { b ->
                if (sq.hasKeyword()) {
                    b.must(keywordQuery(sq.keyword))
                } else {
                    b.must(Query.of { qq -> qq.matchAll { m -> m } })
                }
                for (f in sq.filters) {
                    b.filter(toFilterQuery(f))
                }
                // since filter — updatedAt > since (strict). 같은 시점 문서가 다음 사이클에서
                // 다시 매치되어 알림이 두 번 가는 경계 사례 방지.
                b.filter(
                    Query.of { q2 ->
                        q2.range(
                            RangeQuery.of { rq ->
                                rq.date { d -> d.field(UPDATED_AT_FIELD).gt(since.toString()) }
                            }
                        )
                    }
                )
                b
            }
        }

        val req = SearchRequest.Builder()
            .index(properties.alias())
            .size(maxResults)
            .source { s -> s.fetch(false) } // _source 가져오지 않음 — id 만 필요.
            .query(base)
            .build()

        try {
            val response = client.search(req, Void::class.java)
            return response.hits().hits().map { ProductId.of(it.id()!!) }
        } catch (e: IOException) {
            throw SearchEngineIOException(
                "SavedSearch match 평가 실패 id=${savedSearch.id.value}: ${e.message}", e
            )
        }
    }

    private fun keywordQuery(keyword: String): Query = Query.of { q ->
        q.multiMatch(
            MultiMatchQuery.of { m ->
                m.query(keyword)
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

    private fun buildRangeQuery(r: FilterCriterion.Range): RangeQuery = RangeQuery.of { rq ->
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

    companion object {
        /** 평가 시 since filter 가 사용하는 ES date 필드 — products mapping 의 updatedAt. */
        internal const val UPDATED_AT_FIELD: String = "updatedAt"
    }
}
