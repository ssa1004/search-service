package com.example.search.e2e;

import com.example.search.SearchApplication;
import com.example.search.application.command.IndexProductCommand;
import com.example.search.application.command.SearchProductCommand;
import com.example.search.application.port.in.IndexProductUseCase;
import com.example.search.application.port.in.SearchProductUseCase;
import com.example.search.application.port.out.IndexWriterPort;
import com.example.search.application.port.out.SearchIndexProperties;
import com.example.search.domain.facet.FacetSpec;
import com.example.search.domain.index.BoostRule;
import com.example.search.domain.product.Category;
import com.example.search.domain.product.Product;
import com.example.search.domain.product.ProductId;
import com.example.search.domain.query.FilterCriterion;
import com.example.search.domain.query.Page;
import com.example.search.domain.query.SearchQuery;
import com.example.search.domain.query.SearchResult;
import com.example.search.domain.shared.Money;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 Elasticsearch (Testcontainers) 위에서 indexing → 검색 → boost 시나리오 검증.
 *
 * <p>{@code @Tag("integration")} — {@code ./gradlew test} 의 default 에서 제외, {@code ./gradlew
 * integrationTest} 또는 {@code -PincludeIntegration} 으로 명시 실행.</p>
 *
 * <p>로컬에서 docker / colima 가 없으면 skip — Testcontainers 가 ContainerLaunchException 발생
 * 시 JUnit 이 자연스럽게 fail 표시한다.</p>
 */
@Tag("integration")
@SpringBootTest(classes = SearchApplication.class)
@Testcontainers
class ElasticsearchSearchIT {

    @Container
    static final ElasticsearchContainer ES = new ElasticsearchContainer(
            DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.15.3"))
            .withEnv("discovery.type", "single-node")
            .withEnv("xpack.security.enabled", "false")
            .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");

    @DynamicPropertySource
    static void register(DynamicPropertyRegistry registry) {
        registry.add("search.engine", () -> "elasticsearch");
        registry.add("search.elasticsearch-host", ES::getHttpHostAddress);
    }

    @Autowired
    SearchProductUseCase searchUseCase;
    @Autowired
    IndexProductUseCase indexUseCase;
    @Autowired
    IndexWriterPort indexWriter;
    @Autowired
    SearchIndexProperties properties;

    @BeforeAll
    static void waitForES() {
        // ElasticsearchContainer 의 default healthcheck 가 준비 완료까지 대기 — 추가 로직 불필요.
    }

    @Test
    void 인덱스_생성_indexing_검색_facet() {
        // 1) 인덱스 생성 (mapping JSON 적용).
        indexWriter.createIndex(properties.alias());

        // 2) 상품 indexing.
        Instant now = Instant.parse("2026-05-09T10:00:00Z");
        indexUseCase.index(new IndexProductCommand(product("p-1", "Air Max 1", "Nike", now, 150_000L)));
        indexUseCase.index(new IndexProductCommand(product("p-2", "Air Force 1", "Nike", now, 130_000L)));
        indexUseCase.index(new IndexProductCommand(product("p-3", "Stan Smith", "Adidas", now, 110_000L)));

        // ES refresh 대기 — indexing 직후 검색이 결과를 보려면 refresh 가 끝나야 함.
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            SearchResult r = searchUseCase.search(new SearchProductCommand(
                    SearchQuery.byKeyword("Air", Page.first(10)),
                    List.of(),
                    BoostRule.defaults()));
            assertThat(r.totalHits()).isEqualTo(2);
        });

        // 3) facet aggregation.
        SearchResult facetResult = searchUseCase.search(new SearchProductCommand(
                new SearchQuery("", List.of(), List.of("by-brand"), null, Page.first(10)),
                List.of(new FacetSpec.Terms("by-brand", "brand", 10)),
                BoostRule.defaults()));
        assertThat(facetResult.facets()).hasSize(1);
        assertThat(facetResult.facets().get(0).buckets()).extracting("key")
                .containsExactlyInAnyOrder("Nike", "Adidas");

        // 4) 가격 range filter.
        SearchResult expensive = searchUseCase.search(new SearchProductCommand(
                new SearchQuery("",
                        List.of(FilterCriterion.Range.gte("priceWon", 140_000L)),
                        List.of(), null, Page.first(10)),
                List.of(),
                BoostRule.defaults()));
        assertThat(expensive.totalHits()).isEqualTo(1);
        assertThat(expensive.hits().get(0).name()).isEqualTo("Air Max 1");
    }

    private Product product(String id, String name, String brand, Instant releasedAt, long priceWon) {
        return Product.create(ProductId.of(id), name, brand, Category.SNEAKERS,
                List.of("260", "270"), Money.won(priceWon), 10, releasedAt);
    }
}
