package com.example.search.adapter.out.synonym;

import com.example.search.application.synonym.port.out.SynonymIndexUpdaterPort;
import com.example.search.domain.synonym.SynonymGroup;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * memory 모드 / 단위 테스트용 no-op 어댑터 — RDB 등록만 검증하고 ES 호출은 무시.
 *
 * <p>실 운영 모드에서는 {@link ElasticsearchSynonymIndexUpdater} 가 이 빈을 대체한다.</p>
 */
@Slf4j
public class NoopSynonymIndexUpdater implements SynonymIndexUpdaterPort {

    @Override
    public int reload(List<SynonymGroup> groups) {
        log.debug("synonym reload skipped (memory 모드) groups={}", groups.size());
        return groups.size();
    }
}
