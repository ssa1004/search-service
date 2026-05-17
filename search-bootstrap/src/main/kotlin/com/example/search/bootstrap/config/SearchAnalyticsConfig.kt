package com.example.search.bootstrap.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor
import java.util.concurrent.ThreadPoolExecutor

/**
 * 분석 SearchEvent 비동기 INSERT 전용 thread pool (ADR-0018).
 *
 * 검색 응답 thread 와 분리 — 분석 INSERT 가 느려져도 검색 latency 에 영향 없음.
 *
 *  - core 4 / max 8 / queue 1000 — 평시 부하는 core 로 흡수, 순간 burst 는 queue, 그래도 넘치면 max
 *    까지 확장.
 *  - `DiscardOldestPolicy` — queue 초과 시 *오래된* 이벤트부터 버린다. 분석은 표본 기반이라 일부 손실
 *    허용 가능. caller 차단보다 낫다.
 *
 * `@EnableAsync` 도 같이 — bootstrap 어디서든 한 번만 활성하면 충분.
 */
@Configuration
@EnableAsync
class SearchAnalyticsConfig {

    @Bean(name = ["searchAnalyticsExecutor"])
    open fun searchAnalyticsExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 4
        executor.maxPoolSize = 8
        executor.queueCapacity = 1000
        executor.setThreadNamePrefix("search-analytics-")
        executor.setRejectedExecutionHandler(ThreadPoolExecutor.DiscardOldestPolicy())
        executor.setWaitForTasksToCompleteOnShutdown(true)
        executor.setAwaitTerminationSeconds(10)
        executor.initialize()
        return executor
    }
}
