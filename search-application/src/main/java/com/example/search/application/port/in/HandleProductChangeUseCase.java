package com.example.search.application.port.in;

import com.example.search.domain.event.ProductChangeEvent;

/**
 * CDC 컨슈머가 호출하는 유일한 진입점. INSERT/UPDATE 는 인덱싱, DELETE 는 ES 문서 삭제.
 *
 * <p>이 use case 는 idempotent — 같은 이벤트가 재처리되어도 ES external version 비교로 거부되거나
 * 같은 결과로 수렴한다. Kafka at-least-once 와 잘 맞는다 (ADR-0004).</p>
 */
public interface HandleProductChangeUseCase {
    void handle(ProductChangeEvent event);
}
