package com.example.search.application.savedsearch.port.out;

import com.example.search.domain.product.ProductId;
import com.example.search.domain.savedsearch.SavedSearch;

import java.time.Instant;
import java.util.List;

/**
 * SavedSearch 의 query 를 ES 에 다시 던져 since 이후 신규 매치 product id 만 반환.
 *
 * <p>구현체는 ES query 의 filter 에 {@code updatedAt > since} (strict) 를 추가해 신규/변경된 문서만
 * 후보로 좁힌다. since 와 정확히 같은 시점의 문서는 직전 사이클이 이미 스캔한 것으로 간주해 제외 —
 * 같은 문서가 두 사이클에 걸쳐 매치되어 알림이 중복 발행되는 경계 사례를 막는다. 매번 전체 hit 를
 * 받지 않고 id 만 받음 — 알림에는 id 만 필요.</p>
 *
 * <p>maxResults 로 폭주 방지 — 한 SavedSearch 가 한 사이클에 발행하는 알림 메시지 크기 상한.</p>
 */
public interface SavedSearchMatchFinder {
    List<ProductId> findNewMatches(SavedSearch savedSearch, Instant since, int maxResults);
}
