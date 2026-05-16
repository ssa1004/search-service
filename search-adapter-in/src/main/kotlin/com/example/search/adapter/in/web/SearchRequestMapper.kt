package com.example.search.adapter.`in`.web

import com.example.search.adapter.`in`.web.dto.SearchDtos
import com.example.search.application.command.SearchProductCommand
import com.example.search.domain.facet.FacetSpec
import com.example.search.domain.index.BoostRule
import com.example.search.domain.query.FilterCriterion
import com.example.search.domain.query.Page
import com.example.search.domain.query.SearchQuery
import com.example.search.domain.query.SortSpec

/**
 * REST DTO → 도메인 명령 매핑. 컨트롤러에서 분리해 단위 테스트 가능하게 한다.
 */
object SearchRequestMapper {

    @JvmStatic
    fun toCommand(req: SearchDtos.SearchRequest): SearchProductCommand {
        val query = SearchQuery(
            req.keyword ?: "",
            toFilters(req.filters),
            toFacetNames(req.facets),
            toSort(req.sort),
            Page(req.page, req.size)
        )
        return SearchProductCommand(query, toFacetSpecs(req.facets), BoostRule.defaults())
    }

    private fun toFilters(dtos: List<SearchDtos.FilterDto>?): List<FilterCriterion> {
        if (dtos.isNullOrEmpty()) return emptyList()
        return dtos.map { toFilter(it) }
    }

    private fun toFilter(d: SearchDtos.FilterDto): FilterCriterion = when (d.op) {
        "term" -> FilterCriterion.Term(d.field, d.value!!)
        "terms" -> FilterCriterion.Terms(d.field, d.values!!)
        "range" -> FilterCriterion.Range(
            d.field,
            d.from,
            d.fromInclusive == null || d.fromInclusive,
            d.to,
            d.toInclusive != null && d.toInclusive
        )
        "exists" -> FilterCriterion.Exists(d.field)
        else -> throw IllegalArgumentException("알 수 없는 filter op: ${d.op}")
    }

    private fun toFacetNames(dtos: List<SearchDtos.FacetSpecDto>?): List<String> {
        if (dtos == null) return emptyList()
        return dtos.map { it.name }
    }

    private fun toFacetSpecs(dtos: List<SearchDtos.FacetSpecDto>?): List<FacetSpec> {
        if (dtos == null) return emptyList()
        return dtos.map { toFacetSpec(it) }
    }

    private fun toFacetSpec(d: SearchDtos.FacetSpecDto): FacetSpec = when (d.type) {
        "terms" -> FacetSpec.Terms(d.name, d.field, d.size ?: 10)
        "range" -> {
            val buckets = d.buckets!!.map { b ->
                FacetSpec.Range.Bucket(b.key, b.from, b.to)
            }
            FacetSpec.Range(d.name, d.field, buckets)
        }
        else -> throw IllegalArgumentException("알 수 없는 facet type: ${d.type}")
    }

    private fun toSort(sort: SearchDtos.SortDto?): SortSpec? {
        if (sort == null) return null
        val direction = if ("desc".equals(sort.direction, ignoreCase = true)) {
            SortSpec.Direction.DESC
        } else {
            SortSpec.Direction.ASC
        }
        return SortSpec(sort.field, direction)
    }
}
