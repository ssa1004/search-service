package com.example.search.adapter.out.cdc;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Kafka topic {@code product.changes} 의 메시지 payload. Debezium-style envelope 의 단순화 버전.
 *
 * <ul>
 *   <li>{@code op} — INSERT / UPDATE / DELETE</li>
 *   <li>{@code productId} — 항상 채움</li>
 *   <li>{@code version} — source DB 의 version 컬럼</li>
 *   <li>{@code payload} — INSERT/UPDATE 의 product JSON. DELETE 는 null.</li>
 *   <li>{@code occurredAt} — source 변경 시각 (ISO-8601)</li>
 * </ul>
 */
public record CdcEventPayload(
        String op,
        String productId,
        long version,
        String payload,
        String occurredAt
) {

    @JsonCreator
    public CdcEventPayload(
            @JsonProperty("op") String op,
            @JsonProperty("productId") String productId,
            @JsonProperty("version") long version,
            @JsonProperty("payload") String payload,
            @JsonProperty("occurredAt") String occurredAt
    ) {
        this.op = op;
        this.productId = productId;
        this.version = version;
        this.payload = payload;
        this.occurredAt = occurredAt;
    }
}
