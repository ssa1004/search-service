package com.example.search.domain.product

import com.example.search.domain.shared.Money
import java.time.Duration
import java.time.Instant

/**
 * 상품 — source DB (Postgres `products`) 에 저장되는 권위 (source of truth) 모델.
 *
 * 여기서 ES 로 indexing 될 때는 [com.example.search.domain.index.IndexDocument] 로 변환된다
 * (도메인 모델 ≠ 인덱스 문서 — text/keyword/autocomplete 같은 다중 필드 분리는 인덱스 측 표현).
 *
 * 상태 변경 시 [version] 증가 — ES 의 external version 으로 활용해 동시 indexing 충돌을
 * 막는다 (구 버전 문서가 새 버전을 덮어쓰는 이른바 lost update 방지). ADR-0006 참조.
 *
 * record 의 compact constructor 가 `sizes` 를 방어 복사하므로 data class 가 아닌 일반 class —
 * equals / hashCode 는 정규화된 필드 기준으로 직접 정의한다.
 */
class Product(
    id: ProductId,
    name: String,
    brand: String,
    category: Category,
    sizes: List<String>,
    price: Money,
    stockQuantity: Int,
    status: ProductStatus,
    version: Long,
    releasedAt: Instant,
    updatedAt: Instant
) {

    @get:JvmName("id")
    val id: ProductId

    @get:JvmName("name")
    val name: String

    @get:JvmName("brand")
    val brand: String

    @get:JvmName("category")
    val category: Category

    @get:JvmName("sizes")
    val sizes: List<String>

    @get:JvmName("price")
    val price: Money

    @get:JvmName("stockQuantity")
    val stockQuantity: Int

    @get:JvmName("status")
    val status: ProductStatus

    @get:JvmName("version")
    val version: Long

    @get:JvmName("releasedAt")
    val releasedAt: Instant

    @get:JvmName("updatedAt")
    val updatedAt: Instant

    init {
        require(name.isNotBlank()) { "name 은 빈 값 불가" }
        require(stockQuantity >= 0) { "stock 은 음수 불가: $stockQuantity" }
        require(version >= 0) { "version 은 음수 불가: $version" }

        this.id = id
        this.name = name
        this.brand = brand
        this.category = category
        this.sizes = java.util.List.copyOf(sizes)
        this.price = price
        this.stockQuantity = stockQuantity
        this.status = status
        this.version = version
        this.releasedAt = releasedAt
        this.updatedAt = updatedAt
    }

    /**
     * 부분 갱신. [version] + 1, [updatedAt] 갱신. 가격/재고/상태는 변할 수 있으나 id /
     * brand / category 는 불변 — commerce 도메인에서 카테고리 재분류는 별도 마이그레이션 작업.
     */
    fun update(
        name: String,
        sizes: List<String>,
        price: Money,
        stockQuantity: Int,
        now: Instant
    ): Product = Product(
        id, name, brand, category, sizes, price, stockQuantity,
        this.status, this.version + 1, releasedAt, now
    )

    fun markSoldOut(now: Instant): Product = Product(
        id, name, brand, category, sizes, price, 0,
        ProductStatus.SOLD_OUT, this.version + 1, releasedAt, now
    )

    fun markDiscontinued(now: Instant): Product = Product(
        id, name, brand, category, sizes, price, stockQuantity,
        ProductStatus.DISCONTINUED, this.version + 1, releasedAt, now
    )

    /**
     * 출시 후 경과 일수 — boost rule 의 신상품 decay 계산에 사용.
     */
    fun daysSinceRelease(now: Instant): Long = Duration.between(releasedAt, now).toDays()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Product) return false
        return id == other.id &&
            name == other.name &&
            brand == other.brand &&
            category == other.category &&
            sizes == other.sizes &&
            price == other.price &&
            stockQuantity == other.stockQuantity &&
            status == other.status &&
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
        result = 31 * result + price.hashCode()
        result = 31 * result + stockQuantity
        result = 31 * result + status.hashCode()
        result = 31 * result + version.hashCode()
        result = 31 * result + releasedAt.hashCode()
        result = 31 * result + updatedAt.hashCode()
        return result
    }

    override fun toString(): String =
        "Product[id=$id, name=$name, brand=$brand, category=$category, sizes=$sizes, " +
            "price=$price, stockQuantity=$stockQuantity, status=$status, version=$version, " +
            "releasedAt=$releasedAt, updatedAt=$updatedAt]"

    companion object {
        /**
         * 신규 상품 등록.
         */
        @JvmStatic
        fun create(
            id: ProductId,
            name: String,
            brand: String,
            category: Category,
            sizes: List<String>,
            price: Money,
            stockQuantity: Int,
            now: Instant
        ): Product = Product(
            id, name, brand, category, sizes, price, stockQuantity,
            ProductStatus.AVAILABLE, 1L, now, now
        )
    }
}
