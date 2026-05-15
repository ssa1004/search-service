package com.example.search.domain.shared

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 가격 표현. KRW 만 다룬다고 가정 — 현재 도메인은 단일 통화. 추후 다국가 시 currency
 * 필드 추가.
 *
 * [BigDecimal] 을 내부에 두지만 외부에는 `long won()` 으로 노출 — KRW 는 소수점이
 * 없고, ES 매핑 측에서 `long` (또는 `scaled_float`) 로 저장하기 때문에 정수 변환 시점을
 * 도메인이 책임진다.
 *
 * 생성자에서 입력값을 원 단위로 정규화 (scale 0) 하므로 data class 가 아닌 일반 class —
 * record 의 compact constructor 처럼 컴포넌트를 변형해야 하고, equals/hashCode 는
 * 정규화된 [amount] 기준으로 직접 정의한다.
 */
class Money(amount: BigDecimal) {

    @get:JvmName("amount")
    val amount: BigDecimal

    init {
        // 소수점 저장 방지 — KRW 는 원 단위.
        if (amount.signum() < 0) {
            throw IllegalArgumentException("Money 는 음수일 수 없음: $amount")
        }
        this.amount = amount.setScale(0, RoundingMode.HALF_UP)
    }

    fun won(): Long = amount.longValueExact()

    fun isZero(): Boolean = amount.signum() == 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Money) return false
        return amount == other.amount
    }

    override fun hashCode(): Int = amount.hashCode()

    override fun toString(): String = "Money[amount=$amount]"

    companion object {
        @JvmStatic
        fun won(won: Long): Money = Money(BigDecimal.valueOf(won))

        @JvmStatic
        fun zero(): Money = Money(BigDecimal.ZERO)
    }
}
