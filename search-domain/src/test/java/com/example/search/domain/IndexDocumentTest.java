package com.example.search.domain;

import com.example.search.domain.index.BoostRule;
import com.example.search.domain.index.IndexAlias;
import com.example.search.domain.index.IndexDocument;
import com.example.search.domain.product.Category;
import com.example.search.domain.product.Product;
import com.example.search.domain.product.ProductId;
import com.example.search.domain.shared.Money;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IndexDocumentTest {

    private static final Instant NOW = Instant.parse("2026-05-09T10:00:00Z");

    @Test
    void from_Product_은_clickCount_0_으로_시작() {
        Product p = sample();
        IndexDocument doc = IndexDocument.from(p);
        assertThat(doc.clickCount()).isZero();
        assertThat(doc.id()).isEqualTo(p.id());
        assertThat(doc.brand()).isEqualTo(p.brand());
        assertThat(doc.priceWon()).isEqualTo(p.price().won());
    }

    @Test
    void from_Product_clickCount_은_기존값_보존() {
        Product p = sample();
        IndexDocument doc = IndexDocument.from(p, 42L);
        assertThat(doc.clickCount()).isEqualTo(42L);
    }

    @Test
    void DISCONTINUED_은_isSearchable_false() {
        Product p = sample().markDiscontinued(NOW.plusSeconds(1));
        IndexDocument doc = IndexDocument.from(p);
        assertThat(doc.isSearchable()).isFalse();
    }

    @Test
    void IndexAlias_물리_이름과_alias_같으면_거부() {
        assertThatThrownBy(() -> new IndexAlias("products", "products"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void IndexAlias_physicalNameFor_suffix_조립() {
        String name = IndexAlias.physicalNameFor("products", "v202605");
        assertThat(name).isEqualTo("products-v202605");
    }

    @Test
    void BoostRule_default_는_정상_생성() {
        BoostRule r = BoostRule.defaults();
        assertThat(r.isDisabled()).isFalse();
        assertThat(r.popularityWeight()).isPositive();
        assertThat(r.freshnessHalfLife()).isEqualTo(Duration.ofDays(30));
    }

    @Test
    void BoostRule_상한_초과_거부() {
        assertThatThrownBy(() -> new BoostRule(BoostRule.MAX_POPULARITY_WEIGHT + 1, Duration.ofDays(30)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Product sample() {
        return Product.create(ProductId.of("p-1"), "Air Max 1", "Nike", Category.SNEAKERS,
                List.of("260", "270"), Money.won(150_000L), 10, NOW);
    }
}
