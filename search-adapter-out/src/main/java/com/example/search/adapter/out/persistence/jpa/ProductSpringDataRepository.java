package com.example.search.adapter.out.persistence.jpa;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA 의 thin layer. 도메인은 {@link com.example.search.application.port.out.ProductSourceRepository}
 * port 만 보고, 이 인터페이스는 그 어댑터 안에서만 사용된다.
 */
public interface ProductSpringDataRepository extends JpaRepository<ProductJpaEntity, String> {

    /**
     * id 순 정렬로 안정적인 페이지 — reindex 시 같은 결과가 보장되어야 한다.
     */
    default List<ProductJpaEntity> findAllByPage(int offset, int limit) {
        // PageRequest 는 page 단위지만 reindex 는 offset/limit 이 자연스러우므로 변환.
        int page = offset / Math.max(limit, 1);
        return findAll(PageRequest.of(page, limit, Sort.by("id"))).getContent();
    }
}
