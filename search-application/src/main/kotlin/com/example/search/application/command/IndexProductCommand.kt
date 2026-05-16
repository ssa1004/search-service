package com.example.search.application.command

import com.example.search.domain.product.Product

/**
 * 단건 상품 indexing 명령. [Product] 의 `version` 이 ES external version 으로 사용되어
 * 동시 갱신 시 lost update 를 거부한다.
 */
@JvmRecord
data class IndexProductCommand(val product: Product)
