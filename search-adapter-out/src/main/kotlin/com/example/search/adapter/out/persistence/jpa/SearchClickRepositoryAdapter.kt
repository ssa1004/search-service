package com.example.search.adapter.out.persistence.jpa

import com.example.search.application.port.out.SearchClickRepository
import com.example.search.domain.event.SearchClick
import com.example.search.domain.product.ProductId
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class SearchClickRepositoryAdapter(
    private val delegate: SearchClickSpringDataRepository
) : SearchClickRepository {

    override fun save(click: SearchClick) {
        delegate.save(SearchClickJpaEntity.from(click))
    }

    override fun sumClicksFor(productId: ProductId, since: Instant): Long =
        delegate.countByProductSince(productId.value, since)
}
