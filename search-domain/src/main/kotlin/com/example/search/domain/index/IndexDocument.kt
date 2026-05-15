package com.example.search.domain.index

import com.example.search.domain.product.Category
import com.example.search.domain.product.Product
import com.example.search.domain.product.ProductStatus
import java.time.Duration
import java.time.Instant

/**
 * ES 에 저장될 문서 표현. 도메인 [Product] 와 다르다 — text/keyword/autocomplete 같은 다중
 * 필드 분리는 ES 측의 분석기 (analyzer) 매핑 책임이고, 여기서는 평탄한 형태만 둔다.
 *
 * 매핑 측에서:
 * - `name` → text (standard analyzer) + name.autocomplete (edge_ngram) +
 *   name.keyword (정렬용)
 * - `brand` → keyword (faceted aggregation)
 * - `priceWon` → long
 * - `clickCount` → long (boost rule 의 인기도 시그널)
 *
 * [version] 은 ES 의 external version 으로 사용 — 동시 갱신 시 구 버전이 새 버전을 덮어쓰는
 * lost update 를 ES 가 거부한다 (ADR-0006).
 *
 * record 의 compact constructor 가 `sizes` 를 방어 복사하므로 data class 가 아닌 일반 class —
 * equals / hashCode 는 정규화된 필드 기준으로 직접 정의한다.
 */
class IndexDocument(
    id: com.example.search.domain.product.ProductId,
    name: String,
    brand: String,
    category: String,
    sizes: List<String>,
    priceWon: Long,
    stockQuantity: Int,
    status: String,
    clickCount: Long,
    version: Long,
    releasedAt: Instant,
    updatedAt: Instant
) {

    @get:JvmName("id")
    val id: com.example.search.domain.product.ProductId

    @get:JvmName("name")
    val name: String

    @get:JvmName("brand")
    val brand: String

    @get:JvmName("category")
    val category: String

    @get:JvmName("sizes")
    val sizes: List<String>

    @get:JvmName("priceWon")
    val priceWon: Long

    @get:JvmName("stockQuantity")
    val stockQuantity: Int

    @get:JvmName("status")
    val status: String

    @get:JvmName("clickCount")
    val clickCount: Long

    @get:JvmName("version")
    val version: Long

    @get:JvmName("releasedAt")
    val releasedAt: Instant

    @get:JvmName("updatedAt")
    val updatedAt: Instant

    init {
        this.id = id
        this.name = name
        this.brand = brand
        this.category = category
        this.sizes = java.util.List.copyOf(sizes)
        this.priceWon = priceWon
        this.stockQuantity = stockQuantity
        this.status = status
        this.clickCount = clickCount
        this.version = version
        this.releasedAt = releasedAt
        this.updatedAt = updatedAt
    }

    fun isSearchable(): Boolean = ProductStatus.DISCONTINUED.name != status

    /**
     * 출시일로부터 경과 일수 — boost decay 계산용.
     */
    fun daysSinceRelease(now: Instant): Long = Duration.between(releasedAt, now).toDays()

    fun categoryEnum(): Category = Category.valueOf(category)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IndexDocument) return false
        return id == other.id &&
            name == other.name &&
            brand == other.brand &&
            category == other.category &&
            sizes == other.sizes &&
            priceWon == other.priceWon &&
            stockQuantity == other.stockQuantity &&
            status == other.status &&
            clickCount == other.clickCount &&
            version == other.version &&
            releasedAt == other.releasedAt &&
            updatedAt == other.updatedAt
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + brand.hashCode()
        result = 31 * result + category.hashCode()
        result = 31 * result + sizes.hashCode()
        result = 31 * result + priceWon.hashCode()
        result = 31 * result + stockQuantity
        result = 31 * result + status.hashCode()
        result = 31 * result + clickCount.hashCode()
        result = 31 * result + version.hashCode()
        result = 31 * result + releasedAt.hashCode()
        result = 31 * result + updatedAt.hashCode()
        return result
    }

    override fun toString(): String =
        "IndexDocument[id=$id, name=$name, brand=$brand, category=$category, sizes=$sizes, " +
            "priceWon=$priceWon, stockQuantity=$stockQuantity, status=$status, " +
            "clickCount=$clickCount, version=$version, releasedAt=$releasedAt, " +
            "updatedAt=$updatedAt]"

    companion object {
        /**
         * source 도메인 → 인덱스 문서 변환. [clickCount] 는 0 으로 시작 (별도 update 에서 누적).
         */
        @JvmStatic
        fun from(product: Product): IndexDocument = IndexDocument(
            product.id,
            product.name,
            product.brand,
            product.category.name,
            product.sizes,
            product.price.won(),
            product.stockQuantity,
            product.status.name,
            0L,
            product.version,
            product.releasedAt,
            product.updatedAt
        )

        /**
         * source 도메인 + 기존 click 누적값 → 인덱스 문서 변환. reindex 시 이전 click 시그널을 잃지
         * 않도록 외부에서 주입.
         */
        @JvmStatic
        fun from(product: Product, clickCount: Long): IndexDocument = IndexDocument(
            product.id,
            product.name,
            product.brand,
            product.category.name,
            product.sizes,
            product.price.won(),
            product.stockQuantity,
            product.status.name,
            clickCount,
            product.version,
            product.releasedAt,
            product.updatedAt
        )
    }
}
