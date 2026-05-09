package com.example.search.domain.analytics;

/**
 * 분석 구간의 검색 응답 latency 분포 — ms 단위.
 *
 * <p>{@code sampleSize} 는 통계 신뢰도 — 1k 미만이면 p99 가 흔들리므로 운영자가 표시 여부를 판단.</p>
 */
public record LatencyPercentiles(
        long p50,
        long p95,
        long p99,
        long sampleSize
) {

    public LatencyPercentiles {
        if (p50 < 0 || p95 < 0 || p99 < 0) {
            throw new IllegalArgumentException("percentile 음수 불가");
        }
        if (sampleSize < 0) {
            throw new IllegalArgumentException("sampleSize 음수 불가");
        }
    }

    public static LatencyPercentiles empty() {
        return new LatencyPercentiles(0, 0, 0, 0);
    }
}
