package com.example.search.adapter.out.persistence.jpa;

import com.example.search.domain.product.Category;
import com.example.search.domain.product.Product;
import com.example.search.domain.product.ProductId;
import com.example.search.domain.product.ProductStatus;
import com.example.search.domain.shared.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Postgres `products` source-of-truth 테이블 매핑.
 *
 * <p>도메인 {@link Product} 와 분리 — JPA 어노테이션 / 직렬화 형식은 인프라 책임. {@code sizes} 는
 * Postgres 의 text[] 가 깔끔하지만 H2 호환을 위해 콤마 구분 string 으로 단순 직렬화.</p>
 */
@Entity
@Table(name = "products")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class ProductJpaEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "brand", nullable = false, length = 100)
    private String brand;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private Category category;

    @Column(name = "sizes", nullable = false, length = 500)
    private String sizes;

    @Column(name = "price_won", nullable = false)
    private long priceWon;

    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ProductStatus status;

    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "released_at", nullable = false)
    private Instant releasedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static ProductJpaEntity from(Product p) {
        ProductJpaEntity e = new ProductJpaEntity();
        e.id = p.id().value();
        e.name = p.name();
        e.brand = p.brand();
        e.category = p.category();
        e.sizes = String.join(",", p.sizes());
        e.priceWon = p.price().won();
        e.stockQuantity = p.stockQuantity();
        e.status = p.status();
        e.version = p.version();
        e.releasedAt = p.releasedAt();
        e.updatedAt = p.updatedAt();
        return e;
    }

    public Product toDomain() {
        List<String> sizeList = sizes.isBlank() ? List.of()
                : Arrays.stream(sizes.split(",")).map(String::trim).collect(Collectors.toList());
        return new Product(
                ProductId.of(id),
                name, brand, category, sizeList,
                Money.won(priceWon),
                stockQuantity, status, version, releasedAt, updatedAt);
    }
}
