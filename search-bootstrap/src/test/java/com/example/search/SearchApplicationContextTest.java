package com.example.search;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Spring 컨텍스트 부팅 검증. memory 프로필 + Kafka 비활성으로 외부 의존 없이 부팅 가능.
 *
 * <p>실패 시 빈 의존 그래프 / properties 매핑 / Flyway 마이그레이션 중 하나가 깨졌다는 신호.</p>
 */
@SpringBootTest
@ActiveProfiles({"memory-search", "test"})
@TestPropertySource(properties = {
        "search.engine=memory"
})
class SearchApplicationContextTest {

    @Test
    void contextLoads() {
        // 컨텍스트가 성공적으로 부팅되면 통과.
    }
}
