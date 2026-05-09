package com.example.search.domain.product;

import com.example.search.domain.shared.Money;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 상품 — source DB (Postgres `products`) 에 저장되는 권위 (source of truth) 모델.
 *
 * <p>여기서 ES 로 indexing 될 때는 {@link com.example.search.domain.index.IndexDocument} 로 변환된다
 * (도메인 모델 ≠ 인덱스 문서 — text/keyword/autocomplete 같은 다중 필드 분리는 인덱스 측 표현).</p>
 *
 * <p>상태 변경 시 {@link #version} 증가 — ES 의 external version 으로 활용해 동시 indexing 충돌을
 * 막는다 (구 버전 문서가 새 버전을 덮어쓰는 이른바 lost update 방지). ADR-0006 참조.</p>
 */
public record Product(
        ProductId id,
        String name,
        String brand,
        Category category,
        List<String> sizes,
        Money price,
        int stockQuantity,
        ProductStatus status,
        long version,
        Instant releasedAt,
        Instant updatedAt
) {

    public Product {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(brand, "brand");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(sizes, "sizes");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(releasedAt, "releasedAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name 은 빈 값 불가");
        }
        if (stockQuantity < 0) {
            throw new IllegalArgumentException("stock 은 음수 불가: " + stockQuantity);
        }
        if (version < 0) {
            throw new IllegalArgumentException("version 은 음수 불가: " + version);
        }
        sizes = List.copyOf(sizes);
    }

    /**
     * 신규 상품 등록.
     */
    public static Product create(ProductId id, String name, String brand, Category category,
                                 List<String> sizes, Money price, int stockQuantity, Instant now) {
        return new Product(id, name, brand, category, sizes, price, stockQuantity,
                ProductStatus.AVAILABLE, 1L, now, now);
    }

    /**
     * 부분 갱신. {@code version} + 1, {@code updatedAt} 갱신. 가격/재고/상태는 변할 수 있으나 id /
     * brand / category 는 불변 — commerce 도메인에서 카테고리 재분류는 별도 마이그레이션 작업.
     */
    public Product update(String name, List<String> sizes, Money price, int stockQuantity, Instant now) {
        return new Product(id, name, brand, category, sizes, price, stockQuantity,
                this.status, this.version + 1, releasedAt, now);
    }

    public Product markSoldOut(Instant now) {
        return new Product(id, name, brand, category, sizes, price, 0,
                ProductStatus.SOLD_OUT, this.version + 1, releasedAt, now);
    }

    public Product markDiscontinued(Instant now) {
        return new Product(id, name, brand, category, sizes, price, stockQuantity,
                ProductStatus.DISCONTINUED, this.version + 1, releasedAt, now);
    }

    /**
     * 출시 후 경과 일수 — boost rule 의 신상품 decay 계산에 사용.
     */
    public long daysSinceRelease(Instant now) {
        return java.time.Duration.between(releasedAt, now).toDays();
    }
}
