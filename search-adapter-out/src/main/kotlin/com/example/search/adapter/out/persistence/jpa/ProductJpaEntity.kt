package com.example.search.adapter.out.persistence.jpa

import com.example.search.domain.product.Category
import com.example.search.domain.product.Product
import com.example.search.domain.product.ProductId
import com.example.search.domain.product.ProductStatus
import com.example.search.domain.shared.Money
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * Postgres `products` source-of-truth 테이블 매핑.
 *
 * 도메인 [Product] 와 분리 — JPA 어노테이션 / 직렬화 형식은 인프라 책임. `sizes` 는
 * Postgres 의 text[] 가 깔끔하지만 H2 호환을 위해 콤마 구분 string 으로 단순 직렬화.
 *
 * kotlin-jpa 플러그인이 no-arg 합성 ctor 를, kotlin-spring (allOpen) 이 클래스를 자동 open
 * 처리하므로 entity 자체는 일반 \`class\` 로 작성.
 */
@Entity
@Table(name = "products")
class ProductJpaEntity protected constructor() {

    @Id
    @Column(name = "id", length = 64)
    var id: String = ""
        private set

    @Column(name = "name", nullable = false, length = 200)
    var name: String = ""
        private set

    @Column(name = "brand", nullable = false, length = 100)
    var brand: String = ""
        private set

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    var category: Category = Category.SNEAKERS
        private set

    @Column(name = "sizes", nullable = false, length = 500)
    var sizes: String = ""
        private set

    @Column(name = "price_won", nullable = false)
    var priceWon: Long = 0
        private set

    @Column(name = "stock_quantity", nullable = false)
    var stockQuantity: Int = 0
        private set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    var status: ProductStatus = ProductStatus.AVAILABLE
        private set

    @Column(name = "version", nullable = false)
    var version: Long = 0
        private set

    @Column(name = "released_at", nullable = false)
    var releasedAt: Instant = Instant.EPOCH
        private set

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.EPOCH
        private set

    fun toDomain(): Product {
        val sizeList = if (sizes.isBlank()) emptyList() else sizes.split(",").map { it.trim() }
        return Product(
            ProductId.of(id),
            name, brand, category, sizeList,
            Money.won(priceWon),
            stockQuantity, status, version, releasedAt, updatedAt
        )
    }

    companion object {
        @JvmStatic
        fun from(p: Product): ProductJpaEntity {
            val e = ProductJpaEntity()
            e.id = p.id.value
            e.name = p.name
            e.brand = p.brand
            e.category = p.category
            e.sizes = p.sizes.joinToString(",")
            e.priceWon = p.price.won()
            e.stockQuantity = p.stockQuantity
            e.status = p.status
            e.version = p.version
            e.releasedAt = p.releasedAt
            e.updatedAt = p.updatedAt
            return e
        }
    }
}
