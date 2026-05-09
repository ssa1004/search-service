package com.example.search.domain.analytics;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchEventTest {

    private static final Instant NOW = Instant.parse("2026-05-09T10:00:00Z");

    @Test
    void zeroResult_은_resultCount_0일_때_true() {
        SearchEvent zero = new SearchEvent("s-1", "nike", "u-1", 0, 50, NOW);
        SearchEvent some = new SearchEvent("s-2", "nike", "u-1", 5, 50, NOW);
        assertThat(zero.isZeroResult()).isTrue();
        assertThat(some.isZeroResult()).isFalse();
    }

    @Test
    void resultCount_음수_예외() {
        assertThatThrownBy(() -> new SearchEvent("s", "k", null, -1, 10, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("resultCount");
    }

    @Test
    void latencyMs_음수_예외() {
        assertThatThrownBy(() -> new SearchEvent("s", "k", null, 0, -1, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("latencyMs");
    }

    @Test
    void keyword_길이_상한_초과시_예외() {
        String tooLong = "a".repeat(SearchEvent.MAX_KEYWORD_LENGTH + 1);
        assertThatThrownBy(() -> new SearchEvent("s", tooLong, null, 0, 10, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("keyword 길이");
    }

    @Test
    void userId_null_허용() {
        SearchEvent anon = new SearchEvent("s", "k", null, 0, 10, NOW);
        assertThat(anon.userId()).isNull();
    }
}
