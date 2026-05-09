package com.example.search.adapter.out.persistence.jpa;

import com.example.search.application.port.out.SearchClickRepository;
import com.example.search.domain.event.SearchClick;
import com.example.search.domain.product.ProductId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class SearchClickRepositoryAdapter implements SearchClickRepository {

    private final SearchClickSpringDataRepository delegate;

    @Override
    public void save(SearchClick click) {
        delegate.save(SearchClickJpaEntity.from(click));
    }

    @Override
    public long sumClicksFor(ProductId productId, Instant since) {
        return delegate.countByProductSince(productId.value(), since);
    }
}
