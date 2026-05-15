package com.example.search.domain.query

/**
 * 검색 입력 표현 — REST 요청 / use case 입력의 단일 통로.
 *
 * 도메인 객체이지만 ES 와 직접 매핑되는 형태는 아니다 — adapter-out 의 ES query builder 가 이
 * 객체를 받아 ES `_search` body 로 직렬화한다.
 *
 * - `keyword` — 빈 문자열 가능 (그 경우는 filter 만으로 검색).
 * - `filters` — 정확 일치 (term/terms/range). brand=["nike"], price=[0..200000].
 * - `facets` — 결과로 받고 싶은 분포 차원 (brand, price-range, size).
 * - `sort` — null 이면 relevance score (boost 적용 결과) 기준.
 * - `page` — 0-based.
 *
 * record 의 compact constructor 가 `filters` / `facets` 를 방어 복사하므로 data class 가
 * 아닌 일반 class — equals / hashCode 는 정규화된 필드 기준으로 직접 정의한다.
 */
class SearchQuery(
    keyword: String,
    filters: List<FilterCriterion>,
    facets: List<String>,
    // Java record 가 sort 를 null 검사하지 않으므로 nullable 유지.
    sort: SortSpec?,
    page: Page
) {

    @get:JvmName("keyword")
    val keyword: String

    @get:JvmName("filters")
    val filters: List<FilterCriterion>

    @get:JvmName("facets")
    val facets: List<String>

    @get:JvmName("sort")
    val sort: SortSpec?

    @get:JvmName("page")
    val page: Page

    init {
        this.keyword = keyword
        this.filters = java.util.List.copyOf(filters)
        this.facets = java.util.List.copyOf(facets)
        this.sort = sort
        this.page = page
    }

    fun hasKeyword(): Boolean = keyword.isNotBlank()

    fun hasFilters(): Boolean = filters.isNotEmpty()

    fun wantsFacets(): Boolean = facets.isNotEmpty()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SearchQuery) return false
        return keyword == other.keyword &&
            filters == other.filters &&
            facets == other.facets &&
            sort == other.sort &&
            page == other.page
    }

    override fun hashCode(): Int {
        var result = keyword.hashCode()
        result = 31 * result + filters.hashCode()
        result = 31 * result + facets.hashCode()
        result = 31 * result + (sort?.hashCode() ?: 0)
        result = 31 * result + page.hashCode()
        return result
    }

    override fun toString(): String =
        "SearchQuery[keyword=$keyword, filters=$filters, facets=$facets, " +
            "sort=$sort, page=$page]"

    companion object {
        @JvmStatic
        fun byKeyword(keyword: String, page: Page): SearchQuery =
            SearchQuery(keyword, emptyList(), emptyList(), null, page)
    }
}
