package com.example.search.application.analytics.port.in;

import com.example.search.domain.analytics.ClickThroughRate;
import com.example.search.domain.analytics.LatencyPercentiles;
import com.example.search.domain.analytics.QueryStat;

import java.time.Instant;
import java.util.List;

/**
 * 운영자 검색 분석 화면의 4가지 use case 통합 인터페이스.
 *
 * <p>한 인터페이스로 묶은 이유 — 같은 데이터 소스 (search_events + search_clicks) 에서 같은 구간
 * 입력으로 4가지를 보는 운영 화면 한 곳이 주된 호출자다. 분리하면 컨트롤러가 4개 빈 주입 받는
 * 보일러플레이트만 늘어남.</p>
 */
public interface QueryAnalyticsUseCase {

    List<QueryStat> topQueries(Instant from, Instant to, int limit);

    List<QueryStat> zeroResultQueries(Instant from, Instant to, int limit);

    LatencyPercentiles queryLatencyPercentiles(Instant from, Instant to);

    ClickThroughRate clickThroughRate(Instant from, Instant to);
}
