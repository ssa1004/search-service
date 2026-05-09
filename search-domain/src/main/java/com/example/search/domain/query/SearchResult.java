package com.example.search.domain.query;

import com.example.search.domain.facet.FacetResult;
import com.example.search.domain.product.ProductId;

import java.util.List;
import java.util.Objects;

/**
 * 검색 응답.
 *
 * <p>{@code hits} 는 page 단위 — total 은 ES 의 정확한 개수 (track_total_hits true). facet 결과는
 * 요청 시점에만 채워지고 그 외에는 빈 리스트.</p>
 *
 * <p>{@code took} 은 ES 측 실측 ms — 운영 환경에서 slow query 식별에 활용.</p>
 */
public record SearchResult(
        long totalHits,
        long took,
        List<Hit> hits,
        List<FacetResult> facets
) {

    public SearchResult {
        Objects.requireNonNull(hits, "hits");
        Objects.requireNonNull(facets, "facets");
        if (totalHits < 0) {
            throw new IllegalArgumentException("totalHits 음수 불가: " + totalHits);
        }
        hits = List.copyOf(hits);
        facets = List.copyOf(facets);
    }

    public boolean isEmpty() {
        return hits.isEmpty();
    }

    /**
     * 한 건의 검색 hit. {@code score} 는 boost rule 적용 후의 최종 점수.
     */
    public record Hit(
            ProductId id,
            String name,
            String brand,
            String category,
            long priceWon,
            int stockQuantity,
            String status,
            double score
    ) {
        public Hit {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
        }
    }
}
