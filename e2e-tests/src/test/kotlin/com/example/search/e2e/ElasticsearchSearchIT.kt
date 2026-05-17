package com.example.search.e2e

import com.example.search.SearchApplication
import com.example.search.application.command.IndexProductCommand
import com.example.search.application.command.SearchProductCommand
import com.example.search.application.port.`in`.IndexProductUseCase
import com.example.search.application.port.`in`.SearchProductUseCase
import com.example.search.application.port.out.IndexWriterPort
import com.example.search.application.port.out.SearchIndexProperties
import com.example.search.domain.facet.FacetSpec
import com.example.search.domain.index.BoostRule
import com.example.search.domain.product.Category
import com.example.search.domain.product.Product
import com.example.search.domain.product.ProductId
import com.example.search.domain.query.FilterCriterion
import com.example.search.domain.query.Page
import com.example.search.domain.query.SearchQuery
import com.example.search.domain.shared.Money
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.elasticsearch.ElasticsearchContainer
import org.testcontainers.images.builder.ImageFromDockerfile
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

/**
 * 실제 Elasticsearch (Testcontainers) 위에서 indexing → 검색 → boost 시나리오 검증.
 *
 * `@Tag("integration")` — `./gradlew test` 의 default 에서 제외, `./gradlew integrationTest` 또는
 * `-PincludeIntegration` 으로 명시 실행.
 *
 * 로컬에서 docker / colima 가 없으면 skip — Testcontainers 가 ContainerLaunchException 발생 시
 * JUnit 이 자연스럽게 fail 표시한다.
 */
@Tag("integration")
@SpringBootTest(classes = [SearchApplication::class])
@Testcontainers
class ElasticsearchSearchIT {

    @Autowired
    lateinit var searchUseCase: SearchProductUseCase

    @Autowired
    lateinit var indexUseCase: IndexProductUseCase

    @Autowired
    lateinit var indexWriter: IndexWriterPort

    @Autowired
    lateinit var properties: SearchIndexProperties

    @BeforeAll
    fun waitForES() {
        // ElasticsearchContainer 의 default healthcheck 가 준비 완료까지 대기 — 추가 로직 불필요.
    }

    @Test
    fun `인덱스_생성_indexing_검색_facet`() {
        // 1) 인덱스 생성 (mapping JSON 적용).
        indexWriter.createIndex(properties.alias())

        // 2) 상품 indexing.
        val now = Instant.parse("2026-05-09T10:00:00Z")
        indexUseCase.index(IndexProductCommand(product("p-1", "Air Max 1", "Nike", now, 150_000L)))
        indexUseCase.index(IndexProductCommand(product("p-2", "Air Force 1", "Nike", now, 130_000L)))
        indexUseCase.index(IndexProductCommand(product("p-3", "Stan Smith", "Adidas", now, 110_000L)))

        // ES refresh 대기 — indexing 직후 검색이 결과를 보려면 refresh 가 끝나야 함.
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted {
            val r = searchUseCase.search(
                SearchProductCommand(
                    SearchQuery.byKeyword("Air", Page.first(10)),
                    listOf(),
                    BoostRule.defaults(),
                ),
            )
            assertThat(r.totalHits).isEqualTo(2)
        }

        // 3) facet aggregation.
        val facetResult = searchUseCase.search(
            SearchProductCommand(
                SearchQuery("", listOf(), listOf("by-brand"), null, Page.first(10)),
                listOf(FacetSpec.Terms("by-brand", "brand", 10)),
                BoostRule.defaults(),
            ),
        )
        assertThat(facetResult.facets).hasSize(1)
        assertThat(facetResult.facets[0].buckets).extracting("key")
            .containsExactlyInAnyOrder("Nike", "Adidas")

        // 4) 가격 range filter.
        val expensive = searchUseCase.search(
            SearchProductCommand(
                SearchQuery(
                    "",
                    listOf(FilterCriterion.Range.gte("priceWon", 140_000L)),
                    listOf(),
                    null,
                    Page.first(10),
                ),
                listOf(),
                BoostRule.defaults(),
            ),
        )
        assertThat(expensive.totalHits).isEqualTo(1)
        assertThat(expensive.hits[0].name).isEqualTo("Air Max 1")
    }

    private fun product(id: String, name: String, brand: String, releasedAt: Instant, priceWon: Long): Product =
        Product.create(
            ProductId.of(id),
            name,
            brand,
            Category.SNEAKERS,
            listOf("260", "270"),
            Money.won(priceWon),
            10,
            releasedAt,
        )

    companion object {
        /**
         * 운영 mapping 이 nori (ADR-0015) 를 참조하므로 IT 도 같은 이미지 (analysis-nori 포함) 로
         * 검증해야 mapping 적용이 가능하다. docker/elasticsearch/Dockerfile 을 빌드해 받은 image tag
         * 를 ElasticsearchContainer 에 전달.
         */
        @Container
        @JvmField
        val ES: ElasticsearchContainer = ElasticsearchContainer(
            DockerImageName.parse(noriImage()).asCompatibleSubstituteFor(
                "docker.elastic.co/elasticsearch/elasticsearch",
            ),
        )
            .withEnv("discovery.type", "single-node")
            .withEnv("xpack.security.enabled", "false")
            .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")

        private fun noriImage(): String =
            ImageFromDockerfile("search-service-es-nori-test", false)
                .withDockerfile(Path.of("..", "docker", "elasticsearch", "Dockerfile"))
                .get()

        @JvmStatic
        @DynamicPropertySource
        fun register(registry: DynamicPropertyRegistry) {
            registry.add("search.engine") { "elasticsearch" }
            registry.add("search.elasticsearch-host") { ES.httpHostAddress }
        }
    }
}
