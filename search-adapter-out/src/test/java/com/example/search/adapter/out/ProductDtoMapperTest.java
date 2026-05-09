package com.example.search.adapter.out;

import com.example.search.adapter.out.cdc.ProductDtoMapper;
import com.example.search.domain.product.Category;
import com.example.search.domain.product.Product;
import com.example.search.domain.product.ProductId;
import com.example.search.domain.shared.Money;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductDtoMapperTest {

    private static final Instant NOW = Instant.parse("2026-05-09T10:00:00Z");

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void 직렬화_역직렬화_왕복_동등() {
        Product original = Product.create(ProductId.of("p-1"), "Air Max", "Nike",
                Category.SNEAKERS, List.of("260", "270"), Money.won(150_000L), 10, NOW);
        String json = ProductDtoMapper.toJson(original, mapper);
        Product round = ProductDtoMapper.fromJson(json, mapper);
        assertThat(round).isEqualTo(original);
    }

    @Test
    void 잘못된_JSON_은_명시적_예외() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> ProductDtoMapper.fromJson("{not json}", mapper))
                .isInstanceOf(IllegalStateException.class);
    }
}
