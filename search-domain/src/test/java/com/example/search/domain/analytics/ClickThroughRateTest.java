package com.example.search.domain.analytics;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClickThroughRateTest {

    @Test
    void of_정상_계산() {
        ClickThroughRate ctr = ClickThroughRate.of(100, 25);
        assertThat(ctr.searchesWithResults()).isEqualTo(100);
        assertThat(ctr.clicks()).isEqualTo(25);
        assertThat(ctr.rate()).isEqualTo(0.25);
    }

    @Test
    void searchesWithResults_0이면_rate_0() {
        ClickThroughRate ctr = ClickThroughRate.of(0, 5);
        assertThat(ctr.rate()).isEqualTo(0.0);
    }

    @Test
    void clicks_0이면_rate_0() {
        ClickThroughRate ctr = ClickThroughRate.of(50, 0);
        assertThat(ctr.rate()).isEqualTo(0.0);
    }
}
