package com.example.search.application.analytics.port.out

import com.example.search.domain.analytics.LatencyPercentiles
import com.example.search.domain.analytics.QueryStat
import java.time.Instant

/**
 * Analytics 조회 port — 운영 화면 / 대시보드의 데이터 소스.
 *
 * 구현체는 `search_events` 테이블에서 GROUP BY / aggregation 으로 집계. 큰 데이터에서는
 * 운영 DB 가 아니라 별도 OLAP (ClickHouse, BigQuery) 로 옮길 수 있도록 인터페이스로 분리.
 */
interface SearchEventAnalyticsRepository {

    /**
     * 가장 많이 검색된 keyword top N. 결과 0건 여부 무관 — 전체 검색 빈도 기준.
     */
    fun topQueries(from: Instant, to: Instant, limit: Int): List<QueryStat>

    /**
     * 결과 0건이 자주 났던 keyword top N — 동의어 / 사전 보강 후보.
     */
    fun zeroResultQueries(from: Instant, to: Instant, limit: Int): List<QueryStat>

    /**
     * 구간 latency percentiles + sample size.
     */
    fun latencyPercentiles(from: Instant, to: Instant): LatencyPercentiles

    /**
     * 결과 1건 이상이었던 검색 수 — CTR 분모.
     */
    fun countSearchesWithResults(from: Instant, to: Instant): Long

    /**
     * 같은 구간의 클릭 이벤트 수 — CTR 분자. 클릭 테이블은 RecordSearchClickService 가 채운다.
     */
    fun countClicks(from: Instant, to: Instant): Long
}
