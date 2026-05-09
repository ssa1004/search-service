package com.example.search.application.command;

import com.example.search.domain.product.Product;

import java.util.Objects;

/**
 * 단건 상품 indexing 명령. {@link Product} 의 {@code version} 이 ES external version 으로 사용되어
 * 동시 갱신 시 lost update 를 거부한다.
 */
public record IndexProductCommand(Product product) {

    public IndexProductCommand {
        Objects.requireNonNull(product, "product");
    }
}
