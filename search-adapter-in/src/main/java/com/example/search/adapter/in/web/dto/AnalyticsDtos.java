package com.example.search.adapter.in.web.dto;

import java.util.List;

/**
 * 분석 admin REST DTO 모음. 도메인 record 와 분리해 응답 schema 안정성 확보.
 */
public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    public record QueryStatDto(String keyword, long count) {
    }

    public record TopQueriesResponse(List<QueryStatDto> queries) {
    }

    public record LatencyResponse(long p50, long p95, long p99, long sampleSize) {
    }

    public record CtrResponse(long searchesWithResults, long clicks, double rate) {
    }
}
