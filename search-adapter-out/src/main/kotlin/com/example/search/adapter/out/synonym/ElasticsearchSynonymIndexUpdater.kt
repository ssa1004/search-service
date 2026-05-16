package com.example.search.adapter.out.synonym

import co.elastic.clients.elasticsearch.ElasticsearchClient
import com.example.search.adapter.out.elasticsearch.SearchEngineIOException
import com.example.search.application.port.out.SearchIndexProperties
import com.example.search.application.synonym.port.out.SynonymIndexUpdaterPort
import com.example.search.domain.synonym.SynonymGroup
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.StringReader

/**
 * ES 인덱스 settings 의 synonym graph filter 를 RDB 의 그룹으로 reload.
 *
 * ES 8.x 는 synonym filter 의 rule 을 변경하려면 인덱스가 close 상태여야 한다 — open 상태로
 * settings 를 PUT 하면 `illegal_argument_exception ("non updateable setting")`. 흐름:
 * 1. 인덱스 close — 검색 / 색인 모두 차단됨.
 * 2. `_settings` PUT — analyzer + synonym filter 갱신.
 * 3. 인덱스 open.
 *
 * 그룹이 0개일 때도 동작 보장 — synonym filter 는 빈 rule 리스트로 reload (결과적으로 분석에
 * 영향 없음).
 *
 * 장기적으로는 새 물리 인덱스 + alias swap (ADR-0005) 으로 zero-downtime 적용이 안전하지만,
 * 초기엔 운영자가 한가한 시간에 직접 호출하는 본 path 가 단순하고 충분.
 */
class ElasticsearchSynonymIndexUpdater(
    private val client: ElasticsearchClient,
    private val properties: SearchIndexProperties
) : SynonymIndexUpdaterPort {

    override fun reload(groups: List<SynonymGroup>): Int {
        val alias = properties.alias()
        val physical = resolvePhysicalName(alias)
        val settingsJson = buildSettingsJson(groups)

        try {
            client.indices().close { c -> c.index(physical) }
            try {
                client.indices().putSettings { p ->
                    p.index(physical).withJson(StringReader(settingsJson))
                }
                log.info("synonym settings PUT 완료 index={} groups={}", physical, groups.size)
            } finally {
                // open 은 close 후 무조건 — settings PUT 실패 시에도 인덱스를 닫힌 채 두면 운영 망가짐.
                client.indices().open { o -> o.index(physical) }
            }
            return groups.size
        } catch (e: IOException) {
            throw SearchEngineIOException("ES synonym reload 실패: ${e.message}", e)
        }
    }

    /**
     * alias 가 가리키는 물리 인덱스 이름을 찾아 settings PUT 의 대상으로 사용. alias 자체에 PUT 하면
     * 8.x 가 거부 (alias has more than one index 또는 ambiguous) 사례가 있어 명시 우회.
     */
    private fun resolvePhysicalName(alias: String): String {
        try {
            val aliases = client.indices().getAlias { g -> g.name(alias) }
            if (aliases.result().isEmpty()) {
                throw IllegalStateException(
                    "alias 가 가리키는 인덱스 없음 — synonym 적용 전 인덱스가 먼저 만들어져야 함: $alias"
                )
            }
            return aliases.result().keys.iterator().next()
        } catch (e: IOException) {
            throw SearchEngineIOException("alias 조회 실패: ${e.message}", e)
        }
    }

    /**
     * synonym graph filter 한 종류만 들어있는 minimal settings JSON. 기존 다른 analyzer / filter 는
     * ES 가 PUT settings 시 partial merge 로 보존한다.
     */
    private fun buildSettingsJson(groups: List<SynonymGroup>): String {
        val rulesJsonArray = groups.joinToString(",") { g ->
            "\"" + escapeJsonString(g.toElasticsearchRule()) + "\""
        }
        return """
            {
              "analysis": {
                "filter": {
                  "$FILTER_NAME": {
                    "type": "synonym_graph",
                    "synonyms": [$rulesJsonArray]
                  }
                }
              }
            }
            """.trimIndent()
    }

    /** 운영 등록 단계에서 백슬래시 / 쉼표 / 화살표는 막고 있으므로 추가로 escape 할 문자는 큰따옴표만. */
    private fun escapeJsonString(raw: String): String =
        raw.replace("\\", "\\\\").replace("\"", "\\\"")

    companion object {
        /** 운영 mapping 의 ko_standard analyzer 와 짝을 이룸. ADR-0017 참고. */
        const val FILTER_NAME: String = "domain_synonyms"

        private val log = LoggerFactory.getLogger(ElasticsearchSynonymIndexUpdater::class.java)
    }
}
