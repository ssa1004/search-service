package com.example.search.adapter.in.web;

import com.example.search.adapter.in.web.dto.SearchDtos;
import com.example.search.application.command.AutocompleteCommand;
import com.example.search.application.command.RecordSearchClickCommand;
import com.example.search.application.command.SuggestRelatedCommand;
import com.example.search.application.port.in.AutocompleteUseCase;
import com.example.search.application.port.in.RecordSearchClickUseCase;
import com.example.search.application.port.in.SearchProductUseCase;
import com.example.search.application.port.in.SuggestRelatedUseCase;
import com.example.search.domain.facet.FacetResult;
import com.example.search.domain.product.ProductId;
import com.example.search.domain.query.SearchResult;
import com.example.search.domain.suggest.AutocompleteSuggestion;
import com.example.search.domain.suggest.RelatedSuggestion;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 검색 / 자동완성 / 관련 키워드 / 클릭 기록 REST endpoint.
 *
 * <p>모든 endpoint 는 도메인 명령으로 변환 후 use case 호출. 컨트롤러 자체는 변환과 응답 매핑만
 * 책임진다.</p>
 */
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchProductUseCase searchUseCase;
    private final AutocompleteUseCase autocompleteUseCase;
    private final SuggestRelatedUseCase relatedUseCase;
    private final RecordSearchClickUseCase clickUseCase;

    @PostMapping("/products")
    public SearchDtos.SearchResponse search(@Valid @RequestBody SearchDtos.SearchRequest request) {
        SearchResult result = searchUseCase.search(SearchRequestMapper.toCommand(request));
        return new SearchDtos.SearchResponse(
                result.totalHits(),
                result.took(),
                request.page(),
                request.size(),
                result.hits().stream().map(SearchDtos.SearchResponse.HitDto::from).toList(),
                toFacetMap(result.facets())
        );
    }

    @GetMapping("/autocomplete")
    public SearchDtos.AutocompleteResponse autocomplete(
            @RequestParam("q") String prefix,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        List<AutocompleteSuggestion> suggestions =
                autocompleteUseCase.suggest(new AutocompleteCommand(prefix, limit));
        return new SearchDtos.AutocompleteResponse(
                suggestions.stream()
                        .map(s -> new SearchDtos.AutocompleteResponse.SuggestionDto(
                                s.text(), s.productId().value(), s.score()))
                        .toList());
    }

    @GetMapping("/related")
    public SearchDtos.RelatedResponse related(
            @RequestParam("q") String keyword,
            @RequestParam(value = "limit", defaultValue = "5") int limit,
            @RequestParam(value = "maxDistance", defaultValue = "2") int maxDistance) {
        List<RelatedSuggestion> related =
                relatedUseCase.suggest(new SuggestRelatedCommand(keyword, limit, maxDistance));
        return new SearchDtos.RelatedResponse(
                related.stream()
                        .map(r -> new SearchDtos.RelatedResponse.RelatedDto(
                                r.suggestedKeyword(), r.popularity(), r.distance()))
                        .toList());
    }

    @PostMapping("/searches/{searchId}/clicks")
    public ResponseEntity<Void> recordClick(@PathVariable("searchId") String searchId,
                                            @Valid @RequestBody SearchDtos.RecordClickRequest req) {
        clickUseCase.record(new RecordSearchClickCommand(
                searchId, req.userId(), ProductId.of(req.productId()),
                req.keyword(), req.rank()));
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    private Map<String, List<SearchDtos.SearchResponse.FacetBucketDto>> toFacetMap(List<FacetResult> facets) {
        Map<String, List<SearchDtos.SearchResponse.FacetBucketDto>> result = new LinkedHashMap<>();
        for (FacetResult f : facets) {
            result.put(f.name(), f.buckets().stream()
                    .map(SearchDtos.SearchResponse.FacetBucketDto::from)
                    .toList());
        }
        return result;
    }
}
