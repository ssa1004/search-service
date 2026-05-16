package com.example.search.application.command

import com.example.search.domain.facet.FacetSpec
import com.example.search.domain.index.BoostRule
import com.example.search.domain.query.SearchQuery

/**
 * 상품 검색 명령. [SearchQuery] (입력 표현) + [facetSpecs] + [boostRule] 조합.
 *
 * SearchQuery 의 facets 는 facet 식별자 (이름) 만 담고, 실제 ES aggregation 정의는 [facetSpecs] 가
 * 담당 — 도메인이 facet 정의의 sane range (cardinality 상한) 를 강제한다.
 *
 * [searchId] / [userId] 는 분석용 추적 식별자 — null 가능 (외부 / 익명 검색).
 * SearchProductService 가 호출 후 SearchEvent 비동기 기록에 사용하고, 동일 searchId 로 클릭
 * 이벤트가 join 되어 CTR 계산에 활용된다 (ADR-0018).
 *
 * facetSpecs 는 방어적 복사로 외부 변경에서 보호 — record / data class 가 아닌 일반 class 로 작성하고
 * Java 호출자에게 record 스타일 (`.query()` 등) 접근자를 노출한다.
 */
class SearchProductCommand(
    query: SearchQuery,
    facetSpecs: List<FacetSpec>,
    boostRule: BoostRule,
    searchId: String?,
    userId: String?
) {
    @get:JvmName("query")
    val query: SearchQuery = query

    @get:JvmName("facetSpecs")
    val facetSpecs: List<FacetSpec> = java.util.List.copyOf(facetSpecs)

    @get:JvmName("boostRule")
    val boostRule: BoostRule = boostRule

    // searchId / userId 는 익명 / 외부 검색 호환 위해 null 허용 — 분석에서는 anonymous 처리.
    @get:JvmName("searchId")
    val searchId: String? = searchId

    @get:JvmName("userId")
    val userId: String? = userId

    /** 분석 식별자가 필요 없는 호출자 (단위 테스트 등) 용 단축 생성. */
    constructor(query: SearchQuery, facetSpecs: List<FacetSpec>, boostRule: BoostRule) :
        this(query, facetSpecs, boostRule, null, null)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SearchProductCommand) return false
        return query == other.query &&
            facetSpecs == other.facetSpecs &&
            boostRule == other.boostRule &&
            searchId == other.searchId &&
            userId == other.userId
    }

    override fun hashCode(): Int {
        var result = query.hashCode()
        result = 31 * result + facetSpecs.hashCode()
        result = 31 * result + boostRule.hashCode()
        result = 31 * result + (searchId?.hashCode() ?: 0)
        result = 31 * result + (userId?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "SearchProductCommand(query=$query, facetSpecs=$facetSpecs, boostRule=$boostRule, " +
            "searchId=$searchId, userId=$userId)"
}
