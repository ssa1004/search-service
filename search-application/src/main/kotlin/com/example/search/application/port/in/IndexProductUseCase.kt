package com.example.search.application.port.`in`

import com.example.search.application.command.IndexProductCommand

/**
 * 상품 단건 indexing — INSERT / UPDATE 통합 (upsert). ES external version 으로 동시 갱신 충돌 방지.
 *
 * 호출 경로:
 *
 * - REST POST /products → source DB 저장 → 같은 트랜잭션의 outbox 에 이벤트 → CDC 컨슈머 →
 *   이 use case.
 * - REST PUT /products/{id} → 같은 흐름.
 */
interface IndexProductUseCase {
    fun index(command: IndexProductCommand)
}
