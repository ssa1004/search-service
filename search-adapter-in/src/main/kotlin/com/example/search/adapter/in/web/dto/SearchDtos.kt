package com.example.search.adapter.`in`.web.dto

import com.example.search.domain.facet.FacetResult
import com.example.search.domain.query.SearchResult
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

/**
 * 검색 / 자동완성 / 클릭 / reindex 의 REST DTO 모음. 도메인 객체와 분리 — REST API 의 안정성과 도메인
 * 진화를 분리한다.
 */
object SearchDtos {

    /** 검색 키워드 길이 상한 — 비정상적으로 긴 입력의 multi_match 비용 폭증 방지. */
    const val MAX_KEYWORD_LENGTH: Int = 200

    /** 한 요청의 filter 수 상한 — bool query 의 must/filter 절 폭증으로 인한 메모리 / 분석 비용 보호. */
    const val MAX_FILTERS: Int = 20

    /** 한 요청의 facet 수 상한 — aggregation 폭증으로 인한 메모리 보호 (ADR-0008 참고). */
    const val MAX_FACETS: Int = 10

    /** 단일 filter 의 terms values 상한 — terms 필터가 수천 값을 받아 ES 메모리 폭주하는 사례 차단. */
    const val MAX_TERMS_VALUES: Int = 100

    @JvmRecord
    data class SearchRequest(
        @field:Size(max = MAX_KEYWORD_LENGTH) val keyword: String?,
        @field:Size(max = MAX_FILTERS) val filters: List<FilterDto>?,
        @field:Size(max = MAX_FACETS) val facets: List<FacetSpecDto>?,
        val sort: SortDto?,
        @field:Min(0) val page: Int,
        @field:Min(1) @field:Max(100) val size: Int
    )

    /**
     * 4가지 필터 form 을 DTO 차원에서 표현. [op] 으로 분기하고, 각 op 별 필요한 필드만 채워진다.
     *
     * - op=term — value 필수
     * - op=terms — values 필수
     * - op=range — from / to 중 하나 이상 + fromInclusive / toInclusive (default true / false)
     * - op=exists — field 만
     */
    @JvmRecord
    data class FilterDto(
        @field:NotNull val field: String,
        @field:NotNull val op: String,
        val value: String?,
        @field:Size(max = MAX_TERMS_VALUES) val values: List<String>?,
        val from: Long?,
        val fromInclusive: Boolean?,
        val to: Long?,
        val toInclusive: Boolean?
    )

    @JvmRecord
    data class FacetSpecDto(
        @field:NotNull val name: String,
        @field:NotNull val field: String,
        @field:NotNull val type: String,
        val size: Int?,
        val buckets: List<RangeBucketDto>?
    ) {
        @JvmRecord
        data class RangeBucketDto(@field:NotNull val key: String, val from: Long?, val to: Long?)
    }

    @JvmRecord
    data class SortDto(@field:NotNull val field: String, @field:NotNull val direction: String)

    @JvmRecord
    data class SearchResponse(
        val totalHits: Long,
        val took: Long,
        val page: Int,
        val size: Int,
        val hits: List<HitDto>,
        val facets: Map<String, List<FacetBucketDto>>
    ) {
        @JvmRecord
        data class HitDto(
            val id: String,
            val name: String,
            val brand: String?,
            val category: String?,
            val priceWon: Long,
            val stockQuantity: Int,
            val status: String?,
            val score: Double
        ) {
            companion object {
                @JvmStatic
                fun from(hit: SearchResult.Hit): HitDto = HitDto(
                    hit.id.value, hit.name, hit.brand,
                    hit.category, hit.priceWon, hit.stockQuantity,
                    hit.status, hit.score
                )
            }
        }

        @JvmRecord
        data class FacetBucketDto(val key: String, val count: Long) {
            companion object {
                @JvmStatic
                fun from(b: FacetResult.Bucket): FacetBucketDto = FacetBucketDto(b.key, b.count)
            }
        }
    }

    @JvmRecord
    data class AutocompleteResponse(val suggestions: List<SuggestionDto>) {
        @JvmRecord
        data class SuggestionDto(val text: String, val productId: String, val score: Double)
    }

    @JvmRecord
    data class RelatedResponse(val related: List<RelatedDto>) {
        @JvmRecord
        data class RelatedDto(val suggestedKeyword: String, val popularity: Long, val distance: Int)
    }

    @JvmRecord
    data class RecordClickRequest(
        @field:NotEmpty val productId: String,
        @field:NotEmpty val userId: String,
        @field:NotEmpty @field:Size(max = 200) val keyword: String,
        @field:Min(1) val rank: Int
    )

    @JvmRecord
    data class ReindexRequest(@field:NotEmpty val suffix: String, val dropOld: Boolean)

    @JvmRecord
    data class ReindexResponse(
        val newPhysicalName: String,
        val sourceDocCount: Long,
        val targetDocCount: Long,
        val swapped: Boolean
    )
}
