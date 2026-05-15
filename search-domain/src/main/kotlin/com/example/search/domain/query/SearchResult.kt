package com.example.search.domain.query

import com.example.search.domain.facet.FacetResult
import com.example.search.domain.product.ProductId

/**
 * 검색 응답.
 *
 * `hits` 는 page 단위 — total 은 ES 의 정확한 개수 (track_total_hits true). facet 결과는
 * 요청 시점에만 채워지고 그 외에는 빈 리스트.
 *
 * `took` 은 ES 측 실측 ms — 운영 환경에서 slow query 식별에 활용.
 *
 * record 의 compact constructor 가 `hits` / `facets` 를 방어 복사하므로 data class 가
 * 아닌 일반 class — equals / hashCode 는 정규화된 필드 기준으로 직접 정의한다.
 */
class SearchResult(
    @get:JvmName("totalHits") val totalHits: Long,
    @get:JvmName("took") val took: Long,
    hits: List<Hit>,
    facets: List<FacetResult>
) {

    @get:JvmName("hits")
    val hits: List<Hit>

    @get:JvmName("facets")
    val facets: List<FacetResult>

    init {
        require(totalHits >= 0) { "totalHits 음수 불가: $totalHits" }
        this.hits = java.util.List.copyOf(hits)
        this.facets = java.util.List.copyOf(facets)
    }

    fun isEmpty(): Boolean = hits.isEmpty()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SearchResult) return false
        return totalHits == other.totalHits &&
            took == other.took &&
            hits == other.hits &&
            facets == other.facets
    }

    override fun hashCode(): Int {
        var result = totalHits.hashCode()
        result = 31 * result + took.hashCode()
        result = 31 * result + hits.hashCode()
        result = 31 * result + facets.hashCode()
        return result
    }

    override fun toString(): String =
        "SearchResult[totalHits=$totalHits, took=$took, hits=$hits, facets=$facets]"

    /**
     * 한 건의 검색 hit. [score] 는 boost rule 적용 후의 최종 점수.
     *
     * Java record 가 id / name 만 null 검사하므로 brand / category / status 는 nullable 유지.
     */
    @JvmRecord
    data class Hit(
        val id: ProductId,
        val name: String,
        val brand: String?,
        val category: String?,
        val priceWon: Long,
        val stockQuantity: Int,
        val status: String?,
        val score: Double
    )
}
