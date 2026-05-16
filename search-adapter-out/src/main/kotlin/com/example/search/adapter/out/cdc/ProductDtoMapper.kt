package com.example.search.adapter.out.cdc

import com.example.search.domain.product.Category
import com.example.search.domain.product.Product
import com.example.search.domain.product.ProductId
import com.example.search.domain.product.ProductStatus
import com.example.search.domain.shared.Money
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import java.io.IOException
import java.time.Instant

/**
 * outbox payload (JSON) ↔ [Product] 매핑. 도메인이 Jackson 어노테이션을 가지지 않도록 외부에서
 * 직렬화 책임을 진다.
 */
object ProductDtoMapper {

    @JvmStatic
    fun toJson(p: Product, mapper: ObjectMapper): String {
        val node = mapper.createObjectNode()
        node.put("id", p.id.value)
        node.put("name", p.name)
        node.put("brand", p.brand)
        node.put("category", p.category.name)
        val arr = node.putArray("sizes")
        for (s in p.sizes) arr.add(s)
        node.put("priceWon", p.price.won())
        node.put("stockQuantity", p.stockQuantity)
        node.put("status", p.status.name)
        node.put("version", p.version)
        node.put("releasedAt", p.releasedAt.toString())
        node.put("updatedAt", p.updatedAt.toString())
        try {
            return mapper.writeValueAsString(node)
        } catch (e: IOException) {
            throw IllegalStateException("product 직렬화 실패", e)
        }
    }

    @JvmStatic
    fun fromJson(json: String, mapper: ObjectMapper): Product {
        try {
            val n = mapper.readTree(json) as ObjectNode
            val sizes = ArrayList<String>()
            n.withArray("sizes").forEach { sizes.add(it.asText()) }
            return Product(
                ProductId.of(n.get("id").asText()),
                n.get("name").asText(),
                n.get("brand").asText(),
                Category.valueOf(n.get("category").asText()),
                sizes,
                Money.won(n.get("priceWon").asLong()),
                n.get("stockQuantity").asInt(),
                ProductStatus.valueOf(n.get("status").asText()),
                n.get("version").asLong(),
                Instant.parse(n.get("releasedAt").asText()),
                Instant.parse(n.get("updatedAt").asText())
            )
        } catch (e: IOException) {
            throw IllegalStateException("product JSON 파싱 실패: $json", e)
        } catch (e: ClassCastException) {
            throw IllegalStateException("product JSON 파싱 실패: $json", e)
        }
    }
}
