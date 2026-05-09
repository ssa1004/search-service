package com.example.search.adapter.out.persistence.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Outbox 패턴 엔티티 — source DB INSERT/UPDATE/DELETE 와 같은 트랜잭션 안에서 INSERT 된다. Relay 가
 * unpublished 행만 골라 Kafka 로 발행 — DB 커밋 + Kafka publish 의 atomicity 보장.
 *
 * <p>{@code published_at} = NULL → 미발행, NOT NULL → 발행 완료. 운영 retention 은 별도 정리 작업.</p>
 */
@Entity
@Table(name = "product_change_outbox", indexes = {
        @Index(name = "ix_outbox_published", columnList = "published_at, id")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class ProductChangeOutboxEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "op", nullable = false, length = 16)
    private Op op;

    @Column(name = "product_id", nullable = false, length = 64)
    private String productId;

    @Column(name = "version", nullable = false)
    private long version;

    /**
     * Product JSON payload — INSERT/UPDATE 만 채움 (DELETE 는 null).
     */
    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Setter
    @Column(name = "published_at")
    private Instant publishedAt;

    public enum Op {
        INSERT, UPDATE, DELETE
    }

    public static ProductChangeOutboxEntity insert(String productId, long version,
                                                   String payload, Instant now) {
        return create(Op.INSERT, productId, version, payload, now);
    }

    public static ProductChangeOutboxEntity update(String productId, long version,
                                                   String payload, Instant now) {
        return create(Op.UPDATE, productId, version, payload, now);
    }

    public static ProductChangeOutboxEntity delete(String productId, long version, Instant now) {
        return create(Op.DELETE, productId, version, null, now);
    }

    private static ProductChangeOutboxEntity create(Op op, String productId, long version,
                                                    String payload, Instant now) {
        ProductChangeOutboxEntity e = new ProductChangeOutboxEntity();
        e.op = op;
        e.productId = productId;
        e.version = version;
        e.payload = payload;
        e.occurredAt = now;
        return e;
    }

    public boolean isPublished() {
        return publishedAt != null;
    }

    public void markPublished(Instant at) {
        this.publishedAt = at;
    }
}
