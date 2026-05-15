package com.example.search.domain.event

import com.example.search.domain.product.Product
import com.example.search.domain.product.ProductId
import java.time.Instant

/**
 * source DB (Postgres `products`) 의 INSERT/UPDATE/DELETE 변경을 표현하는 CDC 이벤트.
 *
 * 실제 운영에서는 Debezium 이 이 형태의 이벤트를 만들어 Kafka 로 흘려보내지만, 여기서는
 * outbox + Kafka consumer 로 단순화한 시뮬레이션이다 (ADR-0004).
 *
 * `DELETE` 의 경우 [product] 가 null — id 만 있다 (삭제된 상태이므로 row 정보가 없음).
 * 대신 [productId] 는 항상 채움.
 */
@JvmRecord
data class ProductChangeEvent(
    val op: Op,
    val productId: ProductId,
    // DELETE 는 product 가 null — Java record 와 동일하게 nullable.
    val product: Product?,
    val version: Long,
    val occurredAt: Instant
) {
    init {
        require(!(op != Op.DELETE && product == null)) { "INSERT/UPDATE 는 product 필수" }
        require(!(op == Op.DELETE && product != null)) { "DELETE 는 product 가 null 이어야 함" }
        require(version >= 0) { "version 음수 불가: $version" }
    }

    fun isDelete(): Boolean = op == Op.DELETE

    enum class Op {
        INSERT, UPDATE, DELETE
    }

    companion object {
        @JvmStatic
        fun insert(product: Product, now: Instant): ProductChangeEvent =
            ProductChangeEvent(Op.INSERT, product.id, product, product.version, now)

        @JvmStatic
        fun update(product: Product, now: Instant): ProductChangeEvent =
            ProductChangeEvent(Op.UPDATE, product.id, product, product.version, now)

        @JvmStatic
        fun delete(id: ProductId, version: Long, now: Instant): ProductChangeEvent =
            ProductChangeEvent(Op.DELETE, id, null, version, now)
    }
}
