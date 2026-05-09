package com.example.search.domain.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * 가격 표현. KRW 만 다룬다고 가정 — 현재 도메인은 단일 통화. 추후 다국가 시 currency
 * 필드 추가.
 *
 * <p>{@code BigDecimal} 을 내부에 두지만 외부에는 {@code long won()} 으로 노출 — KRW 는 소수점이
 * 없고, ES 매핑 측에서 {@code long} (또는 {@code scaled_float}) 로 저장하기 때문에 정수 변환 시점을
 * 도메인이 책임진다.</p>
 */
public record Money(BigDecimal amount) {

    public Money {
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Money 는 음수일 수 없음: " + amount);
        }
        // 소수점 저장 방지 — KRW 는 원 단위.
        amount = amount.setScale(0, RoundingMode.HALF_UP);
    }

    public static Money won(long won) {
        return new Money(BigDecimal.valueOf(won));
    }

    public static Money zero() {
        return new Money(BigDecimal.ZERO);
    }

    public long won() {
        return amount.longValueExact();
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }
}
