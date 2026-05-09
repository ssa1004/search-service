package com.example.search.e2e;

import com.example.search.SearchApplication;
import com.example.search.application.command.AutocompleteCommand;
import com.example.search.application.command.IndexProductCommand;
import com.example.search.application.command.RecordSearchClickCommand;
import com.example.search.application.command.SearchProductCommand;
import com.example.search.application.port.in.AutocompleteUseCase;
import com.example.search.application.port.in.IndexProductUseCase;
import com.example.search.application.port.in.RecordSearchClickUseCase;
import com.example.search.application.port.in.SearchProductUseCase;
import com.example.search.domain.facet.FacetSpec;
import com.example.search.domain.index.BoostRule;
import com.example.search.domain.product.Category;
import com.example.search.domain.product.Product;
import com.example.search.domain.product.ProductId;
import com.example.search.domain.query.Page;
import com.example.search.domain.query.SearchQuery;
import com.example.search.domain.query.SearchResult;
import com.example.search.domain.shared.Money;
import com.example.search.domain.suggest.AutocompleteSuggestion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 메모리 모드 e2e — 실제 Spring 컨텍스트 부팅 + 도메인 use case 호출 시나리오. ES / Kafka 미사용.
 *
 * <p>Testcontainers 가 없어도 e2e 흐름의 절반 이상이 검증되어 PR CI 에서 빠른 피드백.</p>
 *
 * <p>실제 ES / Kafka 통합 흐름은 {@code @Tag("integration")} 의 별도 IT 가 검증
 * (Testcontainers).</p>
 */
@SpringBootTest(classes = SearchApplication.class)
@ActiveProfiles("memory-search")
@TestPropertySource(properties = {
        "search.engine=memory"
})
class SearchEndToEndTest {

    @Autowired
    SearchProductUseCase searchUseCase;
    @Autowired
    AutocompleteUseCase autocompleteUseCase;
    @Autowired
    IndexProductUseCase indexUseCase;
    @Autowired
    RecordSearchClickUseCase clickUseCase;

    private static final Instant NOW = Instant.parse("2026-05-09T10:00:00Z");

    @Test
    void 인덱싱_검색_자동완성_클릭_full_flow() {
        // 1) 상품 indexing
        indexUseCase.index(new IndexProductCommand(product("p-1", "Air Max 1", "Nike",
                Category.SNEAKERS, 150_000L)));
        indexUseCase.index(new IndexProductCommand(product("p-2", "Air Force 1", "Nike",
                Category.SNEAKERS, 130_000L)));
        indexUseCase.index(new IndexProductCommand(product("p-3", "Stan Smith", "Adidas",
                Category.SNEAKERS, 110_000L)));

        // 2) 키워드 검색
        SearchProductCommand searchCmd = new SearchProductCommand(
                SearchQuery.byKeyword("Air", Page.first(10)),
                List.of(new FacetSpec.Terms("by-brand", "brand", 10)),
                BoostRule.defaults());
        SearchResult result = searchUseCase.search(searchCmd);
        assertThat(result.totalHits()).isEqualTo(2);
        assertThat(result.hits()).extracting("name")
                .containsExactlyInAnyOrder("Air Max 1", "Air Force 1");
        assertThat(result.facets()).hasSize(1);
        assertThat(result.facets().get(0).buckets()).extracting("key")
                .containsExactly("Nike");

        // 3) 자동완성
        List<AutocompleteSuggestion> suggestions = autocompleteUseCase.suggest(
                new AutocompleteCommand("Air", 5));
        assertThat(suggestions).hasSize(2);

        // 4) 클릭 기록 → ES 의 clickCount += 1
        clickUseCase.record(new RecordSearchClickCommand("search-1", "user-1",
                ProductId.of("p-2"), "Air", 2));
        clickUseCase.record(new RecordSearchClickCommand("search-2", "user-2",
                ProductId.of("p-2"), "Air", 1));

        // 5) 다음 자동완성 — clickCount 기준 정렬로 p-2 가 위에
        List<AutocompleteSuggestion> rankedSuggestions = autocompleteUseCase.suggest(
                new AutocompleteCommand("Air", 5));
        assertThat(rankedSuggestions.get(0).productId().value()).isEqualTo("p-2");
    }

    @Test
    void 빈_결과_시나리오() {
        SearchResult result = searchUseCase.search(new SearchProductCommand(
                SearchQuery.byKeyword("nonexistent-keyword-xyzabc", Page.first(10)),
                List.of(),
                BoostRule.defaults()));
        assertThat(result.isEmpty()).isTrue();
        assertThat(result.totalHits()).isZero();
    }

    private Product product(String id, String name, String brand, Category cat, long priceWon) {
        return Product.create(ProductId.of(id), name, brand, cat,
                List.of("260", "270"), Money.won(priceWon), 10, NOW);
    }
}
