package com.example.search.domain;

import com.example.search.domain.product.Category;
import com.example.search.domain.product.Product;
import com.example.search.domain.product.ProductId;
import com.example.search.domain.product.ProductStatus;
import com.example.search.domain.shared.Money;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductTest {

    private static final Instant NOW = Instant.parse("2026-05-09T10:00:00Z");

    @Test
    void create_초기_version_은_1() {
        Product p = sample();
        assertThat(p.version()).isEqualTo(1L);
        assertThat(p.status()).isEqualTo(ProductStatus.AVAILABLE);
    }

    @Test
    void update_시_version_증가하고_releasedAt_은_불변() {
        Product p = sample();
        Instant later = NOW.plusSeconds(3600);
        Product updated = p.update("Air Max 1 Restock", List.of("260", "270"),
                Money.won(180_000L), 5, later);
        assertThat(updated.version()).isEqualTo(2L);
        assertThat(updated.releasedAt()).isEqualTo(p.releasedAt());
        assertThat(updated.updatedAt()).isEqualTo(later);
    }

    @Test
    void markSoldOut_은_재고_0_과_상태_전환() {
        Product p = sample();
        Product so = p.markSoldOut(NOW.plusSeconds(60));
        assertThat(so.status()).isEqualTo(ProductStatus.SOLD_OUT);
        assertThat(so.stockQuantity()).isZero();
        assertThat(so.version()).isEqualTo(2L);
    }

    @Test
    void 음수_재고_금지() {
        assertThatThrownBy(() -> Product.create(ProductId.random(), "x", "Nike",
                Category.SNEAKERS, List.of("260"), Money.won(100_000L), -1, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 빈_이름_금지() {
        assertThatThrownBy(() -> Product.create(ProductId.random(), "  ", "Nike",
                Category.SNEAKERS, List.of("260"), Money.won(100_000L), 1, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void daysSinceRelease_계산() {
        Product p = sample();
        long days = p.daysSinceRelease(NOW.plus(java.time.Duration.ofDays(7)));
        assertThat(days).isEqualTo(7);
    }

    private static Product sample() {
        return Product.create(ProductId.of("p-1"), "Air Max 1", "Nike", Category.SNEAKERS,
                List.of("260", "270"), Money.won(150_000L), 10, NOW);
    }
}
