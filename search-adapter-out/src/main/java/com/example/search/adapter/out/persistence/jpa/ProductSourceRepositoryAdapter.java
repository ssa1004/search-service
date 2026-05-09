package com.example.search.adapter.out.persistence.jpa;

import com.example.search.application.port.out.ProductSourceRepository;
import com.example.search.domain.product.Product;
import com.example.search.domain.product.ProductId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * port → JPA adapter. 도메인 모델로 매핑해 외부에 노출.
 */
@Component
@RequiredArgsConstructor
public class ProductSourceRepositoryAdapter implements ProductSourceRepository {

    private final ProductSpringDataRepository delegate;

    @Override
    public Optional<Product> findById(ProductId id) {
        return delegate.findById(id.value()).map(ProductJpaEntity::toDomain);
    }

    @Override
    public List<Product> findAll(int offset, int limit) {
        return delegate.findAllByPage(offset, limit).stream()
                .map(ProductJpaEntity::toDomain)
                .toList();
    }

    @Override
    public long countAll() {
        return delegate.count();
    }
}
