package com.example.search.domain;

import com.example.search.domain.shared.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void won_생성_정수_왕복() {
        Money m = Money.won(150_000L);
        assertThat(m.won()).isEqualTo(150_000L);
    }

    @Test
    void 음수_금지() {
        assertThatThrownBy(() -> new Money(BigDecimal.valueOf(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 소수점_입력은_원_단위로_반올림() {
        // KRW 는 소수점이 없음 — 들어와도 강제 반올림.
        Money m = new Money(new BigDecimal("100.6"));
        assertThat(m.won()).isEqualTo(101L);
    }

    @Test
    void zero_helper() {
        assertThat(Money.zero().isZero()).isTrue();
    }
}
