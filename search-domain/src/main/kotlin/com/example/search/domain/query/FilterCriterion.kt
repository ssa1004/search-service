package com.example.search.domain.query

/**
 * 필터 한 개. ES query 의 `filter` context 에 들어가므로 score 에 영향을 주지 않는다 (정확
 * 일치만, 점수 가중 없음).
 *
 * 4가지 형태:
 * - `term` — 단일 값 정확 일치 (brand=nike).
 * - `terms` — 다중 값 OR (brand IN nike, adidas).
 * - `range` — 숫자 범위 (price >= 100000 AND price < 200000).
 * - `exists` — 필드 존재 여부.
 *
 * `field()` 는 Java sealed interface 의 접근자 시그니처를 그대로 유지하기 위해 함수로 선언 —
 * 구현체도 동일 시그니처를 노출한다.
 */
sealed interface FilterCriterion {

    fun field(): String

    /**
     * 컴포넌트 변형은 없지만 인터페이스의 `field()` 함수 시그니처를 맞추기 위해 일반 class —
     * equals / hashCode / toString 을 직접 정의한다.
     */
    class Term(
        private val field: String,
        @get:JvmName("value") val value: String
    ) : FilterCriterion {

        override fun field(): String = field

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Term) return false
            return field == other.field && value == other.value
        }

        override fun hashCode(): Int = 31 * field.hashCode() + value.hashCode()

        override fun toString(): String = "Term[field=$field, value=$value]"
    }

    /**
     * record 의 compact constructor 가 `values` 를 방어 복사하므로 일반 class.
     */
    class Terms(
        private val field: String,
        values: List<String>
    ) : FilterCriterion {

        @get:JvmName("values")
        val values: List<String>

        init {
            require(values.isNotEmpty()) { "Terms 필터는 빈 값 불가: $field" }
            this.values = java.util.List.copyOf(values)
        }

        override fun field(): String = field

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Terms) return false
            return field == other.field && values == other.values
        }

        override fun hashCode(): Int = 31 * field.hashCode() + values.hashCode()

        override fun toString(): String = "Terms[field=$field, values=$values]"
    }

    /**
     * 숫자 범위. [from], [to] 둘 다 nullable — null 은 무한대를 의미. 양쪽 inclusive
     * 여부는 [fromInclusive], [toInclusive] 로 분리.
     */
    class Range(
        private val field: String,
        @get:JvmName("from") val from: Long?,
        @get:JvmName("fromInclusive") val fromInclusive: Boolean,
        @get:JvmName("to") val to: Long?,
        @get:JvmName("toInclusive") val toInclusive: Boolean
    ) : FilterCriterion {

        init {
            require(!(from == null && to == null)) {
                "Range 필터는 from/to 둘 다 null 불가: $field"
            }
            require(!(from != null && to != null && from > to)) { "Range from > to: $field" }
        }

        override fun field(): String = field

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Range) return false
            return field == other.field &&
                from == other.from &&
                fromInclusive == other.fromInclusive &&
                to == other.to &&
                toInclusive == other.toInclusive
        }

        override fun hashCode(): Int {
            var result = field.hashCode()
            result = 31 * result + (from?.hashCode() ?: 0)
            result = 31 * result + fromInclusive.hashCode()
            result = 31 * result + (to?.hashCode() ?: 0)
            result = 31 * result + toInclusive.hashCode()
            return result
        }

        override fun toString(): String =
            "Range[field=$field, from=$from, fromInclusive=$fromInclusive, " +
                "to=$to, toInclusive=$toInclusive]"

        companion object {
            @JvmStatic
            fun gte(field: String, from: Long): Range = Range(field, from, true, null, false)

            @JvmStatic
            fun lt(field: String, to: Long): Range = Range(field, null, false, to, false)

            @JvmStatic
            fun between(field: String, from: Long, to: Long): Range =
                Range(field, from, true, to, false)
        }
    }

    /**
     * 컴포넌트 변형은 없지만 인터페이스의 `field()` 함수 시그니처를 맞추기 위해 일반 class.
     */
    class Exists(
        private val field: String
    ) : FilterCriterion {

        override fun field(): String = field

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Exists) return false
            return field == other.field
        }

        override fun hashCode(): Int = field.hashCode()

        override fun toString(): String = "Exists[field=$field]"
    }
}
