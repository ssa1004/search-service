package com.example.search.adapter.out.cdc;

import com.example.search.domain.product.Category;
import com.example.search.domain.product.Product;
import com.example.search.domain.product.ProductId;
import com.example.search.domain.product.ProductStatus;
import com.example.search.domain.shared.Money;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * outbox payload (JSON) ↔ {@link Product} 매핑. 도메인이 Jackson 어노테이션을 가지지 않도록 외부에서
 * 직렬화 책임을 진다.
 */
public final class ProductDtoMapper {

    private ProductDtoMapper() {
    }

    public static String toJson(Product p, ObjectMapper mapper) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", p.id().value());
        node.put("name", p.name());
        node.put("brand", p.brand());
        node.put("category", p.category().name());
        var arr = node.putArray("sizes");
        for (String s : p.sizes()) arr.add(s);
        node.put("priceWon", p.price().won());
        node.put("stockQuantity", p.stockQuantity());
        node.put("status", p.status().name());
        node.put("version", p.version());
        node.put("releasedAt", p.releasedAt().toString());
        node.put("updatedAt", p.updatedAt().toString());
        try {
            return mapper.writeValueAsString(node);
        } catch (IOException e) {
            throw new IllegalStateException("product 직렬화 실패", e);
        }
    }

    public static Product fromJson(String json, ObjectMapper mapper) {
        try {
            ObjectNode n = (ObjectNode) mapper.readTree(json);
            List<String> sizes = new ArrayList<>();
            n.withArray("sizes").forEach(s -> sizes.add(s.asText()));
            return new Product(
                    ProductId.of(n.get("id").asText()),
                    n.get("name").asText(),
                    n.get("brand").asText(),
                    Category.valueOf(n.get("category").asText()),
                    sizes,
                    Money.won(n.get("priceWon").asLong()),
                    n.get("stockQuantity").asInt(),
                    ProductStatus.valueOf(n.get("status").asText()),
                    n.get("version").asLong(),
                    Instant.parse(n.get("releasedAt").asText()),
                    Instant.parse(n.get("updatedAt").asText())
            );
        } catch (IOException e) {
            throw new IllegalStateException("product JSON 파싱 실패: " + json, e);
        }
    }
}
