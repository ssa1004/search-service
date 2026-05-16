package com.example.search.adapter.`in`.web

import com.example.search.adapter.`in`.web.dto.SearchDtos
import com.example.search.application.command.AutocompleteCommand
import com.example.search.application.command.RecordSearchClickCommand
import com.example.search.application.command.SuggestRelatedCommand
import com.example.search.application.port.`in`.AutocompleteUseCase
import com.example.search.application.port.`in`.RecordSearchClickUseCase
import com.example.search.application.port.`in`.SearchProductUseCase
import com.example.search.application.port.`in`.SuggestRelatedUseCase
import com.example.search.domain.facet.FacetResult
import com.example.search.domain.product.ProductId
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 검색 / 자동완성 / 관련 키워드 / 클릭 기록 REST endpoint.
 *
 * 모든 endpoint 는 도메인 명령으로 변환 후 use case 호출. 컨트롤러 자체는 변환과 응답 매핑만
 * 책임진다.
 */
@RestController
@RequestMapping("/api/v1/search")
class SearchController(
    private val searchUseCase: SearchProductUseCase,
    private val autocompleteUseCase: AutocompleteUseCase,
    private val relatedUseCase: SuggestRelatedUseCase,
    private val clickUseCase: RecordSearchClickUseCase
) {

    @PostMapping("/products")
    fun search(@Valid @RequestBody request: SearchDtos.SearchRequest): SearchDtos.SearchResponse {
        val result = searchUseCase.search(SearchRequestMapper.toCommand(request))
        return SearchDtos.SearchResponse(
            result.totalHits,
            result.took,
            request.page,
            request.size,
            result.hits.map { SearchDtos.SearchResponse.HitDto.from(it) },
            toFacetMap(result.facets)
        )
    }

    @GetMapping("/autocomplete")
    fun autocomplete(
        @RequestParam("q") prefix: String,
        @RequestParam(value = "limit", defaultValue = "10") limit: Int
    ): SearchDtos.AutocompleteResponse {
        val suggestions = autocompleteUseCase.suggest(AutocompleteCommand(prefix, limit))
        return SearchDtos.AutocompleteResponse(
            suggestions.map {
                SearchDtos.AutocompleteResponse.SuggestionDto(
                    it.text, it.productId.value, it.score
                )
            }
        )
    }

    @GetMapping("/related")
    fun related(
        @RequestParam("q") keyword: String,
        @RequestParam(value = "limit", defaultValue = "5") limit: Int,
        @RequestParam(value = "maxDistance", defaultValue = "2") maxDistance: Int
    ): SearchDtos.RelatedResponse {
        val related = relatedUseCase.suggest(SuggestRelatedCommand(keyword, limit, maxDistance))
        return SearchDtos.RelatedResponse(
            related.map {
                SearchDtos.RelatedResponse.RelatedDto(
                    it.suggestedKeyword, it.popularity, it.distance
                )
            }
        )
    }

    @PostMapping("/searches/{searchId}/clicks")
    fun recordClick(
        @PathVariable("searchId") searchId: String,
        @Valid @RequestBody req: SearchDtos.RecordClickRequest
    ): ResponseEntity<Void> {
        clickUseCase.record(
            RecordSearchClickCommand(
                searchId, req.userId, ProductId.of(req.productId),
                req.keyword, req.rank
            )
        )
        return ResponseEntity.status(HttpStatus.ACCEPTED).build()
    }

    private fun toFacetMap(
        facets: List<FacetResult>
    ): Map<String, List<SearchDtos.SearchResponse.FacetBucketDto>> {
        // Jackson serialization 순서를 그대로 보존하기 위해 LinkedHashMap.
        val result = LinkedHashMap<String, List<SearchDtos.SearchResponse.FacetBucketDto>>()
        for (f in facets) {
            result[f.name] = f.buckets.map { SearchDtos.SearchResponse.FacetBucketDto.from(it) }
        }
        return result
    }
}
