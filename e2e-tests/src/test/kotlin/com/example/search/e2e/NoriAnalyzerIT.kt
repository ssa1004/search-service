package com.example.search.e2e

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.http.HttpHost
import org.apache.http.entity.ContentType
import org.apache.http.nio.entity.NStringEntity
import org.assertj.core.api.Assertions.assertThat
import org.elasticsearch.client.Request
import org.elasticsearch.client.RestClient
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.testcontainers.elasticsearch.ElasticsearchContainer
import org.testcontainers.images.builder.ImageFromDockerfile
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.nio.file.Path

/**
 * nori 한국어 형태소 분석기 동작 검증 (ADR-0015).
 *
 * Testcontainers 가 docker/elasticsearch/Dockerfile 을 빌드 (analysis-nori 플러그인 포함) 후 실행.
 * low-level REST 로 mapping JSON 을 그대로 PUT 하고 _analyze API 호출 결과 검증 — Java Client
 * builder DSL 의 nori 표현 복잡도 회피.
 */
@Tag("integration")
@Testcontainers
class NoriAnalyzerIT {

    @Test
    fun `user_dictionary_의_조던1_은_단일_토큰`() {
        val tokens = analyze("에어 조던1 시카고")
        // user_dictionary 가 "조던1" 을 단일 토큰으로 보존.
        assertThat(tokens).contains("조던1")
        // nori_part_of_speech 가 조사 / 어미 제거 — "에어", "시카고" 는 명사로 살아남는다.
        assertThat(tokens.toSet()).contains("에어", "시카고")
    }

    @Test
    fun `한국어_조사_가_제거된다`() {
        val tokens = analyze("나이키의 신상품")
        // "의" / "을/를" 등 조사는 제거되어야 한다 — nori_part_of_speech 의 default stoptag.
        assertThat(tokens).doesNotContain("의")
        assertThat(tokens).contains("나이키")
    }

    private fun analyze(text: String): List<String> {
        val req = Request("POST", "/test-nori/_analyze")
        val body = OM.createObjectNode()
            .put("analyzer", "ko_standard")
            .put("text", text)
            .toString()
        req.entity = NStringEntity(body, ContentType.APPLICATION_JSON)
        val resp = client.performRequest(req)
        val root = OM.readTree(resp.entity.content)
        return root.path("tokens").map { it.path("token").asText() }
    }

    companion object {
        const val NORI_IMAGE: String = "search-service-es-nori-test:latest"

        @JvmField
        val ES: ElasticsearchContainer = ElasticsearchContainer(
            DockerImageName.parse(buildNoriImage()).asCompatibleSubstituteFor(
                "docker.elastic.co/elasticsearch/elasticsearch",
            ),
        )
            .withEnv("discovery.type", "single-node")
            .withEnv("xpack.security.enabled", "false")
            .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")

        /**
         * Dockerfile 빌드를 ImageFromDockerfile 로 미리 수행 — 산출 image tag 를 ElasticsearchContainer
         * 가 받아쓴다. 두 번째 인자 false 는 cache 사용.
         */
        @JvmStatic
        fun buildNoriImage(): String =
            ImageFromDockerfile(NORI_IMAGE, false)
                .withDockerfile(Path.of("..", "docker", "elasticsearch", "Dockerfile"))
                .get()

        private lateinit var client: RestClient
        private val OM: ObjectMapper = ObjectMapper()

        @JvmStatic
        @BeforeAll
        fun start() {
            ES.start()
            client = RestClient.builder(HttpHost.create(ES.httpHostAddress)).build()

            // products-mapping.json 의 핵심 부분만 (settings.analysis) 가져온 인덱스 — _analyze 가
            // 인덱스 settings 를 참조한다.
            val body = """
                {
                  "settings": {
                    "analysis": {
                      "tokenizer": {
                        "nori_user_dict": {
                          "type": "nori_tokenizer",
                          "decompound_mode": "mixed",
                          "discard_punctuation": "true",
                          "user_dictionary_rules": ["조던1"]
                        }
                      },
                      "analyzer": {
                        "ko_standard": {
                          "type": "custom",
                          "tokenizer": "nori_user_dict",
                          "filter": ["lowercase", "asciifolding", "nori_part_of_speech", "nori_readingform"]
                        }
                      }
                    }
                  }
                }
                """.trimIndent()
            val put = Request("PUT", "/test-nori")
            put.entity = NStringEntity(body, ContentType.APPLICATION_JSON)
            client.performRequest(put)
        }

        @JvmStatic
        @AfterAll
        fun stop() {
            client.close()
            ES.stop()
        }
    }
}
