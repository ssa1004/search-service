package com.example.search.domain.event;

import com.example.search.domain.product.Product;
import com.example.search.domain.product.ProductId;

import java.time.Instant;
import java.util.Objects;

/**
 * source DB (Postgres `products`) 의 INSERT/UPDATE/DELETE 변경을 표현하는 CDC 이벤트.
 *
 * <p>실제 운영에서는 Debezium 이 이 형태의 이벤트를 만들어 Kafka 로 흘려보내지만, 여기서는
 * outbox + Kafka consumer 로 단순화한 시뮬레이션이다 (ADR-0004).</p>
 *
 * <p>{@code DELETE} 의 경우 {@code product} 가 null — id 만 있다 (삭제된 상태이므로 row 정보가 없음).
 * 대신 {@code productId} 는 항상 채움.</p>
 */
public record ProductChangeEvent(
        Op op,
        ProductId productId,
        Product product,
        long version,
        Instant occurredAt
) {

    public ProductChangeEvent {
        Objects.requireNonNull(op, "op");
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (op != Op.DELETE && product == null) {
            throw new IllegalArgumentException("INSERT/UPDATE 는 product 필수");
        }
        if (op == Op.DELETE && product != null) {
            throw new IllegalArgumentException("DELETE 는 product 가 null 이어야 함");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version 음수 불가: " + version);
        }
    }

    public enum Op {
        INSERT, UPDATE, DELETE
    }

    public static ProductChangeEvent insert(Product product, Instant now) {
        return new ProductChangeEvent(Op.INSERT, product.id(), product, product.version(), now);
    }

    public static ProductChangeEvent update(Product product, Instant now) {
        return new ProductChangeEvent(Op.UPDATE, product.id(), product, product.version(), now);
    }

    public static ProductChangeEvent delete(ProductId id, long version, Instant now) {
        return new ProductChangeEvent(Op.DELETE, id, null, version, now);
    }

    public boolean isDelete() {
        return op == Op.DELETE;
    }
}
