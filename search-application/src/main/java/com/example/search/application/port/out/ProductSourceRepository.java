package com.example.search.application.port.out;

import com.example.search.domain.product.Product;
import com.example.search.domain.product.ProductId;

import java.util.List;
import java.util.Optional;

/**
 * source DB (Postgres `products`) port — ES 의 권위 (source of truth) 측 접근. CDC 가 깨졌을 때
 * 전체 reindex 를 위해 application 모듈에서도 직접 읽기를 허용한다 (read-only).
 *
 * <p>쓰기 (INSERT/UPDATE/DELETE) 는 outbox 와 함께 트랜잭션으로 묶여야 하므로 별도 service 의 책임
 * (PlaceProductService 등) — 현재 도메인 범위에서는 reindex 와 read 용도로만 사용.</p>
 */
public interface ProductSourceRepository {

    Optional<Product> findById(ProductId id);

    /**
     * 페이지 단위 전체 조회 — reindex 시 메모리 폭발 방지를 위해 cursor / page 단위로 끊어 읽는다.
     * 구현체는 {@code id} 정렬 + offset 또는 keyset pagination.
     */
    List<Product> findAll(int offset, int limit);

    long countAll();
}
