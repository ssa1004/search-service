package com.example.search.bootstrap.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * JPA 엔티티 / 리포지토리 스캔 위치를 adapter-out 모듈로 명시.
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.example.search.adapter.out")
@EntityScan(basePackages = "com.example.search.adapter.out")
public class JpaConfig {
}
