package com.example.search.adapter.out;

import com.example.search.adapter.out.elasticsearch.InMemorySearchEngineAdapter;
import com.example.search.application.command.SearchProductCommand;
import com.example.search.domain.facet.FacetSpec;
import com.example.search.domain.index.BoostRule;
import com.example.search.domain.index.IndexDocument;
import com.example.search.domain.product.Category;
import com.example.search.domain.product.Product;
import com.example.search.domain.product.ProductId;
import com.example.search.domain.query.FilterCriterion;
import com.example.search.domain.query.Page;
import com.example.search.domain.query.SearchQuery;
import com.example.search.domain.query.SearchResult;
import com.example.search.domain.shared.Money;
import com.example.search.domain.suggest.AutocompleteSuggestion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InMemorySearchEngineAdapterTest {

    private static final Instant NOW = Instant.parse("2026-05-09T10:00:00Z");

    private InMemorySearchEngineAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new InMemorySearchEngineAdapter();
        adapter.index(IndexDocument.from(product("p-1", "Air Max 1", "Nike"), 100L));
        adapter.index(IndexDocument.from(product("p-2", "Air Force 1", "Nike"), 50L));
        adapter.index(IndexDocument.from(product("p-3", "Stan Smith", "Adidas"), 30L));
    }

    @Test
    void 키워드_매칭() {
        SearchProductCommand cmd = new SearchProductCommand(
                SearchQuery.byKeyword("Air", Page.first(10)),
                List.of(),
                BoostRule.defaults());
        SearchResult result = adapter.search(cmd);
        assertThat(result.totalHits()).isEqualTo(2);
        assertThat(result.hits()).extracting(SearchResult.Hit::name)
                .containsExactlyInAnyOrder("Air Max 1", "Air Force 1");
    }

    @Test
    void 키워드_필터_조합() {
        SearchProductCommand cmd = new SearchProductCommand(
                new SearchQuery("",
                        List.of(new FilterCriterion.Term("brand", "Nike")),
                        List.of(), null, Page.first(10)),
                List.of(),
                BoostRule.defaults());
        SearchResult result = adapter.search(cmd);
        assertThat(result.totalHits()).isEqualTo(2);
    }

    @Test
    void 자동완성_prefix_정렬은_clickCount_desc() {
        List<AutocompleteSuggestion> suggestions = adapter.autocomplete("Air", 10);
        assertThat(suggestions).hasSize(2);
        assertThat(suggestions.get(0).text()).isEqualTo("Air Max 1");   // clickCount 100
        assertThat(suggestions.get(1).text()).isEqualTo("Air Force 1"); // clickCount 50
    }

    @Test
    void facet_terms_aggregation() {
        SearchProductCommand cmd = new SearchProductCommand(
                SearchQuery.byKeyword("", Page.first(10)),
                List.of(new FacetSpec.Terms("by-brand", "brand", 10)),
                BoostRule.defaults());
        SearchResult result = adapter.search(cmd);
        assertThat(result.facets()).hasSize(1);
        assertThat(result.facets().get(0).buckets()).extracting("key")
                .containsExactly("Nike", "Adidas");
    }

    @Test
    void external_version_낮은_쓰기는_무시() {
        Product p = product("p-1", "Air Max 1 v2", "Nike");
        // version 을 강제로 0 으로 — 기존이 1 이므로 무시되어야 함.
        IndexDocument old = new IndexDocument(p.id(), "OLD", p.brand(), p.category().name(),
                p.sizes(), p.price().won(), p.stockQuantity(), p.status().name(),
                0L, 0L, p.releasedAt(), p.updatedAt());
        adapter.index(old);
        SearchProductCommand cmd = new SearchProductCommand(
                SearchQuery.byKeyword("Max", Page.first(10)),
                List.of(),
                BoostRule.defaults());
        SearchResult result = adapter.search(cmd);
        assertThat(result.hits()).extracting(SearchResult.Hit::name)
                .contains("Air Max 1");
    }

    @Test
    void incrementClickCount_은_누적() {
        adapter.incrementClickCount(ProductId.of("p-3"));
        adapter.incrementClickCount(ProductId.of("p-3"));
        // p-3 의 clickCount 는 30 + 2 = 32 가 되어 자동완성 점수에 반영되는지 간접 확인 — 새 자동완성
        // 결과에서 p-3 의 score 가 32 로 올라간다.
        List<AutocompleteSuggestion> suggestions = adapter.autocomplete("Stan", 1);
        assertThat(suggestions).hasSize(1);
        assertThat(suggestions.get(0).score()).isEqualTo(32.0);
    }

    private static Product product(String id, String name, String brand) {
        return Product.create(ProductId.of(id), name, brand, Category.SNEAKERS,
                List.of("260"), Money.won(150_000L), 10, NOW);
    }
}
