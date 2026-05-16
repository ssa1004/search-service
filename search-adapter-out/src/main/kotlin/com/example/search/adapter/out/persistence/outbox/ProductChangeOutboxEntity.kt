package com.example.search.adapter.out.persistence.outbox

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant

/**
 * Outbox 패턴 엔티티 — source DB INSERT/UPDATE/DELETE 와 같은 트랜잭션 안에서 INSERT 된다. Relay 가
 * unpublished 행만 골라 Kafka 로 발행 — DB 커밋 + Kafka publish 의 atomicity 보장.
 *
 * `published_at` = NULL → 미발행, NOT NULL → 발행 완료. 운영 retention 은 별도 정리 작업.
 */
@Entity
@Table(
    name = "product_change_outbox",
    indexes = [Index(name = "ix_outbox_published", columnList = "published_at, id")]
)
class ProductChangeOutboxEntity protected constructor() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null
        private set

    @Enumerated(EnumType.STRING)
    @Column(name = "op", nullable = false, length = 16)
    var op: Op = Op.INSERT
        private set

    @Column(name = "product_id", nullable = false, length = 64)
    var productId: String = ""
        private set

    @Column(name = "version", nullable = false)
    var version: Long = 0
        private set

    /**
     * Product JSON payload — INSERT/UPDATE 만 채움 (DELETE 는 null).
     */
    @Column(name = "payload", columnDefinition = "TEXT")
    var payload: String? = null
        private set

    @Column(name = "occurred_at", nullable = false)
    var occurredAt: Instant = Instant.EPOCH
        private set

    @Column(name = "published_at")
    var publishedAt: Instant? = null

    enum class Op {
        INSERT, UPDATE, DELETE
    }

    fun isPublished(): Boolean = publishedAt != null

    fun markPublished(at: Instant) {
        this.publishedAt = at
    }

    companion object {

        @JvmStatic
        fun insert(productId: String, version: Long, payload: String, now: Instant): ProductChangeOutboxEntity =
            create(Op.INSERT, productId, version, payload, now)

        @JvmStatic
        fun update(productId: String, version: Long, payload: String, now: Instant): ProductChangeOutboxEntity =
            create(Op.UPDATE, productId, version, payload, now)

        @JvmStatic
        fun delete(productId: String, version: Long, now: Instant): ProductChangeOutboxEntity =
            create(Op.DELETE, productId, version, null, now)

        private fun create(
            op: Op,
            productId: String,
            version: Long,
            payload: String?,
            now: Instant
        ): ProductChangeOutboxEntity {
            val e = ProductChangeOutboxEntity()
            e.op = op
            e.productId = productId
            e.version = version
            e.payload = payload
            e.occurredAt = now
            return e
        }
    }
}
