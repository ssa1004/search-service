package com.example.search;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * application.yml 에 명시한 HikariCP 튜닝 (ADR-0009) 이 실제 빈에 반영됐는지 회귀 보호.
 *
 * <p>Spring Boot 자동 구성이 변하거나 datasource starter 가 교체될 때 운영 default 로 회귀하면
 * 운영 부하 / 누수 디버깅이 느려진다 — 핵심 값만 assert 한다.</p>
 *
 * <p>주의: test 프로필은 leak detection 등을 비활성하므로 여기서는 운영 default 프로필로
 * 부팅해 검증한다.</p>
 */
@SpringBootTest
@ActiveProfiles("memory-search")
@TestPropertySource(properties = {
        "search.engine=memory"
})
class HikariPoolConfigTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void hikari_pool_은_application_yml_의_명시값을_반영한다() {
        assertThat(dataSource).isInstanceOf(HikariDataSource.class);
        HikariDataSource hikari = (HikariDataSource) dataSource;
        assertThat(hikari.getMaximumPoolSize()).isEqualTo(16);
        assertThat(hikari.getMinimumIdle()).isEqualTo(4);
        assertThat(hikari.getConnectionTimeout()).isEqualTo(3_000L);
        assertThat(hikari.getMaxLifetime()).isEqualTo(1_740_000L);
        assertThat(hikari.getLeakDetectionThreshold()).isEqualTo(30_000L);
        assertThat(hikari.getPoolName()).isEqualTo("SearchHikariPool");
    }
}
