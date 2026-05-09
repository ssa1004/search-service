package com.example.search.domain.analytics;

/**
 * 검색 → 클릭 전환율.
 *
 * <p>분모는 결과 1건 이상이었던 검색 수 — 결과 0건 검색은 분모에 포함되지 않는다 (사용자가 클릭할
 * 게 아예 없었으므로). 그렇지 않으면 zero-result 가 늘 때마다 CTR 이 자연 하락해 시그널 흐림.</p>
 *
 * <p>분자는 같은 구간의 클릭 이벤트 수 — 한 검색에서 여러 번 클릭한 경우도 모두 카운트.</p>
 */
public record ClickThroughRate(
        long searchesWithResults,
        long clicks,
        double rate
) {

    public ClickThroughRate {
        if (searchesWithResults < 0 || clicks < 0) {
            throw new IllegalArgumentException("음수 불가");
        }
        if (rate < 0 || rate > 1.0001) {
            throw new IllegalArgumentException("rate 0..1 범위 위반: " + rate);
        }
    }

    public static ClickThroughRate of(long searchesWithResults, long clicks) {
        double r = searchesWithResults == 0 ? 0.0 : (double) clicks / searchesWithResults;
        return new ClickThroughRate(searchesWithResults, clicks, r);
    }
}
