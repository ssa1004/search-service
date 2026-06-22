package com.example.search.bootstrap.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.servers.Server
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * OpenAPI spec metadata — 생성되는 `/v3/api-docs` 문서를 결정적(deterministic)으로 만든다.
 *
 * springdoc 기본값은 title="OpenAPI definition", version="v0", 그리고 요청이 들어온
 * host:port 를 그대로 `servers` 에 박는다. 후자는 부팅 포트마다 값이 바뀌어 commit 된 spec 과
 * CI 가 재생성한 spec 의 diff 를 흔들리게 한다 (drift gate flake). 여기서 server 를 상대경로
 * `/` 하나로 고정하고 info 를 실제 서비스 메타로 채워 spec 을 재현 가능하게 만든다.
 */
@Configuration
class OpenApiConfig {

    @Bean
    open fun searchServiceOpenApi(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("Search Service API")
                    .version("v1")
                    .description(
                        "commerce 상품 검색 서비스 REST API — 키워드 검색, 자동완성, " +
                            "faceted filter, 무중단 reindex, synonym/analytics/CDC DLT 운영.",
                    )
                    .license(License().name("MIT").url("https://opensource.org/licenses/MIT")),
            )
            // 환경 독립적인 상대 server — 포트/호스트에 무관하게 동일한 spec 산출.
            .addServersItem(Server().url("/").description("relative to deployment host"))
}
