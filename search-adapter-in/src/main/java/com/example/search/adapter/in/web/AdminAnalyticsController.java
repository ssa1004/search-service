package com.example.search.adapter.in.web;

import com.example.search.adapter.in.web.dto.AnalyticsDtos;
import com.example.search.application.analytics.port.in.QueryAnalyticsUseCase;
import com.example.search.domain.analytics.ClickThroughRate;
import com.example.search.domain.analytics.LatencyPercentiles;
import com.example.search.domain.analytics.QueryStat;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * 운영자 검색 분석 admin endpoint (ADR-0018).
 *
 * <p>모든 endpoint 는 운영 보안 게이트 (네트워크 분리 또는 인증 미들웨어) 뒤에 둔다. 시간 구간은
 * ISO-8601 instant 로 받음 — 운영 화면이 dashboard / chart 라이브러리와 직접 연동.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/analytics")
@RequiredArgsConstructor
public class AdminAnalyticsController {

    /** 한 호출 default limit — 운영 화면의 일반적인 top N. */
    private static final int DEFAULT_LIMIT = 20;

    private final QueryAnalyticsUseCase analytics;

    @GetMapping("/queries/top")
    public AnalyticsDtos.TopQueriesResponse top(
            @RequestParam("from") Instant from,
            @RequestParam("to") Instant to,
            @RequestParam(value = "limit", defaultValue = "" + DEFAULT_LIMIT) int limit) {
        List<QueryStat> stats = analytics.topQueries(from, to, limit);
        return new AnalyticsDtos.TopQueriesResponse(toDtos(stats));
    }

    @GetMapping("/queries/zero-result")
    public AnalyticsDtos.TopQueriesResponse zeroResult(
            @RequestParam("from") Instant from,
            @RequestParam("to") Instant to,
            @RequestParam(value = "limit", defaultValue = "" + DEFAULT_LIMIT) int limit) {
        List<QueryStat> stats = analytics.zeroResultQueries(from, to, limit);
        return new AnalyticsDtos.TopQueriesResponse(toDtos(stats));
    }

    @GetMapping("/latency")
    public AnalyticsDtos.LatencyResponse latency(
            @RequestParam("from") Instant from,
            @RequestParam("to") Instant to) {
        LatencyPercentiles p = analytics.queryLatencyPercentiles(from, to);
        return new AnalyticsDtos.LatencyResponse(p.p50(), p.p95(), p.p99(), p.sampleSize());
    }

    @GetMapping("/ctr")
    public AnalyticsDtos.CtrResponse ctr(
            @RequestParam("from") Instant from,
            @RequestParam("to") Instant to) {
        ClickThroughRate ctr = analytics.clickThroughRate(from, to);
        return new AnalyticsDtos.CtrResponse(
                ctr.searchesWithResults(), ctr.clicks(), ctr.rate());
    }

    private List<AnalyticsDtos.QueryStatDto> toDtos(List<QueryStat> stats) {
        return stats.stream()
                .map(s -> new AnalyticsDtos.QueryStatDto(s.keyword(), s.count()))
                .toList();
    }
}
