package com.example.search.adapter.out.cdc

import com.example.search.adapter.out.persistence.outbox.ProductChangeOutboxEntity
import com.example.search.adapter.out.persistence.outbox.ProductChangeOutboxRepository
import com.example.search.domain.product.Product
import com.example.search.domain.product.ProductId
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import java.time.Clock

/**
 * source 모듈이 product 를 INSERT/UPDATE/DELETE 할 때 같은 트랜잭션에서 outbox 행을 쓰는 helper.
 *
 * 현재는 demo seed 와 e2e-test 가 직접 호출. 실제 운영 시나리오에서는 PlaceProductService /
 * UpdateProductService 등의 도메인 service 가 같은 트랜잭션 안에서 호출한다.
 */
@Component
class ProductOutboxWriter(
    private val outbox: ProductChangeOutboxRepository,
    private val objectMapper: ObjectMapper,
    private val clock: Clock
) {

    fun recordInsert(product: Product) {
        outbox.save(
            ProductChangeOutboxEntity.insert(
                product.id.value, product.version,
                ProductDtoMapper.toJson(product, objectMapper),
                clock.instant()
            )
        )
    }

    fun recordUpdate(product: Product) {
        outbox.save(
            ProductChangeOutboxEntity.update(
                product.id.value, product.version,
                ProductDtoMapper.toJson(product, objectMapper),
                clock.instant()
            )
        )
    }

    fun recordDelete(id: ProductId, version: Long) {
        outbox.save(ProductChangeOutboxEntity.delete(id.value, version, clock.instant()))
    }
}
