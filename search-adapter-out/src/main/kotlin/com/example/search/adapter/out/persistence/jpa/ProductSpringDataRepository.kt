package com.example.search.adapter.out.persistence.jpa

import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.JpaRepository

/**
 * Spring Data JPA 의 thin layer. 도메인은 [com.example.search.application.port.out.ProductSourceRepository]
 * port 만 보고, 이 인터페이스는 그 어댑터 안에서만 사용된다.
 */
interface ProductSpringDataRepository : JpaRepository<ProductJpaEntity, String> {

    /**
     * id 순 정렬로 안정적인 페이지 — reindex 시 같은 결과가 보장되어야 한다.
     */
    fun findAllByPage(offset: Int, limit: Int): List<ProductJpaEntity> {
        // PageRequest 는 page 단위지만 reindex 는 offset/limit 이 자연스러우므로 변환.
        val page = offset / maxOf(limit, 1)
        return findAll(PageRequest.of(page, limit, Sort.by("id"))).content
    }
}
