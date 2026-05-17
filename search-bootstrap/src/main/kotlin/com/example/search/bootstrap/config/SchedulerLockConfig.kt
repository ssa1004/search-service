package com.example.search.bootstrap.config

import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import javax.sql.DataSource

/**
 * ShedLock 설정 (ADR-0014) — 멀티 인스턴스에서 @Scheduled 중복 실행 방지.
 *
 * `defaultLockAtMostFor` 는 fallback — 개별 @SchedulerLock 이 명시하면 그쪽 우선.
 *
 * JDBC provider 가 `shedlock` 테이블에 SELECT FOR UPDATE 로 lease 획득. lease 시간 안에는 다른
 * 인스턴스가 같은 이름의 job 실행 못 함.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "10m")
class SchedulerLockConfig {

    @Bean
    open fun lockProvider(dataSource: DataSource): LockProvider =
        JdbcTemplateLockProvider(
            JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(JdbcTemplate(dataSource))
                .usingDbTime()      // DB clock 사용 — 노드 간 clock skew 무시.
                .build()
        )
}
