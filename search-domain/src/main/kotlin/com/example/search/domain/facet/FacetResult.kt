package com.example.search.domain.facet

/**
 * facet 결과 한 개. [name] 은 요청 시 지정한 식별자 (UI 가 어떤 facet 인지 구분).
 *
 * ES aggregation 응답을 도메인 형태로 변환한 결과 — adapter 가 매핑하고 use case 는 그대로
 * 클라이언트에 돌려준다.
 *
 * record 의 compact constructor 가 `buckets` 를 방어 복사하므로 data class 가 아닌 일반
 * class — equals / hashCode 는 정규화된 필드 기준으로 직접 정의한다.
 */
class FacetResult(
    name: String,
    buckets: List<Bucket>
) {

    @get:JvmName("name")
    val name: String

    @get:JvmName("buckets")
    val buckets: List<Bucket>

    init {
        this.name = name
        this.buckets = java.util.List.copyOf(buckets)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FacetResult) return false
        return name == other.name && buckets == other.buckets
    }

    override fun hashCode(): Int = 31 * name.hashCode() + buckets.hashCode()

    override fun toString(): String = "FacetResult[name=$name, buckets=$buckets]"

    /**
     * facet bucket. [key] 는 분포 단위 (brand=nike → "nike", price-range → "100k-200k").
     */
    @JvmRecord
    data class Bucket(
        val key: String,
        val count: Long
    ) {
        init {
            require(count >= 0) { "bucket count 음수 불가: $count" }
        }
    }
}
