package com.example.search.domain.index;

import com.example.search.domain.product.Category;
import com.example.search.domain.product.Product;
import com.example.search.domain.product.ProductId;
import com.example.search.domain.product.ProductStatus;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * ES 에 저장될 문서 표현. 도메인 {@link Product} 와 다르다 — text/keyword/autocomplete 같은 다중
 * 필드 분리는 ES 측의 분석기 (analyzer) 매핑 책임이고, 여기서는 평탄한 형태만 둔다.
 *
 * <p>매핑 측에서:</p>
 * <ul>
 *   <li>{@code name} → text (standard analyzer) + name.autocomplete (edge_ngram) +
 *       name.keyword (정렬용)</li>
 *   <li>{@code brand} → keyword (faceted aggregation)</li>
 *   <li>{@code priceWon} → long</li>
 *   <li>{@code clickCount} → long (boost rule 의 인기도 시그널)</li>
 * </ul>
 *
 * <p>{@code version} 은 ES 의 external version 으로 사용 — 동시 갱신 시 구 버전이 새 버전을 덮어쓰는
 * lost update 를 ES 가 거부한다 (ADR-0006).</p>
 */
public record IndexDocument(
        ProductId id,
        String name,
        String brand,
        String category,
        List<String> sizes,
        long priceWon,
        int stockQuantity,
        String status,
        long clickCount,
        long version,
        Instant releasedAt,
        Instant updatedAt
) {

    public IndexDocument {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(brand, "brand");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(sizes, "sizes");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(releasedAt, "releasedAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        sizes = List.copyOf(sizes);
    }

    /**
     * source 도메인 → 인덱스 문서 변환. {@code clickCount} 는 0 으로 시작 (별도 update 에서 누적).
     */
    public static IndexDocument from(Product product) {
        return new IndexDocument(
                product.id(),
                product.name(),
                product.brand(),
                product.category().name(),
                product.sizes(),
                product.price().won(),
                product.stockQuantity(),
                product.status().name(),
                0L,
                product.version(),
                product.releasedAt(),
                product.updatedAt()
        );
    }

    /**
     * source 도메인 + 기존 click 누적값 → 인덱스 문서 변환. reindex 시 이전 click 시그널을 잃지
     * 않도록 외부에서 주입.
     */
    public static IndexDocument from(Product product, long clickCount) {
        return new IndexDocument(
                product.id(),
                product.name(),
                product.brand(),
                product.category().name(),
                product.sizes(),
                product.price().won(),
                product.stockQuantity(),
                product.status().name(),
                clickCount,
                product.version(),
                product.releasedAt(),
                product.updatedAt()
        );
    }

    public boolean isSearchable() {
        return !ProductStatus.DISCONTINUED.name().equals(status);
    }

    /**
     * 출시일로부터 경과 일수 — boost decay 계산용.
     */
    public long daysSinceRelease(Instant now) {
        return java.time.Duration.between(releasedAt, now).toDays();
    }

    public Category categoryEnum() {
        return Category.valueOf(category);
    }
}
