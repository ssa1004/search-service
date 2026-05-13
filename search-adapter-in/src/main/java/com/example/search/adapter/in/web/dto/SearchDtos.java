package com.example.search.adapter.in.web.dto;

import com.example.search.domain.facet.FacetResult;
import com.example.search.domain.query.SearchResult;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * 검색 / 자동완성 / 클릭 / reindex 의 REST DTO 모음. 도메인 객체와 분리 — REST API 의 안정성과 도메인
 * 진화를 분리한다.
 */
public final class SearchDtos {

    private SearchDtos() {
    }

    /** 검색 키워드 길이 상한 — 비정상적으로 긴 입력의 multi_match 비용 폭증 방지. */
    public static final int MAX_KEYWORD_LENGTH = 200;

    /** 한 요청의 filter 수 상한 — bool query 의 must/filter 절 폭증으로 인한 메모리 / 분석 비용 보호. */
    public static final int MAX_FILTERS = 20;

    /** 한 요청의 facet 수 상한 — aggregation 폭증으로 인한 메모리 보호 (ADR-0008 참고). */
    public static final int MAX_FACETS = 10;

    public record SearchRequest(
            @Size(max = MAX_KEYWORD_LENGTH) String keyword,
            @Size(max = MAX_FILTERS) List<FilterDto> filters,
            @Size(max = MAX_FACETS) List<FacetSpecDto> facets,
            SortDto sort,
            @Min(0) int page,
            @Min(1) @Max(100) int size
    ) {
    }

    /** 단일 filter 의 terms values 상한 — terms 필터가 수천 값을 받아 ES 메모리 폭주하는 사례 차단. */
    public static final int MAX_TERMS_VALUES = 100;

    /**
     * 4가지 필터 form 을 DTO 차원에서 표현. {@code op} 으로 분기하고, 각 op 별 필요한 필드만 채워진다.
     *
     * <ul>
     *   <li>op=term — value 필수</li>
     *   <li>op=terms — values 필수</li>
     *   <li>op=range — from / to 중 하나 이상 + fromInclusive / toInclusive (default true / false)</li>
     *   <li>op=exists — field 만</li>
     * </ul>
     */
    public record FilterDto(
            @NotNull String field,
            @NotNull String op,
            String value,
            @Size(max = MAX_TERMS_VALUES) List<String> values,
            Long from,
            Boolean fromInclusive,
            Long to,
            Boolean toInclusive
    ) {
    }

    public record FacetSpecDto(
            @NotNull String name,
            @NotNull String field,
            @NotNull String type,
            Integer size,
            List<RangeBucketDto> buckets
    ) {
        public record RangeBucketDto(@NotNull String key, Long from, Long to) {
        }
    }

    public record SortDto(@NotNull String field, @NotNull String direction) {
    }

    public record SearchResponse(
            long totalHits,
            long took,
            int page,
            int size,
            List<HitDto> hits,
            Map<String, List<FacetBucketDto>> facets
    ) {
        public record HitDto(String id, String name, String brand, String category,
                             long priceWon, int stockQuantity, String status, double score) {
            public static HitDto from(SearchResult.Hit hit) {
                return new HitDto(hit.id().value(), hit.name(), hit.brand(),
                        hit.category(), hit.priceWon(), hit.stockQuantity(),
                        hit.status(), hit.score());
            }
        }

        public record FacetBucketDto(String key, long count) {
            public static FacetBucketDto from(FacetResult.Bucket b) {
                return new FacetBucketDto(b.key(), b.count());
            }
        }
    }

    public record AutocompleteResponse(List<SuggestionDto> suggestions) {
        public record SuggestionDto(String text, String productId, double score) {
        }
    }

    public record RelatedResponse(List<RelatedDto> related) {
        public record RelatedDto(String suggestedKeyword, long popularity, int distance) {
        }
    }

    public record RecordClickRequest(
            @NotEmpty String productId,
            @NotEmpty String userId,
            @NotEmpty @Size(max = 200) String keyword,
            @Min(1) int rank
    ) {
    }

    public record ReindexRequest(@NotEmpty String suffix, boolean dropOld) {
    }

    public record ReindexResponse(String newPhysicalName, long sourceDocCount,
                                  long targetDocCount, boolean swapped) {
    }
}
