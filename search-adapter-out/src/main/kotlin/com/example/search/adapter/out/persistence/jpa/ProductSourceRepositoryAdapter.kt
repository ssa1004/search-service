package com.example.search.adapter.out.persistence.jpa

import com.example.search.application.port.out.ProductSourceRepository
import com.example.search.domain.product.Product
import com.example.search.domain.product.ProductId
import org.springframework.stereotype.Component
import java.util.Optional

/**
 * port → JPA adapter. 도메인 모델로 매핑해 외부에 노출.
 */
@Component
class ProductSourceRepositoryAdapter(
    private val delegate: ProductSpringDataRepository
) : ProductSourceRepository {

    override fun findById(id: ProductId): Optional<Product> =
        delegate.findById(id.value).map { it.toDomain() }

    override fun findAll(offset: Int, limit: Int): List<Product> =
        delegate.findAllByPage(offset, limit).map { it.toDomain() }

    override fun countAll(): Long = delegate.count()
}
