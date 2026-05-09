package com.example.search.bootstrap.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Clock 빈 — 모든 service 가 직접 {@code Instant.now()} 를 부르지 않고 주입받는다. 테스트에서
 * {@code Clock.fixed} 로 시간 고정 가능.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
