package com.example.search.adapter.out.cdc

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Kafka topic `product.changes` 의 메시지 payload. Debezium-style envelope 의 단순화 버전.
 *
 * - `op` — INSERT / UPDATE / DELETE
 * - `productId` — 항상 채움
 * - `version` — source DB 의 version 컬럼
 * - `payload` — INSERT/UPDATE 의 product JSON. DELETE 는 null.
 * - `occurredAt` — source 변경 시각 (ISO-8601)
 */
@JvmRecord
data class CdcEventPayload @JsonCreator constructor(
    @JsonProperty("op") val op: String,
    @JsonProperty("productId") val productId: String,
    @JsonProperty("version") val version: Long,
    @JsonProperty("payload") val payload: String?,
    @JsonProperty("occurredAt") val occurredAt: String
)
