package com.example.search.adapter.out.synonym;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.example.search.adapter.out.elasticsearch.SearchEngineIOException;
import com.example.search.application.port.out.SearchIndexProperties;
import com.example.search.application.synonym.port.out.SynonymIndexUpdaterPort;
import com.example.search.domain.synonym.SynonymGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ES 인덱스 settings 의 synonym graph filter 를 RDB 의 그룹으로 reload.
 *
 * <p>ES 8.x 는 synonym filter 의 rule 을 변경하려면 인덱스가 close 상태여야 한다 — open 상태로
 * settings 를 PUT 하면 {@code illegal_argument_exception ("non updateable setting")}. 흐름:</p>
 * <ol>
 *   <li>인덱스 close — 검색 / 색인 모두 차단됨.</li>
 *   <li>{@code _settings} PUT — analyzer + synonym filter 갱신.</li>
 *   <li>인덱스 open.</li>
 * </ol>
 *
 * <p>그룹이 0개일 때도 동작 보장 — synonym filter 는 빈 rule 리스트로 reload (결과적으로 분석에
 * 영향 없음).</p>
 *
 * <p>장기적으로는 새 물리 인덱스 + alias swap (ADR-0005) 으로 zero-downtime 적용이 안전하지만,
 * 초기엔 운영자가 한가한 시간에 직접 호출하는 본 path 가 단순하고 충분.</p>
 */
@RequiredArgsConstructor
@Slf4j
public class ElasticsearchSynonymIndexUpdater implements SynonymIndexUpdaterPort {

    /** 운영 mapping 의 ko_standard analyzer 와 짝을 이룸. ADR-0017 참고. */
    public static final String FILTER_NAME = "domain_synonyms";

    private final ElasticsearchClient client;
    private final SearchIndexProperties properties;

    @Override
    public int reload(List<SynonymGroup> groups) {
        String alias = properties.alias();
        String physical = resolvePhysicalName(alias);
        String settingsJson = buildSettingsJson(groups);

        try {
            client.indices().close(c -> c.index(physical));
            try {
                client.indices().putSettings(p -> p
                        .index(physical)
                        .withJson(new StringReader(settingsJson)));
                log.info("synonym settings PUT 완료 index={} groups={}", physical, groups.size());
            } finally {
                // open 은 close 후 무조건 — settings PUT 실패 시에도 인덱스를 닫힌 채 두면 운영 망가짐.
                client.indices().open(o -> o.index(physical));
            }
            return groups.size();
        } catch (IOException e) {
            throw new SearchEngineIOException("ES synonym reload 실패: " + e.getMessage(), e);
        }
    }

    /**
     * alias 가 가리키는 물리 인덱스 이름을 찾아 settings PUT 의 대상으로 사용. alias 자체에 PUT 하면
     * 8.x 가 거부 (alias has more than one index 또는 ambiguous) 사례가 있어 명시 우회.
     */
    private String resolvePhysicalName(String alias) {
        try {
            var aliases = client.indices().getAlias(g -> g.name(alias));
            if (aliases.result().isEmpty()) {
                throw new IllegalStateException(
                        "alias 가 가리키는 인덱스 없음 — synonym 적용 전 인덱스가 먼저 만들어져야 함: " + alias);
            }
            return aliases.result().keySet().iterator().next();
        } catch (IOException e) {
            throw new SearchEngineIOException("alias 조회 실패: " + e.getMessage(), e);
        }
    }

    /**
     * synonym graph filter 한 종류만 들어있는 minimal settings JSON. 기존 다른 analyzer / filter 는
     * ES 가 PUT settings 시 partial merge 로 보존한다.
     */
    private String buildSettingsJson(List<SynonymGroup> groups) {
        String rulesJsonArray = groups.stream()
                .map(g -> "\"" + escapeJsonString(g.toElasticsearchRule()) + "\"")
                .collect(Collectors.joining(","));
        return """
                {
                  "analysis": {
                    "filter": {
                      "%s": {
                        "type": "synonym_graph",
                        "synonyms": [%s]
                      }
                    }
                  }
                }
                """.formatted(FILTER_NAME, rulesJsonArray);
    }

    /** 운영 등록 단계에서 백슬래시 / 쉼표 / 화살표는 막고 있으므로 추가로 escape 할 문자는 큰따옴표만. */
    private String escapeJsonString(String raw) {
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
