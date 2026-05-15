package com.example.search.domain.facet

/**
 * 단일 facet 요청 명세. ES 의 aggregation 한 개에 대응.
 *
 * 두 형태:
 * - [Terms] — keyword 필드의 값 분포 (brand 별 개수).
 * - [Range] — 숫자 필드의 구간 분포 (가격대별 개수).
 *
 * 도메인이 cardinality 상한 ([Terms.MAX_SIZE]) 을 강제 — ADR-0008 의 메모리 보호.
 *
 * `name()` / `field()` 는 Java sealed interface 의 접근자 시그니처를 그대로 유지하기 위해
 * 함수로 선언 — 구현체도 동일 시그니처의 함수를 노출한다.
 */
sealed interface FacetSpec {

    fun name(): String

    fun field(): String

    /**
     * size 검증만 하고 컴포넌트 변형이 없지만, 인터페이스의 `name()` / `field()` 함수
     * 시그니처를 맞추기 위해 일반 class — equals / hashCode / toString 을 직접 정의한다.
     */
    class Terms(
        private val name: String,
        private val field: String,
        @get:JvmName("size") val size: Int
    ) : FacetSpec {

        init {
            require(size > 0 && size <= MAX_SIZE) { "terms facet size 1..$MAX_SIZE: $size" }
        }

        override fun name(): String = name

        override fun field(): String = field

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Terms) return false
            return name == other.name && field == other.field && size == other.size
        }

        override fun hashCode(): Int {
            var result = name.hashCode()
            result = 31 * result + field.hashCode()
            result = 31 * result + size
            return result
        }

        override fun toString(): String = "Terms[name=$name, field=$field, size=$size]"

        companion object {
            /**
             * terms aggregation 의 default 상한. 한 facet 이 100 개 bucket 을 넘으면 UI 도 의미 없고
             * ES 메모리 사용량도 급증.
             */
            const val MAX_SIZE: Int = 100
        }
    }

    /**
     * record 의 compact constructor 가 `buckets` 를 방어 복사하므로 일반 class —
     * equals / hashCode / toString 을 직접 정의한다.
     */
    class Range(
        private val name: String,
        private val field: String,
        buckets: List<Bucket>
    ) : FacetSpec {

        @get:JvmName("buckets")
        val buckets: List<Bucket>

        init {
            require(buckets.isNotEmpty()) { "range facet bucket 은 1개 이상" }
            this.buckets = java.util.List.copyOf(buckets)
        }

        override fun name(): String = name

        override fun field(): String = field

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Range) return false
            return name == other.name && field == other.field && buckets == other.buckets
        }

        override fun hashCode(): Int {
            var result = name.hashCode()
            result = 31 * result + field.hashCode()
            result = 31 * result + buckets.hashCode()
            return result
        }

        override fun toString(): String =
            "Range[name=$name, field=$field, buckets=$buckets]"

        /**
         * 범위 한 개. [from] / [to] 둘 다 nullable — null 은 무한대.
         */
        @JvmRecord
        data class Bucket(
            val key: String,
            val from: Long?,
            val to: Long?
        ) {
            init {
                require(!(from == null && to == null)) { "bucket from/to 둘 다 null 불가: $key" }
            }
        }
    }
}
